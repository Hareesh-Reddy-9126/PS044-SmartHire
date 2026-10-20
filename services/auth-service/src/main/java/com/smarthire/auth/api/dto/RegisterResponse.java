package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response to a successful self-registration: the id of the newly created CANDIDATE account. */
@Schema(name = "RegisterResponse", description = "Response after successful user registration")
public record RegisterResponse(
    @Schema(
            name = "userId",
            description = "ID of the newly created user account",
            example = "550e8400-e29b-41d4-a716-446655440000")
        String userId) {}
