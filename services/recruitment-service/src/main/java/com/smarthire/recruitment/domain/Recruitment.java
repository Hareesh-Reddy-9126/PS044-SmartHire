package com.smarthire.recruitment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Recruitment aggregate. Fields are the Project-44 source fields — {@code recruitment_id}, {@code
 * application_id}, {@code interview_status}, {@code final_status} — persisted to {@code
 * recruitment_svc.recruitments}.
 *
 * <p>The aggregate owns two independent lifecycles:
 *
 * <ol>
 *   <li><b>Interview status</b> — {@code NOT_SCHEDULED → SCHEDULED → COMPLETED/NO_SHOW}
 *   <li><b>Final status</b> — {@code PENDING → HIRED/REJECTED}
 * </ol>
 *
 * Those are the only legal transitions; every other attempt throws {@link
 * IllegalRecruitmentStateException} deterministically, so the rule cannot be bypassed.
 *
 * <p>Alongside them the aggregate stores the facts authorization is decided from: {@code jobId} and
 * {@code ownerOrgId} (which organization's posting this recruitment belongs to) and {@code
 * candidateId} (whose application it tracks). They are resolved once, at shortlist time, from the
 * Application and Job services, and are write-once here — {@code updatable = false} on all of them,
 * with no setters — so no request can re-point a recruitment at a different organization or
 * candidate after the fact.
 */
@Entity
@Table(name = "recruitments")
public class Recruitment {

  @Id
  @Column(name = "recruitment_id", nullable = false, updatable = false)
  private UUID recruitmentId;

  @Column(name = "application_id", nullable = false, updatable = false)
  private UUID applicationId;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "owner_org_id", nullable = false, updatable = false)
  private UUID ownerOrgId;

  @Column(name = "candidate_id", nullable = false, updatable = false)
  private UUID candidateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "interview_status", nullable = false)
  private InterviewStatus interviewStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "final_status", nullable = false)
  private FinalStatus finalStatus;

  /** Required by JPA. */
  protected Recruitment() {}

  /** Creates a recruitment with explicit states — the form the persistence layer reconstitutes. */
  public Recruitment(
      UUID recruitmentId,
      UUID applicationId,
      UUID jobId,
      UUID ownerOrgId,
      UUID candidateId,
      InterviewStatus interviewStatus,
      FinalStatus finalStatus) {
    this.recruitmentId = recruitmentId;
    this.applicationId = applicationId;
    this.jobId = jobId;
    this.ownerOrgId = ownerOrgId;
    this.candidateId = candidateId;
    this.interviewStatus = interviewStatus;
    this.finalStatus = finalStatus;
  }

  /**
   * Shortlists an application: a new recruitment at the start of both lifecycles.
   *
   * <p>{@code ownerOrgId} and {@code candidateId} are supplied by the caller having read them from
   * the Job and Application services — never from a request body.
   */
  public static Recruitment shortlist(
      UUID recruitmentId, UUID applicationId, UUID jobId, UUID ownerOrgId, UUID candidateId) {
    return new Recruitment(
        recruitmentId,
        applicationId,
        jobId,
        ownerOrgId,
        candidateId,
        InterviewStatus.NOT_SCHEDULED,
        FinalStatus.PENDING);
  }

  // ===================== Ownership =====================

  /** True when this recruitment belongs to the given organization's job posting. */
  public boolean isOwnedBy(UUID orgId) {
    return orgId != null && orgId.equals(ownerOrgId);
  }

  /** True when this recruitment tracks the given candidate's own application (AC-5 tracking). */
  public boolean belongsToCandidate(UUID candidate) {
    return candidate != null && candidate.equals(candidateId);
  }

  // ===================== Interview status transitions =====================

  /**
   * {@code NOT_SCHEDULED → SCHEDULED}.
   *
   * @throws IllegalRecruitmentStateException if the recruitment is not NOT_SCHEDULED
   */
  public void scheduleInterview() {
    if (interviewStatus != InterviewStatus.NOT_SCHEDULED) {
      throw new IllegalRecruitmentStateException(
          "Only a NOT_SCHEDULED recruitment can be scheduled; current interview status is "
              + interviewStatus
              + ".",
          interviewStatus.name(),
          finalStatus.name(),
          "scheduleInterview");
    }
    interviewStatus = InterviewStatus.SCHEDULED;
  }

  /**
   * {@code SCHEDULED → COMPLETED}.
   *
   * @throws IllegalRecruitmentStateException if the recruitment is not SCHEDULED
   */
  public void completeInterview() {
    if (interviewStatus != InterviewStatus.SCHEDULED) {
      throw new IllegalRecruitmentStateException(
          "Only a SCHEDULED interview can be marked completed; current interview status is "
              + interviewStatus
              + ".",
          interviewStatus.name(),
          finalStatus.name(),
          "completeInterview");
    }
    interviewStatus = InterviewStatus.COMPLETED;
  }

  /**
   * {@code SCHEDULED → NO_SHOW}.
   *
   * @throws IllegalRecruitmentStateException if the recruitment is not SCHEDULED
   */
  public void markNoShow() {
    if (interviewStatus != InterviewStatus.SCHEDULED) {
      throw new IllegalRecruitmentStateException(
          "Only a SCHEDULED interview can be marked NO_SHOW; current interview status is "
              + interviewStatus
              + ".",
          interviewStatus.name(),
          finalStatus.name(),
          "markNoShow");
    }
    interviewStatus = InterviewStatus.NO_SHOW;
  }

  // ===================== Final status transitions =====================

  /**
   * {@code PENDING → HIRED}.
   *
   * @throws IllegalRecruitmentStateException if the recruitment is not PENDING
   */
  public void hire() {
    if (finalStatus != FinalStatus.PENDING) {
      throw new IllegalRecruitmentStateException(
          "Only a PENDING recruitment can be hired; current final status is " + finalStatus + ".",
          interviewStatus.name(),
          finalStatus.name(),
          "hire");
    }
    finalStatus = FinalStatus.HIRED;
  }

  /**
   * {@code PENDING → REJECTED}.
   *
   * @throws IllegalRecruitmentStateException if the recruitment is not PENDING
   */
  public void reject() {
    if (finalStatus != FinalStatus.PENDING) {
      throw new IllegalRecruitmentStateException(
          "Only a PENDING recruitment can be rejected; current final status is "
              + finalStatus
              + ".",
          interviewStatus.name(),
          finalStatus.name(),
          "reject");
    }
    finalStatus = FinalStatus.REJECTED;
  }

  // ===================== Getters =====================

  public UUID getRecruitmentId() {
    return recruitmentId;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public UUID getOwnerOrgId() {
    return ownerOrgId;
  }

  public UUID getCandidateId() {
    return candidateId;
  }

  public InterviewStatus getInterviewStatus() {
    return interviewStatus;
  }

  public FinalStatus getFinalStatus() {
    return finalStatus;
  }
}
