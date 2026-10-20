package com.smarthire.application.service;

import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import com.smarthire.application.domain.IllegalApplicationStateException;
import com.smarthire.application.infra.ApplicationRepository;
import com.smarthire.application.infra.JobClient;
import com.smarthire.application.infra.NotificationClient;
import com.smarthire.common.audit.AuditService;
import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Application aggregate.
 *
 * <p>This is the object-level authorization layer. The filter chain has already established
 * <em>that</em> the caller is authenticated and method security has established <em>which role</em>
 * they hold; here we decide whether this particular caller may see or change this particular
 * application. {@code callerCandidateId} always originates from the verified JWT {@code sub} claim
 * for candidates; {@code callerOrgId} from the JWT {@code orgId} claim for recruiters — never from
 * a body, query parameter or header.
 *
 * <p>Visibility rules:
 *
 * <ul>
 *   <li>Candidate: can see only their own applications (candidate_id match).
 *   <li>Recruiter: can see applications for jobs owned by their organization (validated via
 *       JobClient).
 *   <li>ADMIN: platform operations — access to any resource.
 * </ul>
 */
@Service
public class ApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

  private final ApplicationRepository applicationRepository;
  private final JobClient jobClient;
  private final NotificationClient notificationClient;

  public ApplicationService(
      ApplicationRepository applicationRepository,
      JobClient jobClient,
      NotificationClient notificationClient) {
    this.applicationRepository = applicationRepository;
    this.jobClient = jobClient;
    this.notificationClient = notificationClient;
  }

  /**
   * Creates an application in APPLIED status, owned by the candidate from the verified JWT.
   *
   * @param jobId the job to apply to
   * @param resumeLink the candidate's resume link
   * @param candidateId the candidate UUID from the verified JWT (never from request body)
   * @return the created application
   * @throws ApplicationNotFoundException if the job does not exist
   * @throws IllegalArgumentException if the job is not OPEN
   */
  @Transactional
  public Application create(UUID jobId, String resumeLink, UUID candidateId) {
    // Validate job exists and is OPEN via Job Service
    JobClient.JobResponse job;
    try {
      job = jobClient.getJob(jobId);
    } catch (feign.FeignException.NotFound e) {
      throw new ApplicationNotFoundException("Job not found: " + jobId);
    }

    if (!job.isOpen()) {
      throw new IllegalArgumentException(
          "Cannot apply to a job that is not OPEN; current status: " + job.status());
    }

    Application application =
        Application.createApplied(UUID.randomUUID(), jobId, candidateId, resumeLink);
    Application saved = applicationRepository.save(application);
    AuditService.application(
        AuditService.ApplicationEvent.CREATE,
        candidateId,
        saved.getApplicationId(),
        AuditService.Outcome.SUCCESS,
        "jobId=" + jobId);
    return saved;
  }

  /**
   * Reads one application, subject to visibility rules.
   *
   * @param applicationId the application to read
   * @param callerCandidateId the candidate UUID from JWT (null if not a candidate)
   * @param callerOrgId the org UUID from JWT (null if not a recruiter)
   * @param isAdmin whether the caller has ADMIN role
   * @return the application
   * @throws ApplicationNotFoundException if not found or not visible
   * @throws ApplicationForbiddenException if caller lacks access
   */
  @Transactional(readOnly = true)
  public Application get(
      UUID applicationId, UUID callerCandidateId, UUID callerOrgId, boolean isAdmin) {
    Application application = load(applicationId);

    if (isAdmin) {
      return application;
    }

    // Candidate can only see their own
    if (callerCandidateId != null && application.isOwnedBy(callerCandidateId)) {
      return application;
    }

    // Recruiter can see applications for their organization's jobs
    if (callerOrgId != null) {
      try {
        JobClient.JobResponse job = jobClient.getJob(application.getJobId());
        if (callerOrgId.equals(job.ownerOrgId())) {
          return application;
        }
      } catch (feign.FeignException.NotFound ignored) {
        // Job not found - fall through to 404
      }
    }

    throw new ApplicationNotFoundException("Application not found: " + applicationId);
  }

  /**
   * Candidate withdraws their application.
   *
   * @param applicationId the application to withdraw
   * @param callerCandidateId the candidate UUID from JWT
   * @return the withdrawn application
   * @throws ApplicationNotFoundException if not found
   * @throws ApplicationForbiddenException if not the owner
   * @throws IllegalApplicationStateException if illegal transition
   */
  @Transactional
  public Application withdraw(UUID applicationId, UUID callerCandidateId) {
    Application application = loadOwned(applicationId, callerCandidateId);
    application.withdraw();
    Application saved = applicationRepository.save(application);
    AuditService.application(
        AuditService.ApplicationEvent.WITHDRAW,
        callerCandidateId,
        applicationId,
        AuditService.Outcome.SUCCESS,
        null);
    return saved;
  }

  /**
   * Recruiter or ADMIN updates an application's status (recruiter-driven transitions).
   *
   * <p>Allowed transitions:
   *
   * <ul>
   *   <li>APPLIED → UNDER_REVIEW
   *   <li>UNDER_REVIEW → SHORTLISTED
   *   <li>SHORTLISTED → HIRED
   *   <li>SHORTLISTED → REJECTED
   * </ul>
   *
   * <p>Authorization:
   *
   * <ul>
   *   <li>RECRUITER must belong to the organization that owns the job associated with the
   *       application
   *   <li>ADMIN can update any application
   *   <li>CANDIDATE cannot update status (use withdraw instead)
   * </ul>
   *
   * <p>After persisting the status change, triggers a notification via Notification Service for
   * statuses UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED. Notification delivery is best-effort and
   * does not roll back the application status transition on failure.
   *
   * @param applicationId the application to update
   * @param newStatus the target status
   * @param callerOrgId the org UUID from JWT (null if not a recruiter)
   * @param isAdmin whether the caller has ADMIN role
   * @return the updated application
   * @throws ApplicationNotFoundException if not found
   * @throws ApplicationForbiddenException if caller lacks access
   * @throws IllegalApplicationStateException if illegal transition
   */
  @Transactional
  public Application updateStatus(
      UUID applicationId, ApplicationStatus newStatus, UUID callerOrgId, boolean isAdmin) {
    Application application = load(applicationId);

    if (!isAdmin) {
      // Recruiter must own the job
      if (callerOrgId == null) {
        throw new ApplicationForbiddenException(
            "Recruiter must have an organization to update application status.");
      }
      JobClient.JobResponse job;
      try {
        job = jobClient.getJob(application.getJobId());
      } catch (feign.FeignException.NotFound e) {
        throw new ApplicationNotFoundException("Job not found for application: " + applicationId);
      }
      if (!callerOrgId.equals(job.ownerOrgId())) {
        AuditService.authorizationFailure(
            "application",
            applicationId,
            callerOrgId,
            "Recruiter does not own the job for this application");
        throw new ApplicationForbiddenException(
            "Recruiter does not own the job for this application.");
      }
    }

    // Apply the transition
    String oldStatus = application.getStatus().name();
    switch (newStatus) {
      case UNDER_REVIEW -> application.moveToUnderReview();
      case SHORTLISTED -> application.shortlist();
      case HIRED -> application.hire();
      case REJECTED -> application.reject();
      case APPLIED, WITHDRAWN ->
          throw new IllegalApplicationStateException(
              "Cannot transition to "
                  + newStatus
                  + " via recruiter-driven update; this application is "
                  + application.getStatus()
                  + ".",
              application.getStatus(),
              "updateStatus");
      default -> throw new IllegalArgumentException("Unknown status: " + newStatus);
    }

    // Persist the status change
    Application savedApplication = applicationRepository.save(application);

    // Audit the status change
    AuditService.application(
        AuditService.ApplicationEvent.STATUS_CHANGE,
        isAdmin ? null : callerOrgId,
        applicationId,
        AuditService.Outcome.SUCCESS,
        "oldStatus=" + oldStatus + ",newStatus=" + newStatus.name());

    // Trigger notification for status changes that require candidate notification
    // Notification delivery is best-effort - failures do not roll back the application transaction
    if (isNotificationStatus(newStatus)) {
      try {
        notificationClient.sendNotification(
            new ApplicationStatusChangeRequest(
                application.getCandidateId(),
                application.getApplicationId(),
                newStatus.toString()));
        log.info(
            "Triggered notification for application {} status change to {}",
            application.getApplicationId(),
            newStatus);
      } catch (Exception e) {
        // Log but don't fail the application status update
        log.warn(
            "Failed to send notification for application {} status change to {}: {}",
            application.getApplicationId(),
            newStatus,
            e.getMessage(),
            e);
      }
    }

    return savedApplication;
  }

  /** Lists applications for a candidate. */
  @Transactional(readOnly = true)
  public Page<Application> listForCandidate(UUID candidateId, Pageable pageable) {
    return applicationRepository.findByCandidateId(candidateId, pageable);
  }

  // --- helpers -------------------------------------------------------------------

  private Application load(UUID applicationId) {
    return applicationRepository
        .findByApplicationId(applicationId)
        .orElseThrow(
            () -> new ApplicationNotFoundException("Application not found: " + applicationId));
  }

  private Application loadOwned(UUID applicationId, UUID callerCandidateId) {
    Application application = load(applicationId);
    if (!application.isOwnedBy(callerCandidateId)) {
      throw new ApplicationForbiddenException("This application belongs to another candidate.");
    }
    return application;
  }

  /**
   * Determines if an application status should trigger a notification.
   *
   * <p>Notifications are sent for: UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED.
   *
   * @param status the application status to check
   * @return true if a notification should be sent
   */
  private boolean isNotificationStatus(ApplicationStatus status) {
    return status == ApplicationStatus.UNDER_REVIEW
        || status == ApplicationStatus.SHORTLISTED
        || status == ApplicationStatus.HIRED
        || status == ApplicationStatus.REJECTED;
  }
}
