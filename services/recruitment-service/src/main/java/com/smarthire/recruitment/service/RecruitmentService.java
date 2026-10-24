package com.smarthire.recruitment.service;

import com.smarthire.common.audit.AuditService;
import com.smarthire.recruitment.domain.FinalStatus;
import com.smarthire.recruitment.domain.IllegalRecruitmentStateException;
import com.smarthire.recruitment.domain.InterviewStatus;
import com.smarthire.recruitment.domain.Recruitment;
import com.smarthire.recruitment.infra.ApplicationClient;
import com.smarthire.recruitment.infra.JobClient;
import com.smarthire.recruitment.infra.RecruitmentRepository;
import feign.FeignException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Recruitment aggregate.
 *
 * <p>This is the object-level authorization layer. The filter chain has already established
 * <em>that</em> the caller is authenticated and method security has established <em>which role</em>
 * they hold; here we decide whether this particular caller may see or change this particular
 * recruitment. {@code callerCandidateId} always originates from the verified JWT {@code sub} claim,
 * {@code callerOrgId} from the verified JWT {@code orgId} claim — never from a body, query
 * parameter or header, and never from a gateway-supplied header.
 *
 * <p>Every decision compares those claims against columns on the recruitment row itself:
 *
 * <ul>
 *   <li><b>Recruiter</b> — JWT {@code orgId} against the stored {@code owner_org_id}; may read and
 *       may drive both status lifecycles.
 *   <li><b>Candidate</b> — JWT {@code sub} against the stored {@code candidate_id}; may read only,
 *       which is what lets a candidate track their own outcome (AC-5).
 *   <li><b>ADMIN</b> — platform operations, so it may read for support and audit, and deliberately
 *       has no write path: ADMIN is not a recruitment actor (ADR-0010) and must not decide a hire.
 * </ul>
 *
 * <p>Those two ownership columns are resolved exactly once, when the application is shortlisted, by
 * reading the application (for {@code candidate_id} and {@code job_id}) and then the job (for
 * {@code owner_org_id}). Afterwards no request touches another service to authorize, and because
 * the values are write-once on the aggregate, a later request cannot re-point a recruitment at a
 * different organization or candidate.
 *
 * <p>Object-level denials report "not found", not "forbidden", so a caller cannot use the
 * difference to confirm that an identifier exists — the same rule job-service applies to non-public
 * postings.
 */
@Service
public class RecruitmentService {

  private static final Logger log = LoggerFactory.getLogger(RecruitmentService.class);

  private final RecruitmentRepository recruitmentRepository;
  private final ApplicationClient applicationClient;
  private final JobClient jobClient;

  public RecruitmentService(
      RecruitmentRepository recruitmentRepository,
      ApplicationClient applicationClient,
      JobClient jobClient) {
    this.recruitmentRepository = recruitmentRepository;
    this.applicationClient = applicationClient;
    this.jobClient = jobClient;
  }

  /**
   * Shortlists an application — creates the recruitment record at the start of both lifecycles.
   *
   * <p>Resolves ownership before writing anything: the application supplies {@code jobId} and
   * {@code candidateId}, the job supplies {@code ownerOrgId}, and the recruiter's verified {@code
   * orgId} must equal that {@code ownerOrgId}. A recruiter of another organization gets 404,
   * identical to the response for an application that does not exist, so the endpoint cannot be
   * used to discover another organization's application identifiers.
   *
   * @param applicationId the application to shortlist
   * @param callerOrgId the organization UUID from the verified JWT (never from the request body)
   * @return the created recruitment
   * @throws RecruitmentForbiddenException if the recruiter's token carries no organization
   * @throws RecruitmentNotFoundException if the application or its job does not exist, or belongs
   *     to another organization
   * @throws RecruitmentConflictException if the application has already been shortlisted
   */
  @Transactional
  public Recruitment shortlist(UUID applicationId, UUID callerOrgId) {
    if (callerOrgId == null) {
      throw new RecruitmentForbiddenException(
          "Your access token carries no organization, so it cannot shortlist an application.");
    }

    ApplicationClient.ApplicationResponse application;
    try {
      application = applicationClient.getApplication(applicationId);
    } catch (FeignException.NotFound notFound) {
      throw new RecruitmentNotFoundException("Application not found: " + applicationId);
    }

    // An application names a job but not its owning organization, so follow the chain one more hop.
    JobClient.JobResponse job;
    try {
      job = jobClient.getJob(application.jobId());
    } catch (FeignException.NotFound notFound) {
      throw new RecruitmentNotFoundException("Application not found: " + applicationId);
    }

    if (!callerOrgId.equals(job.ownerOrgId())) {
      // Same response as a missing application: a recruiter must not learn that this identifier is
      // real but belongs to someone else.
      throw new RecruitmentNotFoundException("Application not found: " + applicationId);
    }

    if (recruitmentRepository.findByApplicationId(applicationId).isPresent()) {
      throw new RecruitmentConflictException(
          "This application has already been shortlisted: " + applicationId);
    }

    Recruitment recruitment =
        Recruitment.shortlist(
            UUID.randomUUID(),
            applicationId,
            application.jobId(),
            job.ownerOrgId(),
            application.candidateId());
    try {
      // Flush inside the call so the UNIQUE(application_id) violation surfaces here, as a 409,
      // rather than at commit time where it would escape this method as a 500. Two concurrent
      // shortlists of one application therefore produce one recruitment and one conflict.
      Recruitment saved = recruitmentRepository.saveAndFlush(recruitment);
      AuditService.recruitment(
          AuditService.RecruitmentEvent.SHORTLIST,
          callerOrgId,
          saved.getRecruitmentId(),
          AuditService.Outcome.SUCCESS,
          "applicationId=" + applicationId);

      // Start the application lifecycle on the path to SHORTLISTED. The aggregate only allows
      // APPLIED → UNDER_REVIEW → SHORTLISTED, and a recruiter shortlists whatever state the
      // application is in, so the intermediate step is performed here when it is still APPLIED.
      // Without it a fresh application is refused by the application state machine (409) and the
      // recruitment could never be created.
      if ("APPLIED".equals(application.status())) {
        updateApplicationStatus(applicationId, "UNDER_REVIEW");
      }
      if (!"SHORTLISTED".equals(application.status())) {
        updateApplicationStatus(applicationId, "SHORTLISTED");
      }

      return saved;
    } catch (DataIntegrityViolationException race) {
      throw new RecruitmentConflictException(
          "This application has already been shortlisted: " + applicationId);
    }
  }

  /**
   * Moves an application to the next status, translating the downstream answer.
   *
   * <p>Application Service remains the authority on whether the transition is legal; this method
   * only decides which HTTP status the caller sees. A refusal by its state machine is a 409 (the
   * request was understood and declined), a missing application is a 404, and everything else is a
   * 503 that leaves the local transaction to roll back.
   */
  private void updateApplicationStatus(UUID applicationId, String status) {
    try {
      applicationClient.updateStatus(
          applicationId, new ApplicationClient.StatusUpdateRequest(status));
    } catch (FeignException.NotFound notFound) {
      throw new RecruitmentNotFoundException("Application not found: " + applicationId);
    } catch (FeignException.Conflict | FeignException.BadRequest refused) {
      throw new ApplicationSyncConflictException(
          "Application "
              + applicationId
              + " cannot be moved to "
              + status
              + "; the recruitment was not created.");
    } catch (FeignException other) {
      throw new ApplicationClient.ApplicationServiceUnavailableException(
          "Application service rejected the status update for application "
              + applicationId
              + ": "
              + other.status());
    }
  }

  /**
   * Reads one recruitment, subject to the visibility rules above. Needs no cross-service call: both
   * ownership facts are columns on the row.
   *
   * @param recruitmentId the recruitment to read
   * @param callerCandidateId the {@code sub} UUID from the verified JWT ({@code null} if absent)
   * @param callerOrgId the {@code orgId} UUID from the verified JWT ({@code null} if absent)
   * @param isAdmin whether the verified token carries the ADMIN role
   * @return the recruitment
   * @throws RecruitmentNotFoundException if it does not exist, or is not visible to this caller
   */
  @Transactional(readOnly = true)
  public Recruitment get(
      UUID recruitmentId, UUID callerCandidateId, UUID callerOrgId, boolean isAdmin) {
    Recruitment recruitment = load(recruitmentId);

    if (isAdmin) {
      return recruitment;
    }
    if (recruitment.isOwnedBy(callerOrgId)) {
      return recruitment;
    }
    if (recruitment.belongsToCandidate(callerCandidateId)) {
      return recruitment;
    }
    throw new RecruitmentNotFoundException("Recruitment not found: " + recruitmentId);
  }

  /**
   * Lists the recruitments the caller may see — their organization's for a recruiter, their own for
   * a candidate (AC-5 status tracking), everything for ADMIN's operational read. A caller who is
   * none of those gets an empty page rather than an error, because there is nothing to hide.
   *
   * <p>Filtering happens in the query, not after it: a caller never receives another organization's
   * or another candidate's row to be filtered out in memory.
   */
  @Transactional(readOnly = true)
  public Page<Recruitment> list(
      UUID callerCandidateId, UUID callerOrgId, boolean isAdmin, Pageable pageable) {
    if (isAdmin) {
      return recruitmentRepository.findAll(pageable);
    }
    if (callerOrgId != null) {
      return recruitmentRepository.findByOwnerOrgId(callerOrgId, pageable);
    }
    if (callerCandidateId != null) {
      return recruitmentRepository.findByCandidateId(callerCandidateId, pageable);
    }
    return Page.empty(pageable);
  }

  /**
   * Advances the interview lifecycle: {@code NOT_SCHEDULED → SCHEDULED → COMPLETED | NO_SHOW}. Only
   * a recruiter of the owning organization may call it; the aggregate rejects every other
   * transition.
   *
   * @throws RecruitmentNotFoundException if it does not exist or belongs to another organization
   * @throws IllegalRecruitmentStateException if the transition is not legal from the current state
   */
  @Transactional
  public Recruitment updateInterviewStatus(
      UUID recruitmentId, InterviewStatus status, UUID callerOrgId) {
    Recruitment recruitment = loadOwned(recruitmentId, callerOrgId);
    String oldStatus = recruitment.getInterviewStatus().name();
    switch (status) {
      case SCHEDULED -> recruitment.scheduleInterview();
      case COMPLETED -> recruitment.completeInterview();
      case NO_SHOW -> recruitment.markNoShow();
        // NOT_SCHEDULED is the initial state only; nothing may move back to it.
      case NOT_SCHEDULED ->
          throw new IllegalRecruitmentStateException(
              "An interview cannot be returned to NOT_SCHEDULED.",
              recruitment.getInterviewStatus().name(),
              recruitment.getFinalStatus().name(),
              "updateInterviewStatus:NOT_SCHEDULED");
    }
    Recruitment saved = recruitmentRepository.save(recruitment);
    AuditService.recruitment(
        AuditService.RecruitmentEvent.INTERVIEW_STATUS_CHANGE,
        callerOrgId,
        recruitmentId,
        AuditService.Outcome.SUCCESS,
        "oldStatus=" + oldStatus + ",newStatus=" + status.name());
    return saved;
  }

  /** Schedules an owned recruitment with complete interview details. */
  @Transactional
  public Recruitment scheduleInterview(
      UUID recruitmentId,
      LocalDate interviewDate,
      LocalTime interviewStartTime,
      String interviewer,
      String interviewLocation,
      UUID callerOrgId) {
    Recruitment recruitment = loadOwned(recruitmentId, callerOrgId);
    if (LocalDateTime.of(interviewDate, interviewStartTime).isBefore(LocalDateTime.now())) {
      throw new RecruitmentScheduleException("Interview date and time must be in the future.");
    }
    recruitment.scheduleInterview(
        interviewDate, interviewStartTime, interviewer.trim(), interviewLocation.trim());
    Recruitment saved = recruitmentRepository.save(recruitment);
    AuditService.recruitment(
        AuditService.RecruitmentEvent.INTERVIEW_STATUS_CHANGE,
        callerOrgId,
        recruitmentId,
        AuditService.Outcome.SUCCESS,
        "newStatus=SCHEDULED,interviewDate=" + interviewDate);
    return saved;
  }

  /**
   * Decides the outcome: {@code PENDING → HIRED | REJECTED}, both terminal. Only a recruiter of the
   * owning organization may call it.
   *
   * <p>Deciding a recruitment and settling its application are one operation from the caller's
   * point of view, so they are performed together. Once the aggregate accepts the transition, the
   * application is moved through its own API via {@link ApplicationClient} — never by writing to
   * the application service's database — and Application Service stays the sole authority on
   * whether the application may take that state.
   *
   * <ul>
   *   <li>HIRED → Application status HIRED
   *   <li>REJECTED → Application status REJECTED
   * </ul>
   *
   * <p>A failed synchronization aborts the whole call: the exception propagates out of this
   * {@code @Transactional} method, the recruitment change is rolled back, and the caller receives
   * an error. Neither aggregate is left half-settled, and the caller is never told the hire
   * succeeded while the application still reads APPLIED. {@code
   * ApplicationServiceUnavailableException} maps to an RFC 9457 503; a refusal by the application
   * state machine maps to 409.
   *
   * <p>What this does not cover is the opposite order — the application commits and this
   * transaction then fails to commit. Closing that window needs a transactional outbox and a
   * reconciler, which are out of scope for R1.
   *
   * @throws RecruitmentNotFoundException if it does not exist or belongs to another organization
   * @throws IllegalRecruitmentStateException if the recruitment has already been decided
   * @throws ApplicationSyncConflictException if Application Service refused the transition
   * @throws ApplicationClient.ApplicationServiceUnavailableException if Application Service could
   *     not be reached
   */
  @Transactional
  public Recruitment updateFinalStatus(UUID recruitmentId, FinalStatus status, UUID callerOrgId) {
    Recruitment recruitment = loadOwned(recruitmentId, callerOrgId);

    // Apply the recruitment final status transition
    String oldStatus = recruitment.getFinalStatus().name();
    switch (status) {
      case HIRED -> recruitment.hire();
      case REJECTED -> recruitment.reject();
        // PENDING is the initial state only; an outcome cannot be un-decided.
      case PENDING ->
          throw new IllegalRecruitmentStateException(
              "A recruitment cannot be returned to PENDING.",
              recruitment.getInterviewStatus().name(),
              recruitment.getFinalStatus().name(),
              "updateFinalStatus:PENDING");
    }

    // Persist first, so that a synchronization failure rolls the decision back instead of leaving
    // the recruitment decided and its application untouched.
    Recruitment saved = recruitmentRepository.save(recruitment);

    // Audit the final status change
    AuditService.recruitment(
        AuditService.RecruitmentEvent.FINAL_STATUS_CHANGE,
        callerOrgId,
        recruitmentId,
        AuditService.Outcome.SUCCESS,
        "oldStatus=" + oldStatus + ",newStatus=" + status.name());

    // Synchronize application status. Any failure here propagates and rolls the save back.
    synchronizeApplicationStatus(saved, status);

    return saved;
  }

  /**
   * Moves the application to the status the recruitment decision implies.
   *
   * <p>Failures are never swallowed. {@code ApplicationServiceUnavailableException} is deliberately
   * left to propagate untouched, because it is already the Resilience4j contract: an open circuit,
   * a full bulkhead and exhausted retries all arrive here as that one type, which the exception
   * handler renders as a 503. The {@code FeignException} branches are the path taken when the
   * circuit-breaker fallback is not in play, and they translate the downstream status rather than
   * discarding it — a refusal by the application's own state machine is a 409, not an outage.
   *
   * @param recruitment the saved recruitment with updated final status
   * @param finalStatus the new final status (HIRED or REJECTED)
   */
  private void synchronizeApplicationStatus(Recruitment recruitment, FinalStatus finalStatus) {
    UUID applicationId = recruitment.getApplicationId();
    String targetApplicationStatus = finalStatus == FinalStatus.HIRED ? "HIRED" : "REJECTED";

    log.info(
        "Synchronizing application {} status to {} for recruitment {} (final status: {})",
        applicationId,
        targetApplicationStatus,
        recruitment.getRecruitmentId(),
        finalStatus);

    ApplicationClient.ApplicationResponse updated;
    try {
      updated =
          applicationClient.updateStatus(
              applicationId, new ApplicationClient.StatusUpdateRequest(targetApplicationStatus));
    } catch (FeignException.NotFound notFound) {
      throw new RecruitmentNotFoundException("Application not found: " + applicationId);
    } catch (FeignException.Conflict | FeignException.BadRequest refused) {
      throw new ApplicationSyncConflictException(
          "Application "
              + applicationId
              + " cannot be moved to "
              + targetApplicationStatus
              + "; the recruitment decision was not applied.");
    } catch (FeignException other) {
      throw new ApplicationClient.ApplicationServiceUnavailableException(
          "Application service rejected the status update for application "
              + applicationId
              + ": "
              + other.status());
    }

    log.info(
        "Synchronized application {} status to {} (application reports status {})",
        applicationId,
        targetApplicationStatus,
        updated == null ? "unknown" : updated.status());
  }

  // ===================== Helpers =====================

  private Recruitment load(UUID recruitmentId) {
    return recruitmentRepository
        .findByRecruitmentId(recruitmentId)
        .orElseThrow(
            () -> new RecruitmentNotFoundException("Recruitment not found: " + recruitmentId));
  }

  /**
   * Loads a recruitment the caller's organization owns. This is the single gate every mutation goes
   * through, so a recruiter of another organization — or a caller whose token has no {@code orgId}
   * at all, which {@code isOwnedBy} rejects — can change nothing.
   */
  private Recruitment loadOwned(UUID recruitmentId, UUID callerOrgId) {
    Recruitment recruitment = load(recruitmentId);
    if (!recruitment.isOwnedBy(callerOrgId)) {
      AuditService.authorizationFailure(
          "recruitment", recruitmentId, callerOrgId, "Organization does not own this recruitment");
      throw new RecruitmentNotFoundException("Recruitment not found: " + recruitmentId);
    }
    return recruitment;
  }
}
