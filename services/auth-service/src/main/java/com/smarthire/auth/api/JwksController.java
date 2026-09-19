package com.smarthire.auth.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public JWKS endpoint. Publishes only the RSA public key ({@code toPublicJWK()} strips all private
 * material) so the API Gateway and any offline verifier can validate RS256 access tokens without a
 * shared secret (ADR-0009, decision 9). Left anonymous by {@link
 * com.smarthire.auth.config.SecurityConfig}.
 */
@RestController
public class JwksController {

  private final RSAKey rsaKey;

  public JwksController(RSAKey rsaKey) {
    this.rsaKey = rsaKey;
  }

  @GetMapping("/oauth2/jwks")
  public Map<String, Object> jwks() {
    return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
  }
}
