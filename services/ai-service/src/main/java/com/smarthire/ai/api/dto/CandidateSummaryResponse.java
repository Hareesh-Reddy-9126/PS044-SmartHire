package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Response containing a structured candidate summary. */
@Schema(
    name = "CandidateSummaryResponse",
    description = "AI-generated candidate summary (advisory only)")
public record CandidateSummaryResponse(
    @Schema(
            name = "summaryId",
            description = "Unique identifier for this summary",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID summaryId,
    @Schema(
            name = "applicationId",
            description = "Application UUID",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID applicationId,
    @Schema(
            name = "summary",
            description = "AI-generated candidate summary",
            example = "Candidate demonstrates strong technical skills...",
            required = true)
        String summary,
    @Schema(
            name = "keySkills",
            description = "Key skills extracted from the candidate's profile",
            required = true)
        List<String> keySkills,
    @Schema(
            name = "experienceYears",
            description = "Estimated years of experience",
            example = "5",
            minimum = "0")
        Integer experienceYears,
    @Schema(
            name = "education",
            description = "Highest education level",
            example = "Bachelor of Science in Computer Science")
        String education,
    @Schema(
            name = "model",
            description = "AI model used",
            example = "claude-3-haiku-20240307",
            required = true)
        String model,
    @Schema(
            name = "promptVersion",
            description = "Prompt version used",
            example = "v1.0",
            required = true)
        String promptVersion) {

  public CandidateSummaryResponse {
    keySkills = keySkills != null ? List.copyOf(keySkills) : List.of();
  }
}
