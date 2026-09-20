package com.smarthire.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Edge JWT-verification settings. The gateway verifies RS256 access tokens offline against the
 * auth-service JWKS (ADR-0009, decision 9): {@code jwkSetUri} is a direct HTTP URL — deliberately
 * NOT an {@code lb://} address, because the reactive decoder's key fetch does not pass through the
 * gateway's load-balancer filter — while {@code issuer} and {@code audience} pin the token's {@code
 * iss}/{@code aud} claims to the same values auth-service signs, so a token minted for another
 * audience is rejected at the edge too (decision 4, defense-in-depth mirrored here).
 */
@ConfigurationProperties("smarthire.gateway.jwt")
public record GatewaySecurityProperties(String jwkSetUri, String issuer, String audience) {}
