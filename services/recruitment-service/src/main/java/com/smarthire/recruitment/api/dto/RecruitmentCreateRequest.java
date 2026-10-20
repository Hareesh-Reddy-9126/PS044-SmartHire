package com.smarthire.recruitment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to create a new recruitment record. */
@Schema(
    name = "RecruitmentCreateRequest",
    description = "Request payload for shortlisting an application")
public record RecruitmentCreateRequest(
    @Schema(
            name = "applicationId",
            description = "ID of the application to shortlist",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID applicationId) {}
