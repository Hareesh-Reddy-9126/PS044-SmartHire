package com.smarthire.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Job posting aggregate. Fields are the Project-44 source fields — {@code job_id}, {@code
 * company_name}, {@code role}, {@code location}, {@code status} — plus the Inc 2 ownership column
 * {@code owner_org_id}, persisted to {@code job_svc.jobs}.
 *
 * <p>The aggregate owns the lifecycle (D5): {@code DRAFT → OPEN → CLOSED → OPEN}. Those are the
 * only legal transitions; every other attempt throws {@link IllegalJobStateException}
 * deterministically, so the rule cannot be bypassed by calling the repository directly or by a
 * future caller that forgets a guard. Controllers and services decide <em>who</em> may act; the
 * aggregate decides <em>whether the move is legal</em>.
 *
 * <p>{@code ownerOrgId} is the organization UUID carried by the verified JWT at creation time. It
 * is deliberately write-once (no setter, {@code updatable = false}): ownership is never re-assigned
 * by a request, which is what makes the service-layer ownership check trustworthy.
 */
@Entity
@Table(name = "jobs")
public class Job {

  @Id
  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "company_name", nullable = false)
  private String companyName;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "location", nullable = false)
  private String location;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private JobStatus status;

  @Column(name = "owner_org_id", nullable = false, updatable = false)
  private UUID ownerOrgId;

  /** Required by JPA. */
  protected Job() {}

  /**
   * Creates a job in an explicit state. Every job has an owning organization from the moment it
   * exists — there is no ownerless constructor, so an unowned row cannot be built in Java.
   */
  public Job(
      UUID jobId,
      String companyName,
      String role,
      String location,
      JobStatus status,
      UUID ownerOrgId) {
    this.jobId = jobId;
    this.companyName = companyName;
    this.role = role;
    this.location = location;
    this.status = status;
    this.ownerOrgId = ownerOrgId;
  }

  /**
   * Creates a new DRAFT job owned by the given organization — the only state a job may start in.
   */
  public static Job createDraft(
      UUID jobId, String companyName, String role, String location, UUID ownerOrgId) {
    return new Job(jobId, companyName, role, location, JobStatus.DRAFT, ownerOrgId);
  }

  /** Edits the descriptive fields. Status and ownership are untouched by an edit. */
  public void updateDetails(String companyName, String role, String location) {
    this.companyName = companyName;
    this.role = role;
    this.location = location;
  }

  /**
   * {@code DRAFT → OPEN}.
   *
   * @throws IllegalJobStateException if the job is not DRAFT (including an already-OPEN job:
   *     transitions are strict, not idempotent, so a repeated call is reported rather than silently
   *     accepted)
   */
  public void open() {
    if (status != JobStatus.DRAFT) {
      throw new IllegalJobStateException(
          "Only a DRAFT job can be opened; this job is " + status + ".", status, "open");
    }
    status = JobStatus.OPEN;
  }

  /**
   * {@code OPEN → CLOSED}.
   *
   * @throws IllegalJobStateException if the job is not OPEN
   */
  public void close() {
    if (status != JobStatus.OPEN) {
      throw new IllegalJobStateException(
          "Only an OPEN job can be closed; this job is " + status + ".", status, "close");
    }
    status = JobStatus.CLOSED;
  }

  /**
   * {@code CLOSED → OPEN}.
   *
   * @throws IllegalJobStateException if the job is not CLOSED
   */
  public void reopen() {
    if (status != JobStatus.CLOSED) {
      throw new IllegalJobStateException(
          "Only a CLOSED job can be reopened; this job is " + status + ".", status, "reopen");
    }
    status = JobStatus.OPEN;
  }

  /** True when the job is visible to anonymous and non-owning callers (decision 5). */
  public boolean isPubliclyVisible() {
    return status == JobStatus.OPEN;
  }

  /** True when the given organization owns this job. A {@code null} caller org never owns. */
  public boolean isOwnedBy(UUID callerOrgId) {
    return callerOrgId != null && callerOrgId.equals(ownerOrgId);
  }

  public UUID getJobId() {
    return jobId;
  }

  public String getCompanyName() {
    return companyName;
  }

  public String getRole() {
    return role;
  }

  public String getLocation() {
    return location;
  }

  public JobStatus getStatus() {
    return status;
  }

  public UUID getOwnerOrgId() {
    return ownerOrgId;
  }
}
