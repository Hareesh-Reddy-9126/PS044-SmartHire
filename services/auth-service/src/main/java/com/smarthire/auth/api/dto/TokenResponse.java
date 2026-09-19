package com.smarthire.auth.api.dto;

import java.time.Instant;

/**
 * Successful-authentication response for login/refresh. {@code tokenType} is always {@code Bearer};
 * the access token is an RS256 JWT valid for 15 minutes ({@code accessTokenExpiresAt} is its
 * absolute expiry); {@code refreshToken} is the rotated opaque token (7-day lifetime).
 */
public record TokenResponse(
    String tokenType, String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}
