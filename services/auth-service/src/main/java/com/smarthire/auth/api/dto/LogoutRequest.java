package com.smarthire.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Logout request: the refresh token to revoke. The access token comes from the Authorization
 * header.
 */
public record LogoutRequest(@NotBlank String refreshToken) {}
