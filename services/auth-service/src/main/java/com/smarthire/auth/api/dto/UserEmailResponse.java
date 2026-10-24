package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Internal user-directory answer: the verified email for one user id.
 *
 * <p>Used by notification-service to address candidate notifications. It is deliberately not a full
 * profile: only the field another service needs is exposed.
 */
@Schema(name = "UserEmailResponse", description = "Email address for a single user id")
public record UserEmailResponse(
    @Schema(description = "User id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID userId,
    @Schema(description = "Verified email address", example = "candidate@example.com")
        String email) {}
