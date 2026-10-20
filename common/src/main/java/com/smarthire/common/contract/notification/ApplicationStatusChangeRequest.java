package com.smarthire.common.contract.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Contract DTO for application status change events sent to Notification Service.
 *
 * <p>Shared between Application Service (sender) and Notification Service (receiver). This is the
 * cross-service contract - no implementation details leak here.
 */
@Schema(
    name = "ApplicationStatusChangeRequest",
    description = "Request payload for application status change notification (internal)")
public record ApplicationStatusChangeRequest(
    @Schema(
            name = "candidateId",
            description = "ID of the candidate",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID candidateId,
    @Schema(
            name = "applicationId",
            description = "ID of the application",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID applicationId,
    @Schema(
            name = "status",
            description = "New application status",
            example = "HIRED",
            required = true)
        @NotNull
        String status) {}
