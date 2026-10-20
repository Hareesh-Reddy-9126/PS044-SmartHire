package com.smarthire.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * AI-generated match score record.
 *
 * <p>Persisted to {@code ai_svc.match_scores}. All fields are advisory only — this service never
 * makes autonomous recruitment decisions. Human-in-the-loop is required for any action based on
 * these scores (ADR-0015, governance §43).
 *
 * <p>The {@code overriddenBy}, {@code overriddenAt}, and {@code overrideReason} fields capture
 * recruiter overrides for auditability and bias evaluation.
 */
@Entity
@Table(name = "match_scores", schema = "ai_svc")
public class MatchScore {

  @Id
  @Column(name = "match_score_id", nullable = false, updatable = false)
  private UUID matchScoreId;

  @Column(name = "application_id", nullable = false, updatable = false)
  private UUID applicationId;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "score", nullable = false)
  private Short score;

  @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
  private String rationale;

  @Column(name = "model", nullable = false, length = 100)
  private String model;

  @Column(name = "prompt_version", nullable = false, length = 50)
  private String promptVersion;

  @Column(name = "confidence")
  private Short confidence;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "overridden_by")
  private UUID overriddenBy;

  @Column(name = "overridden_at")
  private Instant overriddenAt;

  @Column(name = "override_reason", columnDefinition = "TEXT")
  private String overrideReason;

  /** Required by JPA. */
  protected MatchScore() {}

  /** Creates a match score with explicit state — the form the persistence layer reconstitutes. */
  public MatchScore(
      UUID matchScoreId,
      UUID applicationId,
      UUID jobId,
      Short score,
      String rationale,
      String model,
      String promptVersion,
      Short confidence,
      Instant createdAt,
      UUID overriddenBy,
      Instant overriddenAt,
      String overrideReason) {
    this.matchScoreId = matchScoreId;
    this.applicationId = applicationId;
    this.jobId = jobId;
    this.score = score;
    this.rationale = rationale;
    this.model = model;
    this.promptVersion = promptVersion;
    this.confidence = confidence;
    this.createdAt = createdAt;
    this.overriddenBy = overriddenBy;
    this.overriddenAt = overriddenAt;
    this.overrideReason = overrideReason;
  }

  /**
   * Creates a new AI-generated match score.
   *
   * @param matchScoreId the unique identifier
   * @param applicationId the application UUID
   * @param jobId the job UUID
   * @param score the match score (0-100)
   * @param rationale the AI-generated rationale
   * @param model the model identifier used
   * @param promptVersion the prompt version used
   * @param confidence the confidence score (0-100, optional)
   * @return a new MatchScore in advisory state
   */
  public static MatchScore create(
      UUID matchScoreId,
      UUID applicationId,
      UUID jobId,
      Short score,
      String rationale,
      String model,
      String promptVersion,
      Short confidence) {
    return new MatchScore(
        matchScoreId,
        applicationId,
        jobId,
        score,
        rationale,
        model,
        promptVersion,
        confidence,
        Instant.now(),
        null,
        null,
        null);
  }

  /**
   * Marks this match score as overridden by a recruiter.
   *
   * @param recruiterId the recruiter UUID who overrode the recommendation
   * @param reason the reason for the override
   */
  public void override(UUID recruiterId, String reason) {
    this.overriddenBy = recruiterId;
    this.overriddenAt = Instant.now();
    this.overrideReason = reason;
  }

  // ===================== Getters =====================

  public UUID getMatchScoreId() {
    return matchScoreId;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public Short getScore() {
    return score;
  }

  public String getRationale() {
    return rationale;
  }

  public String getModel() {
    return model;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public Short getConfidence() {
    return confidence;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getOverriddenBy() {
    return overriddenBy;
  }

  public Instant getOverriddenAt() {
    return overriddenAt;
  }

  public String getOverrideReason() {
    return overrideReason;
  }

  public boolean isOverridden() {
    return overriddenBy != null;
  }
}