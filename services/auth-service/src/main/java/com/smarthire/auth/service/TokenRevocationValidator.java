package com.smarthire.auth.service;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects a JWT whose {@code jti} is on the Redis revocation denylist (ADR-0011). Plugged into
 * auth-service's own {@code JwtDecoder} as defense-in-depth behind the gateway's primary
 * enforcement (decision 4).
 */
public class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error REVOKED =
      new OAuth2Error("invalid_token", "The token has been revoked", null);

  private final RevocationService revocationService;

  public TokenRevocationValidator(RevocationService revocationService) {
    this.revocationService = revocationService;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    if (revocationService.isRevoked(token.getId())) {
      return OAuth2TokenValidatorResult.failure(REVOKED);
    }
    return OAuth2TokenValidatorResult.success();
  }
}
