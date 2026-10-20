package com.smarthire.recruitment.api.dto;

import com.smarthire.recruitment.domain.FinalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request to update final status. */
@Schema(
    name = "FinalStatusUpdateRequest",
    description = "Request payload for updating final recruitment outcome")
public record FinalStatusUpdateRequest(
    @Schema(
            name = "status",
            description = "Final recruitment outcome",
            example = "HIRED",
            required = true)
        @NotNull
        FinalStatus status) {}
