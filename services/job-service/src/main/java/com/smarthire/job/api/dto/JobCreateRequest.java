package com.smarthire.job.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new job. The job is created in DRAFT status; the owner organization is
 * derived from the authenticated recruiter's JWT.
 */
@Schema(name = "JobCreateRequest", description = "Request payload for creating a new job posting")
public record JobCreateRequest(
    @Schema(
            name = "companyName",
            description = "Name of the hiring company",
            example = "Tech Corp",
            required = true,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String companyName,
    @Schema(
            name = "role",
            description = "Job title/role to recruit for",
            example = "Senior Software Engineer",
            required = true,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String role,
    @Schema(
            name = "location",
            description = "Job location",
            example = "New York, NY",
            required = true,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String location) {}
