package com.smarthire.ai.infra;

import com.smarthire.ai.api.dto.CandidateSummaryRequest;
import com.smarthire.ai.api.dto.CandidateSummaryResponse;
import com.smarthire.ai.api.dto.JdAssistRequest;
import com.smarthire.ai.api.dto.JdAssistResponse;
import com.smarthire.ai.api.dto.MatchScoreRequest;
import com.smarthire.ai.api.dto.MatchScoreResponse;
import com.smarthire.ai.api.dto.ResumeParseRequest;
import com.smarthire.ai.api.dto.ResumeParseResponse;

/**
 * Provider-neutral abstraction for AI model integration.
 *
 * <p>Implementations must be stateless, thread-safe, and return schema-valid responses. The
 * interface isolates the AI service from provider-specific SDKs, enabling local development with a
 * deterministic mock and production use with a real provider (e.g., Claude, OpenAI) without
 * changing business logic.
 *
 * <p>All methods must enforce output validation and fail fast on schema violations. PII redaction
 * and prompt-injection defenses are applied at the service layer before calling the provider.
 */
public interface AiProvider {

  /** Returns the provider identifier (e.g., "mock", "claude", "openai"). */
  String getProviderId();

  /** Returns the model identifier used by this provider (e.g., "claude-3-haiku-20240307"). */
  String getModelId();

  /** Returns the prompt version used by this provider. */
  String getPromptVersion();

  /**
   * Computes a candidate–job match score with rationale.
   *
   * @param request the match score request
   * @return the match score response
   * @throws AiProviderException if the provider is unavailable or returns invalid output
   */
  MatchScoreResponse computeMatchScore(MatchScoreRequest request) throws AiProviderException;

  /**
   * Generates a structured candidate summary.
   *
   * @param request the candidate summary request
   * @return the candidate summary response
   * @throws AiProviderException if the provider is unavailable or returns invalid output
   */
  CandidateSummaryResponse generateCandidateSummary(CandidateSummaryRequest request)
      throws AiProviderException;

  /**
   * Assists in drafting or improving a job description.
   *
   * @param request the JD assist request
   * @return the JD assist response
   * @throws AiProviderException if the provider is unavailable or returns invalid output
   */
  JdAssistResponse assistJd(JdAssistRequest request) throws AiProviderException;

  /**
   * Parses a resume into structured fields.
   *
   * @param request the resume parse request
   * @return the resume parse response
   * @throws AiProviderException if the provider is unavailable or returns invalid output
   */
  ResumeParseResponse parseResume(ResumeParseRequest request) throws AiProviderException;

  /** Exception thrown when the AI provider fails to produce a valid response. */
  class AiProviderException extends RuntimeException {
    public AiProviderException(String message) {
      super(message);
    }

    public AiProviderException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
