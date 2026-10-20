package com.smarthire.application.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request to create a new application. The candidate is derived from the authenticated JWT. */
@Schema(
    name = "ApplicationCreateRequest",
    description = "Request payload for creating a new job application")
public record ApplicationCreateRequest(
    @Schema(
            name = "jobId",
            description = "ID of the job to apply for",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID jobId,
    @Schema(
            name = "resumeLink",
            description = "Link to the candidate's resume",
            example = "https://example.com/resume.pdf",
            required = true)
        @NotBlank
        @Size(max = 2048)
        String resumeLink) {}
