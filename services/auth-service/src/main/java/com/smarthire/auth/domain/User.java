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
 * A registered identity. Holds the credential ({@link #passwordHash}, a {@code PasswordEncoder}-
 * prefixed BCrypt hash — never plaintext), the {@link Role}, and an optional owning organization.
 * The {@code sub}/{@code roles}/{@code orgId} JWT claims are derived from this record at login.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private Role role;

  @Column(name = "org_id")
  private UUID orgId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected User() {}

  public User(
      UUID id, String email, String passwordHash, Role role, UUID orgId, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.orgId = orgId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
