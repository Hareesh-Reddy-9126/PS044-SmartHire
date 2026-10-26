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
    String privateKey) {

  public JwtProperties {
    requireResolved("issuer", issuer);
    requireResolved("audience", audience);
  }

  /**
   * Fails startup when a placeholder could not be resolved. Spring Boot's binder leaves an
   * unresolvable {@code ${...}} as literal text, which would otherwise sign tokens that every
   * downstream service rejects — a failure that only appears once traffic flows.
   */
  private static void requireResolved(String name, String value) {
    if (value == null || value.isBlank() || value.contains("${")) {
      throw new IllegalStateException(
          "smarthire.jwt."
              + name
              + " is unresolved (a ${...} placeholder was not replaced); set AUTH_JWT_ISSUER and"
              + " AUTH_JWT_AUDIENCE in the deployment environment");
    }
  }
}
