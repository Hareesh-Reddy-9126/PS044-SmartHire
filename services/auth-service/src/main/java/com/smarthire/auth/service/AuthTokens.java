package com.smarthire.auth.service;

import java.time.Instant;

/**
 * Service-layer result of a successful authentication: the signed access token, its absolute
 * expiry, and the opaque refresh token. Mapped to the API {@code TokenResponse} by the controller
 * so the service layer stays independent of the api layer (governance §3).
 */
public record AuthTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}
