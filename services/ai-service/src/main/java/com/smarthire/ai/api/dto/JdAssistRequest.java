package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to assist in drafting or improving a job description.
 */
@Schema(name = "JdAssistRequest", description = "Request for AI-assisted job description drafting")
public record JdAssistRequest(
    @Schema(name = "role", description = "Job role/title", example = "Senior Software Engineer", required = true)
    @NotBlank String role,

    @Schema(name = "seniority", description = "Seniority level", example = "Senior", required = true)
    @NotBlank String seniority,

    @Schema(name = "draftJd", description = "Existing draft job description (optional)", example = "We are looking for a Senior Software Engineer...")
    String draftJd,

    @Schema(name = "tone", description = "Desired tone", example = "professional", allowableValues = {"professional", "friendly", "formal", "casual"})
    String tone) {}