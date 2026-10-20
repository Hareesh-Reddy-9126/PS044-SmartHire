package com.smarthire.recruitment.api.dto;

import com.smarthire.recruitment.domain.InterviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request to update interview status. */
@Schema(
    name = "InterviewStatusUpdateRequest",
    description = "Request payload for updating interview status")
public record InterviewStatusUpdateRequest(
    @Schema(
            name = "status",
            description = "New interview status",
            example = "SCHEDULED",
            required = true)
        @NotNull
        InterviewStatus status) {}
