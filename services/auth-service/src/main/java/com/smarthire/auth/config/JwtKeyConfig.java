package com.smarthire.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.smarthire.auth.service.RevocationService;
import com.smarthire.auth.service.TokenRevocationValidator;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 signing key + JWKS + local verification (ADR-0009; decision 9 — direct Nimbus, not Spring
 * Authorization Server). One RSA key is the single secret: the public key is derived from it, so
 * the {@link JwtEncoder} (sign), the {@link JwtDecoder} (verify own tokens, defense-in-depth per
 * decision 4) and the JWKS endpoint all share it. The {@code kid} is the key's RFC 7638 thumbprint,
 * so the token header {@code kid} matches the published JWK.
 */
@Configuration
public class JwtKeyConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);
  private static final int EPHEMERAL_KEY_SIZE = 2048;

  private final JwtProperties props;
  private final Environment environment;

  public JwtKeyConfig(JwtProperties props, Environment environment) {
    this.props = props;
    this.environment = environment;
  }

  @Bean
  public RSAKey rsaKey() throws Exception {
    RSAPrivateKey privateKey;
    if (props.privateKey() == null || props.privateKey().isBlank()) {
      if (!environment.acceptsProfiles(Profiles.of("local"))) {
        throw new IllegalStateException(
            "AUTH_JWT_PRIVATE_KEY must be configured outside the local profile");
      }
      log.warn(
          "smarthire.jwt.private-key is not set — generating an EPHEMERAL RSA keypair. "
              + "Issued tokens will not survive a restart. Set AUTH_JWT_PRIVATE_KEY for stable keys.");
      KeyPair pair = generateEphemeralKeyPair();
      privateKey = (RSAPrivateKey) pair.getPrivate();
    } else {
      privateKey = parsePrivateKey(props.privateKey());
    }
    RSAPublicKey publicKey = derivePublicKey(privateKey);
    RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
    // Stable kid tied to the key material, so a rotated key yields a new kid automatically.
    return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  @Bean
  public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  public JwtDecoder jwtDecoder(RSAKey rsaKey, RevocationService revocationService)
      throws Exception {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    // Standard checks (exp/nbf + issuer) + audience + jti-revocation denylist (defense-in-depth).
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<java.util.List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(props.audience()));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(props.issuer()),
            audience,
            new TokenRevocationValidator(revocationService)));
    return decoder;
  }

  private static KeyPair generateEphemeralKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(EPHEMERAL_KEY_SIZE);
    return generator.generateKeyPair();
  }

  private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  /**
   * Derives the RSA public key from the private key's CRT parameters. Standard PKCS#8 RSA keys
   * carry these parameters; a non-CRT private key is not supported (and not produced by the usual
   * tooling).
   */
  private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
    if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
      throw new IllegalStateException(
          "RSA private key lacks CRT parameters; cannot derive the public key. "
              + "Supply a standard PKCS#8 RSA key.");
    }
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPublicKey)
        factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
  }
}
