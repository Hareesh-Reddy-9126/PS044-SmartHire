package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to generate a candidate summary.
 */
@Schema(name = "CandidateSummaryRequest", description = "Request to generate a structured candidate summary")
public record CandidateSummaryRequest(
    @Schema(name = "applicationId", description = "Application UUID", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    @NotNull UUID applicationId) {}