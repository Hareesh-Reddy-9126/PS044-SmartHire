package com.smarthire.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Company domain entity. A company is an independent recruiting organization. Companies start in
 * {@code PENDING_APPROVAL} and are activated by a Platform Admin. Recruiters and Company Admins
 * belong to exactly one active company.
 *
 * <p>Company status lifecycle:
 *
 * <ul>
 *   <li>{@link #PENDING_APPROVAL} - submitted but not yet reviewed
 *   <li>{@link #ACTIVE} - approved and operational
 *   <li>{@link #REJECTED} - rejected by Platform Admin
 *   <li>{@link #SUSPENDED} - temporarily disabled by Platform Admin
 * </ul>
 */
@Entity
@Table(name = "companies")
public class Company {

  public enum Status {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    SUSPENDED
  }

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  /** Required by JPA. */
  protected Company() {}

  public Company(
      UUID id,
      String name,
      Status status,
      UUID createdBy,
      Instant createdAt,
      Instant approvedAt,
      UUID approvedBy,
      String rejectionReason) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.createdAt = createdAt;
    this.approvedAt = approvedAt;
    this.approvedBy = approvedBy;
    this.rejectionReason = rejectionReason;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void approve(UUID approvedBy, Instant approvedAt) {
    if (status != Status.PENDING_APPROVAL) {
      throw new IllegalStateException(
          "Only PENDING_APPROVAL companies can be approved; current status is " + status);
    }
    this.status = Status.ACTIVE;
    this.approvedBy = approvedBy;
    this.approvedAt = approvedAt;
  }

  public void reject(UUID approvedBy, Instant approvedAt, String rejectionReason) {
    if (status != Status.PENDING_APPROVAL) {
      throw new IllegalStateException(
          "Only PENDING_APPROVAL companies can be rejected; current status is " + status);
    }
    this.status = Status.REJECTED;
    this.approvedBy = approvedBy;
    this.approvedAt = approvedAt;
    this.rejectionReason = rejectionReason;
  }

  public void suspend(UUID suspendedBy, Instant suspendedAt) {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException(
          "Only ACTIVE companies can be suspended; current status is " + status);
    }
    this.status = Status.SUSPENDED;
    this.approvedBy = suspendedBy;
    this.approvedAt = suspendedAt;
  }

  public void reactivate(UUID reactivatedBy, Instant reactivatedAt) {
    if (status != Status.SUSPENDED) {
      throw new IllegalStateException(
          "Only SUSPENDED companies can be reactivated; current status is " + status);
    }
    this.status = Status.ACTIVE;
    this.approvedBy = reactivatedBy;
    this.approvedAt = reactivatedAt;
    this.rejectionReason = null;
  }
}
