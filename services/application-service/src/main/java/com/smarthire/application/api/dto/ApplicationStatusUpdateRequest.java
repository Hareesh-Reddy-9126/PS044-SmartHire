package com.smarthire.application.api.dto;

import com.smarthire.application.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request to update an application's status (recruiter-driven). */
@Schema(
    name = "ApplicationStatusUpdateRequest",
    description = "Request payload for updating an application status")
public record ApplicationStatusUpdateRequest(
    @Schema(
            name = "status",
            description = "New application status",
            example = "HIRED",
            required = true)
        @NotNull
        ApplicationStatus status) {}
