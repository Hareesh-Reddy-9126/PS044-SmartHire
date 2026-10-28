package com.smarthire.auth.service;

import com.smarthire.auth.config.GoogleProperties;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies the Google ID token produced by Google Identity Services.
 *
 * <p>The browser receives a signed JWT directly from Google and posts it to {@code
 * /api/v1/auth/google}; there is no authorization-code exchange, so no client secret is stored on
 * the server. Verification uses Google's published JWKS and checks signature, expiry, issuer,
 * audience and the {@code email_verified} claim. Only the verified email is returned — the token
 * itself is never persisted or logged.
 */
@Component
public class GoogleTokenVerifier {

  static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

  /** Google has historically emitted both forms; both identify the same issuer. */
  private static final Set<String> VALID_ISSUERS =
      Set.of("https://accounts.google.com", "accounts.google.com");

  private final GoogleProperties properties;
  private final JwtDecoder decoder;

  public GoogleTokenVerifier(GoogleProperties properties) {
    this(properties, defaultDecoder());
  }

  GoogleTokenVerifier(GoogleProperties properties, JwtDecoder decoder) {
    this.properties = properties;
    this.decoder = decoder;
  }

  private static JwtDecoder defaultDecoder() {
    return NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
  }

  /**
   * Returns the normalized email address proven by the token.
   *
   * @throws GoogleSignInUnavailableException when no client id is configured
   * @throws InvalidGoogleCredentialException when the token is invalid or the email is unverified
   */
  public String verifiedEmail(String credential) {
    if (!properties.isConfigured()) {
      throw new GoogleSignInUnavailableException();
    }

    Jwt jwt;
    try {
      jwt = decoder.decode(credential);
    } catch (JwtException | IllegalArgumentException ex) {
      throw new InvalidGoogleCredentialException("Google credential rejected", ex);
    }

    String issuer = jwt.getClaimAsString("iss");
    if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
      throw new InvalidGoogleCredentialException("Google credential has an unexpected issuer");
    }

    String clientId = properties.clientId().trim();
    if (jwt.getAudience() == null || !jwt.getAudience().contains(clientId)) {
      throw new InvalidGoogleCredentialException("Google credential is for another client");
    }

    if (!isEmailVerified(jwt.getClaim("email_verified"))) {
      throw new InvalidGoogleCredentialException("Google account email is not verified");
    }

    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank() || email.length() > 255) {
      throw new InvalidGoogleCredentialException("Google credential has no usable email");
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  /** Google sends a boolean, but some older tokens serialize it as the string "true". */
  private static boolean isEmailVerified(Object claim) {
    if (claim instanceof Boolean value) {
      return value;
    }
    return claim instanceof String value && "true".equalsIgnoreCase(value.trim());
  }
}
