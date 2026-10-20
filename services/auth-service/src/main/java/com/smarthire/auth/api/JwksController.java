package com.smarthire.auth.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "JWKS", description = "JSON Web Key Set endpoint for JWT public key verification")
public class JwksController {

  private final RSAKey rsaKey;

  public JwksController(RSAKey rsaKey) {
    this.rsaKey = rsaKey;
  }

  @Operation(
      summary = "Get JSON Web Key Set",
      description =
          "Returns the RSA public key in JWKS format for validating RS256 JWT access tokens. Public endpoint, no authentication required.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "JWKS retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema =
                        @Schema(
                            type = "object",
                            description = "JWKS object containing the RSA public key",
                            example =
                                "{\"keys\":[{\"kty\":\"RSA\",\"e\":\"AQAB\",\"kid\":\"...\",\"n\":\"...\",\"alg\":\"RS256\"}]}")))
      })
  @SecurityRequirement(name = "")
  @GetMapping("/oauth2/jwks")
  public Map<String, Object> jwks() {
    return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
  }
}
