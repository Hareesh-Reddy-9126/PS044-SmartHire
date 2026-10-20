package com.smarthire.job.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to update an existing job's fields. Status changes are handled by the dedicated lifecycle
 * endpoints ({@code /open}, {@code /close}, {@code /reopen}).
 */
@Schema(
    name = "JobUpdateRequest",
    description = "Request payload for updating an existing job posting")
public record JobUpdateRequest(
    @Schema(
            name = "companyName",
            description = "Updated company name",
            example = "Tech Corp Updated",
            required = true,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String companyName,
    @Schema(
            name = "role",
            description = "Updated job role/title",
            example = "Principal Software Architect",
            required = true,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String role,
    @Schema(
            name = "location",
            description = "Updated job location",
            example = "Boston, MA",
            required = true,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String location) {}
