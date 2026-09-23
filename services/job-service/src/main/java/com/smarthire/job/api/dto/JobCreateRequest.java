package com.smarthire.job.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new job. The job is created in DRAFT status; the owner organization is
 * derived from the authenticated recruiter's JWT.
 */
public record JobCreateRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Size(max = 255) String role,
    @NotBlank @Size(max = 255) String location) {}
