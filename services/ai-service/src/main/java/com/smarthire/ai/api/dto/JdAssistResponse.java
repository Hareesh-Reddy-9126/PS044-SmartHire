package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Response containing an AI-assisted job description suggestion.
 */
@Schema(name = "JdAssistResponse", description = "AI-assisted job description suggestion (advisory only)")
public record JdAssistResponse(
    @Schema(name = "suggestionId", description = "Unique identifier for this suggestion", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    UUID suggestionId,

    @Schema(name = "improvedJd", description = "AI-improved job description", example = "Enhanced job description with structured requirements...", required = true)
    String improvedJd,

    @Schema(name = "model", description = "AI model used", example = "claude-3-haiku-20240307", required = true)
    String model,

    @Schema(name = "promptVersion", description = "Prompt version used", example = "v1.0", required = true)
    String promptVersion) {}