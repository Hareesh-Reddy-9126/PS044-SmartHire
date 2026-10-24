package com.smarthire.ai.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.ai.api.dto.CandidateSummaryRequest;
import com.smarthire.ai.api.dto.CandidateSummaryResponse;
import com.smarthire.ai.api.dto.JdAssistRequest;
import com.smarthire.ai.api.dto.JdAssistResponse;
import com.smarthire.ai.api.dto.MatchScoreRequest;
import com.smarthire.ai.api.dto.MatchScoreResponse;
import com.smarthire.ai.api.dto.ResumeParseRequest;
import com.smarthire.ai.api.dto.ResumeParseResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MockAiProvider}.
 *
 * <p>Verifies deterministic, schema-valid responses that clearly identify as mock.
 */
class MockAiProviderTest {

  private final MockAiProvider provider = new MockAiProvider();

  @Test
  void providerIdentification() {
    assertThat(provider.getProviderId()).isEqualTo("mock");
    assertThat(provider.getModelId()).isEqualTo("mock-v1");
    assertThat(provider.getPromptVersion()).isEqualTo("v1.0");
  }

  @Test
  void computeMatchScoreDeterministic() {
    UUID appId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    MatchScoreRequest request = new MatchScoreRequest(appId, jobId);

    MatchScoreResponse response1 = provider.computeMatchScore(request);
    MatchScoreResponse response2 = provider.computeMatchScore(request);

    // Deterministic: same input = same output
    assertThat(response1).isEqualTo(response2);

    // Schema validation
    assertThat(response1.matchScoreId()).isNotNull();
    assertThat(response1.applicationId()).isEqualTo(appId);
    assertThat(response1.jobId()).isEqualTo(jobId);
    assertThat(response1.score()).isBetween((short) 0, (short) 100);
    assertThat(response1.rationale()).contains("[MOCK]");
    assertThat(response1.model()).isEqualTo("mock-v1");
    assertThat(response1.promptVersion()).isEqualTo("v1.0");
    assertThat(response1.confidence()).isBetween((short) 0, (short) 100);
  }

  @Test
  void generateCandidateSummary() {
    UUID appId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    CandidateSummaryRequest request = new CandidateSummaryRequest(appId);

    CandidateSummaryResponse response = provider.generateCandidateSummary(request);

    assertThat(response.summaryId()).isNotNull();
    assertThat(response.applicationId()).isEqualTo(appId);
    assertThat(response.summary()).contains("[MOCK]");
    assertThat(response.keySkills()).isNotEmpty();
    assertThat(response.experienceYears()).isPositive();
    assertThat(response.education()).isNotBlank();
    assertThat(response.model()).isEqualTo("mock-v1");
    assertThat(response.promptVersion()).isEqualTo("v1.0");
  }

  @Test
  void assistJd() {
    JdAssistRequest request =
        new JdAssistRequest(
            "Senior Software Engineer",
            "Senior",
            "We need a Senior Software Engineer.",
            "professional");

    JdAssistResponse response = provider.assistJd(request);

    assertThat(response.suggestionId()).isNotNull();
    assertThat(response.improvedJd()).contains("[MOCK ENHANCEMENT]");
    assertThat(response.model()).isEqualTo("mock-v1");
    assertThat(response.promptVersion()).isEqualTo("v1.0");
  }

  @Test
  void parseResume() {
    UUID docId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    ResumeParseRequest request = new ResumeParseRequest(docId);

    ResumeParseResponse response = provider.parseResume(request);

    assertThat(response.parseId()).isNotNull();
    assertThat(response.documentId()).isEqualTo(docId);
    assertThat(response.skills()).isNotEmpty();
    assertThat(response.experience()).isNotEmpty();
    assertThat(response.education()).isNotEmpty();
    assertThat(response.certifications()).isNotEmpty();
    assertThat(response.model()).isEqualTo("mock-v1");
    assertThat(response.promptVersion()).isEqualTo("v1.0");
  }

  @Test
  void deterministicOutputs() {
    UUID appId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    MatchScoreRequest request = new MatchScoreRequest(appId, jobId);

    // Call multiple times - should always return same result
    for (int i = 0; i < 10; i++) {
      MatchScoreResponse response = provider.computeMatchScore(request);
      assertThat(response.score()).isEqualTo(provider.computeMatchScore(request).score());
    }
  }
}
