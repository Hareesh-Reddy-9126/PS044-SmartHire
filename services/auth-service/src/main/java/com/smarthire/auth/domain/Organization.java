package com.smarthire.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Employer organization. Recruiters may belong to one (see {@link User#getOrgId()}); candidates do
 * not. Recruiter registration creates a dedicated organization; seeded demo accounts attach to the
 * demo organization created by the initial migration.
 */
@Entity
@Table(name = "organizations")
public class Organization {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected Organization() {}

  public Organization(UUID id, String name, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
