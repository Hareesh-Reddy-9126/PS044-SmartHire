package com.smarthire.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Application aggregate. Fields are the Project-44 source fields — {@code application_id}, {@code
 * job_id}, {@code candidate_id}, {@code resume_link}, {@code status} — persisted to {@code
 * application_svc.applications}.
 *
 * <p>The aggregate owns the lifecycle (D5): {@code APPLIED → UNDER_REVIEW → SHORTLISTED →
 * HIRED/REJECTED} plus candidate withdrawal {@code APPLIED/UNDER_REVIEW/SHORTLISTED → WITHDRAWN}.
 * Those are the only legal transitions; every other attempt throws {@link
 * IllegalApplicationStateException} deterministically, so the rule cannot be bypassed by calling
 * the repository directly or by a future caller that forgets a guard. Controllers and services
 * decide <em>who</em> may act; the aggregate decides <em>whether the move is legal</em>.
 *
 * <p>{@code candidateId} is the candidate UUID carried by the verified JWT at creation time. It is
 * deliberately write-once (no setter, {@code updatable = false}): ownership is never re-assigned by
 * a request, which is what makes the service-layer ownership check trustworthy.
 */
@Entity
@Table(name = "applications")
public class Application {

  @Id
  @Column(name = "application_id", nullable = false, updatable = false)
  private UUID applicationId;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "candidate_id", nullable = false, updatable = false)
  private UUID candidateId;

  @Column(name = "resume_link", nullable = false)
  private String resumeLink;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ApplicationStatus status;

  /** Required by JPA. */
  protected Application() {}

  /**
   * Creates an application in an explicit state. Every application has a candidate from the moment
   * it exists — there is no ownerless constructor, so an unowned row cannot be built in Java.
   */
  public Application(
      UUID applicationId,
      UUID jobId,
      UUID candidateId,
      String resumeLink,
      ApplicationStatus status) {
    this.applicationId = applicationId;
    this.jobId = jobId;
    this.candidateId = candidateId;
    this.resumeLink = resumeLink;
    this.status = status;
  }

  /** Creates a new APPLIED application — the only state an application may start in. */
  public static Application createApplied(
      UUID applicationId, UUID jobId, UUID candidateId, String resumeLink) {
    return new Application(
        applicationId, jobId, candidateId, resumeLink, ApplicationStatus.APPLIED);
  }

  /**
   * {@code APPLIED → UNDER_REVIEW}.
   *
   * @throws IllegalApplicationStateException if the application is not APPLIED
   */
  public void moveToUnderReview() {
    if (status != ApplicationStatus.APPLIED) {
      throw new IllegalApplicationStateException(
          "Only an APPLIED application can move to UNDER_REVIEW; this application is "
              + status
              + ".",
          status,
          "moveToUnderReview");
    }
    status = ApplicationStatus.UNDER_REVIEW;
  }

  /**
   * {@code UNDER_REVIEW → SHORTLISTED}.
   *
   * @throws IllegalApplicationStateException if the application is not UNDER_REVIEW
   */
  public void shortlist() {
    if (status != ApplicationStatus.UNDER_REVIEW) {
      throw new IllegalApplicationStateException(
          "Only an UNDER_REVIEW application can be shortlisted; this application is "
              + status
              + ".",
          status,
          "shortlist");
    }
    status = ApplicationStatus.SHORTLISTED;
  }

  /**
   * {@code SHORTLISTED → HIRED}.
   *
   * @throws IllegalApplicationStateException if the application is not SHORTLISTED
   */
  public void hire() {
    if (status != ApplicationStatus.SHORTLISTED) {
      throw new IllegalApplicationStateException(
          "Only a SHORTLISTED application can be hired; this application is " + status + ".",
          status,
          "hire");
    }
    status = ApplicationStatus.HIRED;
  }

  /**
   * {@code SHORTLISTED → REJECTED}.
   *
   * @throws IllegalApplicationStateException if the application is not SHORTLISTED
   */
  public void reject() {
    if (status != ApplicationStatus.SHORTLISTED) {
      throw new IllegalApplicationStateException(
          "Only a SHORTLISTED application can be rejected; this application is " + status + ".",
          status,
          "reject");
    }
    status = ApplicationStatus.REJECTED;
  }

  /**
   * Candidate withdrawal: {@code APPLIED/UNDER_REVIEW/SHORTLISTED → WITHDRAWN}.
   *
   * @throws IllegalApplicationStateException if the application is not in a withdrawable state
   */
  public void withdraw() {
    if (status != ApplicationStatus.APPLIED
        && status != ApplicationStatus.UNDER_REVIEW
        && status != ApplicationStatus.SHORTLISTED) {
      throw new IllegalApplicationStateException(
          "Only an APPLIED, UNDER_REVIEW, or SHORTLISTED application can be withdrawn; this application is "
              + status
              + ".",
          status,
          "withdraw");
    }
    status = ApplicationStatus.WITHDRAWN;
  }

  /** True when the application is in a terminal state (no further transitions allowed). */
  public boolean isTerminal() {
    return status == ApplicationStatus.HIRED
        || status == ApplicationStatus.REJECTED
        || status == ApplicationStatus.WITHDRAWN;
  }

  /** True when the given candidate owns this application. A {@code null} caller never owns. */
  public boolean isOwnedBy(UUID callerCandidateId) {
    return callerCandidateId != null && callerCandidateId.equals(candidateId);
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public UUID getCandidateId() {
    return candidateId;
  }

  public String getResumeLink() {
    return resumeLink;
  }

  public ApplicationStatus getStatus() {
    return status;
  }
}
