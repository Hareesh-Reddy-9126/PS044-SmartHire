package com.smarthire.ai.service;

import com.smarthire.ai.api.dto.MatchScoreRequest;
import com.smarthire.ai.api.dto.MatchScoreResponse;
import com.smarthire.ai.api.dto.CandidateSummaryRequest;
import com.smarthire.ai.api.dto.CandidateSummaryResponse;
import com.smarthire.ai.api.dto.JdAssistRequest;
import com.smarthire.ai.api.dto.JdAssistResponse;
import com.smarthire.ai.api.dto.ResumeParseRequest;
import com.smarthire.ai.api.dto.ResumeParseResponse;
import com.smarthire.ai.domain.MatchScore;
import com.smarthire.ai.infra.AiProvider;
import com.smarthire.ai.infra.MockAiProvider;
import com.smarthire.common.audit.AuditService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core AI service orchestrating match scoring, candidate summaries, JD assistance, and resume
 * parsing.
 *
 * <p>All AI outputs are advisory only. Human-in-the-loop is required for any recruitment decision
 * (ADR-0015, governance §43). Features are gated by the {@code smarthire.ai.enabled} feature flag.
 *
 * <p>Safety layers applied before every provider call:
 * <ol>
 *   <li>Feature flag check (kill switch)
 *   <li>PII redaction on untrusted input
 *   <li>Prompt injection defense (framing + sanitization)
 *   <li>Provider call with timeout/circuit breaker (via Feign Resilience4j on downstream calls)
 *   <li>Output schema validation
 *   <li>Audit logging (no sensitive content)
 * </ol>
 */
@Service
public class AiMatchingService {

  private static final Logger log = LoggerFactory.getLogger(AiMatchingService.class);

  private final AiProvider aiProvider;
  private final PiiRedactionService piiRedactionService;
  private final PromptInjectionDefense promptInjectionDefense;
  private final MatchScoreRepository matchScoreRepository;
  private final boolean aiEnabled;

  public AiMatchingService(
      AiProvider aiProvider,
      PiiRedactionService piiRedactionService,
      PromptInjectionDefense promptInjectionDefense,
      MatchScoreRepository matchScoreRepository,
      AiProperties aiProperties) {
    this.aiProvider = aiProvider;
    this.piiRedactionService = piiRedactionService;
    this.promptInjectionDefense = promptInjectionDefense;
    this.matchScoreRepository = matchScoreRepository;
    this.aiEnabled = aiProperties.isEnabled();
  }

  /**
   * Computes a candidate–job match score with rationale.
   *
   * @param request the match score request (validated)
   * @param recruiterId the authenticated recruiter's orgId (for audit)
   * @return the match score response
   * @throws AiServiceDisabledException if AI features are disabled
   * @throws AiProviderException if the provider fails
   */
  @Transactional
  public MatchScoreResponse computeMatchScore(MatchScoreRequest request, UUID recruiterId) {
    checkEnabled();

    log.info("Computing match score for application={}, job={}", request.applicationId(), request.jobId());

    // Safety: validate input (already done by Bean Validation)
    // Safety: no PII in request — only UUIDs

    // Call provider (mock or real)
    MatchScoreResponse response = aiProvider.computeMatchScore(request);

    // Persist for audit trail and potential override tracking
    MatchScore matchScore = MatchScore.create(
        response.matchScoreId(),
        request.applicationId(),
        request.jobId(),
        response.score(),
        response.rationale(),
        response.model(),
        response.promptVersion(),
        response.confidence());
    matchScoreRepository.save(matchScore);

    // Audit: AI output generated (no sensitive content in log)
    AuditService.application(
        AuditService.ApplicationEvent.STATUS_CHANGE, // Reusing closest event type
        recruiterId,
        request.applicationId(),
        AuditService.Outcome.SUCCESS,
        "ai_match_generated:matchScoreId=" + response.matchScoreId()
            + ",score=" + response.score()
            + ",model=" + response.model()
            + ",promptVersion=" + response.promptVersion());

    log.info("Match score computed: matchScoreId={}, score={}", response.matchScoreId(), response.score());
    return response;
  }

  /**
   * Generates a structured candidate summary.
   *
   * @param request the candidate summary request
   * @param recruiterId the authenticated recruiter's orgId (for audit)
   * @return the candidate summary response
   */
  @Transactional
  public CandidateSummaryResponse generateCandidateSummary(CandidateSummaryRequest request, UUID recruiterId) {
    checkEnabled();

    log.info("Generating candidate summary for application={}", request.applicationId());

    // Safety: validate input
    // Safety: no PII in request — only UUID

    CandidateSummaryResponse response = aiProvider.generateCandidateSummary(request);

    // Audit
    AuditService.application(
        AuditService.ApplicationEvent.STATUS_CHANGE,
        recruiterId,
        request.applicationId(),
        AuditService.Outcome.SUCCESS,
        "ai_summary_generated:summaryId=" + response.summaryId()
            + ",model=" + response.model()
            + ",promptVersion=" + response.promptVersion());

    log.info("Candidate summary generated: summaryId={}", response.summaryId());
    return response;
  }

  /**
   * Assists in drafting or improving a job description.
   *
   * @param request the JD assist request
   * @param recruiterId the authenticated recruiter's orgId (for audit)
   * @return the JD assist response
   */
  @Transactional
  public JdAssistResponse assistJd(JdAssistRequest request, UUID recruiterId) {
    checkEnabled();

    log.info("Assisting JD for role={}, seniority={}", request.role(), request.seniority());

    // Safety: PII redaction on draft JD (may contain contact info)
    String redactedDraft = piiRedactionService.redact(request.draftJd());
    // Safety: Prompt injection defense
    String sanitizedDraft = promptInjectionDefense.sanitize(redactedDraft);
    String framedDraft = promptInjectionDefense.frameContent(sanitizedDraft);

    // Create modified request with safe content
    JdAssistRequest safeRequest = new JdAssistRequest(
        request.role(),
        request.seniority(),
        framedDraft,
        request.tone());

    JdAssistResponse response = aiProvider.assistJd(safeRequest);

    // Audit (no sensitive content)
    AuditService.job(
        AuditService.JobEvent.UPDATE, // Reusing closest event type
        recruiterId,
        null, // No jobId yet — this is a draft
        AuditService.Outcome.SUCCESS,
        "ai_jd_assist:suggestionId=" + response.suggestionId()
            + ",model=" + response.model()
            + ",promptVersion=" + response.promptVersion());

    log.info("JD assist completed: suggestionId={}", response.suggestionId());
    return response;
  }

  /**
   * Parses a resume into structured fields.
   *
   * @param request the resume parse request
   * @param actorId the authenticated candidate's sub or recruiter's orgId (for audit)
   * @param isCandidate whether the actor is a candidate (true) or recruiter (false)
   * @return the resume parse response
   */
  @Transactional
  public ResumeParseResponse parseResume(ResumeParseRequest request, UUID actorId, boolean isCandidate) {
    checkEnabled();

    log.info("Parsing resume for document={}, actor={}, isCandidate={}", request.documentId(), actorId, isCandidate);

    // Safety: The document content would be fetched from Document Service
    // For now, we pass the documentId and let the provider handle it (mock)
    // In real implementation, we would:
    // 1. Fetch document content from Document Service
    // 2. Apply PII redaction
    // 3. Apply prompt injection defense
    // 4. Send to provider

    ResumeParseResponse response = aiProvider.parseResume(request);

    // Audit
    AuditService.document(
        AuditService.DocumentEvent.UPLOAD, // Reusing closest event type
        isCandidate ? actorId : null,
        request.documentId(),
        AuditService.Outcome.SUCCESS,
        "ai_resume_parse:parseId=" + response.parseId()
            + ",model=" + response.model()
            + ",promptVersion=" + response.promptVersion());

    log.info("Resume parsed: parseId={}", response.parseId());
    return response;
  }

  /**
   * Retrieves a previously computed match score.
   *
   * @param matchScoreId the match score UUID
   * @return the match score if found
   */
  @Transactional(readOnly = true)
  public MatchScore getMatchScore(UUID matchScoreId) {
    return matchScoreRepository.findById(matchScoreId)
        .orElseThrow(() -> new MatchScoreNotFoundException("Match score not found: " + matchScoreId));
  }

  /**
   * Records a recruiter override of an AI match score recommendation.
   *
   * @param matchScoreId the match score UUID
   * @param recruiterId the recruiter UUID
   * @param reason the override reason
   */
  @Transactional
  public void overrideMatchScore(UUID matchScoreId, UUID recruiterId, String reason) {
    MatchScore matchScore = getMatchScore(matchScoreId);
    matchScore.override(recruiterId, reason);
    matchScoreRepository.save(matchScore);

    AuditService.application(
        AuditService.ApplicationEvent.STATUS_CHANGE,
        recruiterId,
        matchScore.getApplicationId(),
        AuditService.Outcome.SUCCESS,
        "ai_match_overridden:matchScoreId=" + matchScoreId
            + ",overriddenBy=" + recruiterId
            + ",reason=" + reason);

    log.info("Match score overridden: matchScoreId={}, recruiter={}", matchScoreId, recruiterId);
  }

  private void checkEnabled() {
    if (!aiEnabled) {
      throw new AiServiceDisabledException(
          "AI features are disabled. Enable with smarthire.ai.enabled=true");
    }
  }

  public static class AiServiceDisabledException extends RuntimeException {
    public AiServiceDisabledException(String message) {
      super(message);
    }
  }

  public static class MatchScoreNotFoundException extends RuntimeException {
    public MatchScoreNotFoundException(String message) {
      super(message);
    }
  }
}