package com.smarthire.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT issuance/verification settings (ADR-0009). TTLs are the approved values: access 15m, refresh
 * 7d (decisions 2 & 3). {@code privateKey} is a PKCS#8 PEM RSA key supplied via the environment
 * (governance §17); blank means an ephemeral dev keypair is generated at startup.
 */
@ConfigurationProperties("smarthire.jwt")
public record JwtProperties(
    String issuer,
    String audience,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String privateKey) {}
