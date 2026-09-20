package com.smarthire.auth.service;

import java.time.Instant;

/**
 * A freshly issued access token and the metadata callers need without re-parsing it: the {@code
 * jti} (for later revocation) and the absolute {@code expiresAt}.
 */
public record IssuedToken(String value, String jti, Instant expiresAt) {}
