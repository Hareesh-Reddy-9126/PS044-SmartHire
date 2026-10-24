package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to compute a candidate–job match score. */
@Schema(name = "MatchScoreRequest", description = "Request to compute a candidate–job match score")
public record MatchScoreRequest(
    @Schema(
            name = "applicationId",
            description = "Application UUID",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID applicationId,
    @Schema(
            name = "jobId",
            description = "Job UUID",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID jobId) {}
