package com.smarthire.auth.api.dto;

import java.time.Instant;

/**
 * Successful-authentication response for login/refresh. {@code tokenType} is always {@code Bearer};
 * the access token is an RS256 JWT valid for 15 minutes ({@code accessTokenExpiresAt} is its
 * absolute expiry). The rotated opaque refresh token is returned only in an HttpOnly, Secure,
 * SameSite cookie.
 */
public record TokenResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt) {}
