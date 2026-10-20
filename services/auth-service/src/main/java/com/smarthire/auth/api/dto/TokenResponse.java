package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Successful-authentication response for login/refresh. {@code tokenType} is always {@code Bearer};
 * the access token is an RS256 JWT valid for 15 minutes ({@code accessTokenExpiresAt} is its
 * absolute expiry). The rotated opaque refresh token is returned only in an HttpOnly, Secure,
 * SameSite cookie.
 */
@Schema(
    name = "TokenResponse",
    description = "Access token response after successful authentication")
public record TokenResponse(
    @Schema(name = "tokenType", description = "Token type, always 'Bearer'", example = "Bearer")
        String tokenType,
    @Schema(
            name = "accessToken",
            description = "RS256 JWT access token",
            example = "eyJhbGciOiJSUzI1NiIs...")
        String accessToken,
    @Schema(
            name = "accessTokenExpiresAt",
            description = "Absolute expiry timestamp of the access token",
            example = "2024-01-15T10:30:00Z")
        Instant accessTokenExpiresAt) {}
