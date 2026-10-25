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
 * Company membership domain entity. A membership links a user to a company with a specific role
 * and status.
 *
 * <p>Membership lifecycle:
 * <ul>
 *   <li>{@link #PENDING} - invited or requested, awaiting approval</li>
 *   <li>{@link #ACTIVE} - approved and active member</li>
 *   <li>{@link #REJECTED} - invitation/request rejected</li>
 *   <li>{@link #REVOKED} - membership revoked by company admin</li>
 *   <li>{@link #EXPIRED} - invitation expired before acceptance</li>
 * </ul>
 *
 * <p>Membership roles:
 * <ul>
 *   <li>{@link #COMPANY_ADMIN} - can manage company, invite/revoke members, approve jobs</li>
 *   <li>{@link #RECRUITER} - can create and manage company jobs and applications</li>
 * </ul>
 */
@Entity
@Table(name = "company_memberships")
public class CompanyMembership {

  public enum Role {
    COMPANY_ADMIN,
    RECRUITER
  }

  public enum Status {
    PENDING,
    ACTIVE,
    REJECTED,
    REVOKED,
    EXPIRED
  }

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private UUID companyId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status;

  @Column(name = "invited_by")
  private UUID invitedBy;

  @Column(name = "invited_at")
  private Instant invitedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "invitation_token")
  private String invitationToken;

  @Column(name = "invitation_expires_at")
  private Instant invitationExpiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected CompanyMembership() {}

  public CompanyMembership(
      UUID id,
      UUID companyId,
      UUID userId,
      Role role,
      Status status,
      UUID invitedBy,
      Instant invitedAt,
      Instant approvedAt,
      UUID approvedBy,
      String invitationToken,
      Instant invitationExpiresAt,
      Instant createdAt) {
    this.id = id;
    this.companyId = companyId;
    this.userId = userId;
    this.role = role;
    this.status = status;
    this.invitedBy = invitedBy;
    this.invitedAt = invitedAt;
    this.approvedAt = approvedAt;
    this.approvedBy = approvedBy;
    this.invitationToken = invitationToken;
    this.invitationExpiresAt = invitationExpiresAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCompanyId() {
    return companyId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Role getRole() {
    return role;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getInvitedBy() {
    return invitedBy;
  }

  public Instant getInvitedAt() {
    return invitedAt;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public String getInvitationToken() {
    return invitationToken;
  }

  public Instant getInvitationExpiresAt() {
    return invitationExpiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void approve(UUID approvedBy, Instant approvedAt) {
    if (status != Status.PENDING) {
      throw new IllegalStateException(
          "Only PENDING memberships can be approved; current status is " + status);
    }
    this.status = Status.ACTIVE;
    this.approvedBy = approvedBy;
    this.approvedAt = approvedAt;
  }

  public void reject(UUID approvedBy, Instant approvedAt) {
    if (status != Status.PENDING) {
      throw new IllegalStateException(
          "Only PENDING memberships can be rejected; current status is " + status);
    }
    this.status = Status.REJECTED;
    this.approvedBy = approvedBy;
    this.approvedAt = approvedAt;
  }

  public void revoke(UUID revokedBy, Instant revokedAt) {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException(
          "Only ACTIVE memberships can be revoked; current status is " + status);
    }
    this.status = Status.REVOKED;
    this.approvedBy = revokedBy;
    this.approvedAt = revokedAt;
  }

  public void expire() {
    if (status != Status.PENDING) {
      throw new IllegalStateException(
          "Only PENDING memberships can expire; current status is " + status);
    }
    this.status = Status.EXPIRED;
  }
}
