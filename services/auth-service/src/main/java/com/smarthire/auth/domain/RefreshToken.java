package com.smarthire.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A persisted refresh token in a rotation family (ADR-0009). Only the SHA-256 {@link #tokenHash} of
 * the opaque token is stored, never the raw value (governance §17). On each refresh the presented
 * token is rotated: the old row is {@link #revoke(UUID) revoked} and points at its successor via
 * {@link #replacedBy}. Presenting an already-revoked token is reuse — the whole {@link #familyId}
 * is then revoked.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  @Column(name = "replaced_by")
  private UUID replacedBy;

  /** Required by JPA. */
  protected RefreshToken() {}

  public RefreshToken(
      UUID id, UUID userId, UUID familyId, String tokenHash, Instant issuedAt, Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.revoked = false;
  }

  /** Marks this token revoked, recording the successor that replaced it (may be {@code null}). */
  public void revoke(UUID replacedById) {
    this.revoked = true;
    this.replacedBy = replacedById;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public UUID getReplacedBy() {
    return replacedBy;
  }
}
