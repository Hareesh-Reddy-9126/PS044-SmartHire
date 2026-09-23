package com.smarthire.job.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-server JWT-verification settings. Job-service verifies the auth-service RS256 access
 * tokens offline against the published JWKS (ADR-0009, decision 9) rather than trusting the
 * gateway: {@code jwkSetUri} is a direct HTTP URL — deliberately NOT an {@code lb://} address,
 * because the decoder's key fetch does not pass through the gateway's load-balancer filter — while
 * {@code issuer} and {@code audience} pin the token's {@code iss}/{@code aud} claims to the same
 * values auth-service signs. Identical to the gateway's pinning (decision 4, defense-in-depth): a
 * token minted for another audience is rejected at both hops.
 */
@ConfigurationProperties("smarthire.job.jwt")
public record JobSecurityProperties(String jwkSetUri, String issuer, String audience) {}
