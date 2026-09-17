package com.smarthire.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Job posting aggregate. Fields are exactly the Project-44 source fields — {@code job_id}, {@code
 * company_name}, {@code role}, {@code location}, {@code status} — persisted to {@code
 * job_svc.jobs}. Behaviour (state-machine transitions, editing) arrives at Inc 2; Inc 0 exposes
 * read access only.
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

  /** Required by JPA. */
  protected Job() {}

  public Job(UUID jobId, String companyName, String role, String location, JobStatus status) {
    this.jobId = jobId;
    this.companyName = companyName;
    this.role = role;
    this.location = location;
    this.status = status;
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
}
