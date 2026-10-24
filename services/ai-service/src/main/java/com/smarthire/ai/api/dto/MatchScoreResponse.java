package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Response containing a candidate–job match score with rationale. */
@Schema(
    name = "MatchScoreResponse",
    description = "AI-generated match score with rationale (advisory only)")
public record MatchScoreResponse(
    @Schema(
            name = "matchScoreId",
            description = "Unique identifier for this match score",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID matchScoreId,
    @Schema(
            name = "applicationId",
            description = "Application UUID",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID applicationId,
    @Schema(
            name = "jobId",
            description = "Job UUID",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID jobId,
    @Schema(
            name = "score",
            description = "Match score (0-100)",
            example = "85",
            minimum = "0",
            maximum = "100",
            required = true)
        Short score,
    @Schema(
            name = "rationale",
            description = "AI-generated rationale for the score",
            example = "Candidate has 5 years of Java experience matching the job requirements.",
            required = true)
        String rationale,
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
        String promptVersion,
    @Schema(
            name = "confidence",
            description = "Confidence score (0-100)",
            example = "80",
            minimum = "0",
            maximum = "100")
        Short confidence) {}
