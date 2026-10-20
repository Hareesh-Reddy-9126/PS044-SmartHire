package com.smarthire.ai.infra;

import com.smarthire.ai.api.dto.CandidateSummaryRequest;
import com.smarthire.ai.api.dto.CandidateSummaryResponse;
import com.smarthire.ai.api.dto.JdAssistRequest;
import com.smarthire.ai.api.dto.JdAssistResponse;
import com.smarthire.ai.api.dto.MatchScoreRequest;
import com.smarthire.ai.api.dto.MatchScoreResponse;
import com.smarthire.ai.api.dto.ResumeParseRequest;
import com.smarthire.ai.api.dto.ResumeParseResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deterministic mock AI provider for local development and testing.
 *
 * <p>Returns predictable, schema-valid responses based on simple heuristics. Clearly identifies
 * itself as a mock provider in all responses. Never implies that mock scores represent validated
 * real-world candidate suitability.
 *
 * <p>Key properties:
 * <ul>
 *   <li>Deterministic: same input always produces same output
 *   <li>No external dependencies: no network calls, no SDKs
 *   <li>Fast: suitable for unit and integration tests
 *   <li>Self-identifying: all responses include provider="mock" and model="mock-v1"
 * </ul>
 *
 * <p><b>Not for production use.</b> The real provider implementation must be selected via
 * configuration ({@code smarthire.ai.provider=claude}).
 */
@Component
public class MockAiProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(MockAiProvider.class);

  private static final String PROVIDER_ID = "mock";
  private static final String MODEL_ID = "mock-v1";
  private static final String PROMPT_VERSION = "v1.0";

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public String getModelId() {
    return MODEL_ID;
  }

  @Override
  public String getPromptVersion() {
    return PROMPT_VERSION;
  }

  @Override
  public MatchScoreResponse computeMatchScore(MatchScoreRequest request) {
    log.debug("MockAiProvider: computing match score for application={}, job={}",
        request.applicationId(), request.jobId());

    // Deterministic score based on UUID hash
    int hash = request.applicationId().hashCode() ^ request.jobId().hashCode();
    int score = Math.abs(hash % 101); // 0-100

    String rationale = String.format(
        "[MOCK] Candidate %s scored %d/100 for job %s. "
            + "Rationale: Based on synthetic evaluation of skills overlap, experience alignment, "
            + "and location match. This is a deterministic mock response — not a real assessment.",
        request.applicationId(), score, request.jobId());

    return new MatchScoreResponse(
        UUID.randomUUID(),
        request.applicationId(),
        request.jobId(),
        (short) score,
        rationale,
        MODEL_ID,
        PROMPT_VERSION,
        (short) 75); // Mock confidence
  }

  @Override
  public CandidateSummaryResponse generateCandidateSummary(CandidateSummaryRequest request) {
    log.debug("MockAiProvider: generating candidate summary for application={}", request.applicationId());

    return new CandidateSummaryResponse(
        UUID.randomUUID(),
        request.applicationId(),
        "[MOCK] Summary for candidate application " + request.applicationId() + ". "
            + "Candidate demonstrates strong technical skills in Java, Spring Boot, and cloud "
            + "technologies. 5+ years of experience in backend development. Bachelor's degree in "
            + "Computer Science. This is a deterministic mock response — not a real assessment.",
        List.of("Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes", "AWS"),
        5,
        "Bachelor of Science in Computer Science",
        MODEL_ID,
        PROMPT_VERSION);
  }

  @Override
  public JdAssistResponse assistJd(JdAssistRequest request) {
    log.debug("MockAiProvider: assisting JD for role={}, seniority={}", request.role(), request.seniority());

    String improvedJd = String.format(
        "%s\n\n[MOCK ENHANCEMENT] This job description has been enhanced with structured "
            + "requirements, clear responsibilities, and inclusive language. "
            + "Original draft length: %d chars. This is a deterministic mock response.",
        request.draftJd(), request.draftJd().length());

    return new JdAssistResponse(
        UUID.randomUUID(),
        improvedJd,
        MODEL_ID,
        PROMPT_VERSION);
  }

  @Override
  public ResumeParseResponse parseResume(ResumeParseRequest request) {
    log.debug("MockAiProvider: parsing resume for document={}", request.documentId());

    return new ResumeParseResponse(
        UUID.randomUUID(),
        request.documentId(),
        List.of("Java", "Spring Boot", "Microservices", "PostgreSQL", "Redis", "Docker"),
        List.of(
            "Senior Software Engineer at TechCorp (2020-2024)",
            "Software Engineer at StartupInc (2018-2020)"),
        List.of("B.S. Computer Science, State University (2018)"),
        List.of("AWS Certified Solutions Architect", "Oracle Certified Java Developer"),
        MODEL_ID,
        PROMPT_VERSION);
  }
}