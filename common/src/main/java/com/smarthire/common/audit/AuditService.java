package com.smarthire.common.audit;

import com.smarthire.common.web.CorrelationId;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Centralized audit logging for security-relevant events.
 *
 * <p>Emits structured JSON log lines to the SLF4J logger, including the correlation id from MDC
 * (populated by {@link CorrelationIdFilter}) so that a single request's audit trail can be
 * reconstructed across service boundaries.
 *
 * <p>Never logs sensitive data: passwords, JWTs, refresh tokens, authorization headers, document
 * contents, or full request bodies.
 *
 * <p>Audit events are logged at INFO level so they survive in production log aggregation.
 */
public final class AuditService {

  private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

  private AuditService() {}

  /**
   * Emits an authentication audit event.
   *
   * @param eventType the authentication event (REGISTER, LOGIN_SUCCESS, LOGIN_FAILURE, REFRESH,
   *     LOGOUT)
   * @param userId the user identifier (from JWT sub), or null if unknown (e.g., failed login)
   * @param email the email used in the attempt (safe to log; not a secret)
   * @param outcome SUCCESS or FAILURE
   * @param details optional additional context (must not contain secrets)
   */
  public static void auth(
      AuthEvent eventType, UUID userId, String email, Outcome outcome, String details) {
    log("AUTH", eventType.name(), userId, email, outcome, details, null, null);
  }

  /**
   * Emits a job lifecycle audit event.
   *
   * @param eventType the job event (CREATE, UPDATE, OPEN, CLOSE, REOPEN)
   * @param actorOrgId the organization UUID from the caller's JWT
   * @param jobId the job identifier
   * @param outcome SUCCESS or FAILURE
   * @param details optional additional context
   */
  public static void job(
      JobEvent eventType, UUID actorOrgId, UUID jobId, Outcome outcome, String details) {
    log("JOB", eventType.name(), actorOrgId, null, outcome, details, "job", jobId);
  }

  /**
   * Emits an application lifecycle audit event.
   *
   * @param eventType the application event (CREATE, WITHDRAW, STATUS_CHANGE)
   * @param actorId the actor UUID (candidateId for create/withdraw, orgId for status change)
   * @param applicationId the application identifier
   * @param outcome SUCCESS or FAILURE
   * @param details optional additional context (e.g., old/new status)
   */
  public static void application(
      ApplicationEvent eventType,
      UUID actorId,
      UUID applicationId,
      Outcome outcome,
      String details) {
    log(
        "APPLICATION",
        eventType.name(),
        actorId,
        null,
        outcome,
        details,
        "application",
        applicationId);
  }

  /**
   * Emits a recruitment workflow audit event.
   *
   * @param eventType the recruitment event (SHORTLIST, INTERVIEW_STATUS_CHANGE,
   *     FINAL_STATUS_CHANGE)
   * @param actorOrgId the organization UUID from the caller's JWT
   * @param recruitmentId the recruitment identifier
   * @param outcome SUCCESS or FAILURE
   * @param details optional additional context (e.g., old/new status)
   */
  public static void recruitment(
      RecruitmentEvent eventType,
      UUID actorOrgId,
      UUID recruitmentId,
      Outcome outcome,
      String details) {
    log(
        "RECRUITMENT",
        eventType.name(),
        actorOrgId,
        null,
        outcome,
        details,
        "recruitment",
        recruitmentId);
  }

  /**
   * Emits a document audit event.
   *
   * @param eventType the document event (UPLOAD, DELETE)
   * @param actorCandidateId the candidate UUID from the caller's JWT
   * @param documentId the document identifier
   * @param outcome SUCCESS or FAILURE
   * @param details optional additional context (e.g., document type, filename)
   */
  public static void document(
      DocumentEvent eventType,
      UUID actorCandidateId,
      UUID documentId,
      Outcome outcome,
      String details) {
    log(
        "DOCUMENT",
        eventType.name(),
        actorCandidateId,
        null,
        outcome,
        details,
        "document",
        documentId);
  }

  /**
   * Emits an authorization failure audit event.
   *
   * @param resourceType the type of resource access was attempted on
   * @param resourceId the resource identifier
   * @param actorId the actor identifier from JWT (if available)
   * @param reason the authorization failure reason (safe to log)
   */
  public static void authorizationFailure(
      String resourceType, UUID resourceId, UUID actorId, String reason) {
    log(
        "AUTHORIZATION_FAILURE",
        "DENIED",
        actorId,
        null,
        Outcome.FAILURE,
        reason,
        resourceType,
        resourceId);
  }

  /** Generic audit log emission. Formats as structured JSON for log aggregation. */
  private static void log(
      String category,
      String event,
      UUID actorId,
      String email,
      Outcome outcome,
      String details,
      String resourceType,
      UUID resourceId) {

    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    Instant timestamp = Instant.now();

    // Build structured log message as JSON
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    appendField(sb, "timestamp", timestamp.toString());
    appendField(sb, "category", category);
    appendField(sb, "event", event);
    appendField(sb, "outcome", outcome.name());
    if (actorId != null) appendField(sb, "actorId", actorId.toString());
    if (email != null) appendField(sb, "email", email);
    if (resourceType != null) appendField(sb, "resourceType", resourceType);
    if (resourceId != null) appendField(sb, "resourceId", resourceId.toString());
    if (details != null) appendField(sb, "details", details);
    if (correlationId != null) appendField(sb, "correlationId", correlationId);
    sb.append("}");

    auditLog.info(sb.toString());
  }

  private static void appendField(StringBuilder sb, String key, String value) {
    if (sb.length() > 1) {
      sb.append(",");
    }
    sb.append('"').append(key).append("\":\"").append(escapeJson(value)).append('"');
  }

  private static String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  /** Authentication event types. */
  public enum AuthEvent {
    REGISTER,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    REFRESH,
    LOGOUT
  }

  /** Job lifecycle event types. */
  public enum JobEvent {
    CREATE,
    UPDATE,
    OPEN,
    CLOSE,
    REOPEN
  }

  /** Application lifecycle event types. */
  public enum ApplicationEvent {
    CREATE,
    WITHDRAW,
    STATUS_CHANGE
  }

  /** Recruitment workflow event types. */
  public enum RecruitmentEvent {
    SHORTLIST,
    INTERVIEW_STATUS_CHANGE,
    FINAL_STATUS_CHANGE
  }

  /** Document event types. */
  public enum DocumentEvent {
    UPLOAD,
    DELETE
  }

  /** Operation outcome. */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }
}
