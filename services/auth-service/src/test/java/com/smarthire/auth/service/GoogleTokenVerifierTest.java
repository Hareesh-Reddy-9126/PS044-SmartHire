package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smarthire.auth.config.GoogleProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Pins the trust checks on the Google ID token. The decoder is mocked, so these tests exercise the
 * claims validation that runs after signature/expiry verification without calling Google.
 */
@ExtendWith(MockitoExtension.class)
class GoogleTokenVerifierTest {

  private static final String CLIENT_ID = "1234567890-smarthire.apps.googleusercontent.com";
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private JwtDecoder decoder;

  private GoogleTokenVerifier verifier;

  @BeforeEach
  void setUp() {
    verifier = new GoogleTokenVerifier(new GoogleProperties(CLIENT_ID), decoder);
  }

  private static Jwt token(
      String issuer, String audience, Object emailVerified, String email, String subject) {
    Jwt.Builder builder =
        Jwt.withTokenValue("id-token")
            .header("alg", "RS256")
            .subject(subject)
            .claim("iss", issuer)
            .claim("aud", List.of(audience))
            .claim("email_verified", emailVerified)
            .issuedAt(NOW)
            .expiresAt(NOW.plusSeconds(3600));
    if (email != null) {
      builder.claim("email", email);
    }
    return builder.build();
  }

  @Test
  void returnsTheNormalizedEmailFromAValidToken() {
    when(decoder.decode("id-token"))
        .thenReturn(
            token(
                "https://accounts.google.com",
                CLIENT_ID,
                true,
                "  Ada@Example.COM ",
                "google-subject"));

    assertThat(verifier.verifiedEmail("id-token")).isEqualTo("ada@example.com");
  }

  @Test
  void acceptsTheLegacyIssuerWithoutTheHttpsScheme() {
    when(decoder.decode("id-token"))
        .thenReturn(token("accounts.google.com", CLIENT_ID, true, "ada@example.com", "sub"));

    assertThat(verifier.verifiedEmail("id-token")).isEqualTo("ada@example.com");
  }

  @Test
  void acceptsAStringEncodedVerifiedFlag() {
    when(decoder.decode("id-token"))
        .thenReturn(
            token("https://accounts.google.com", CLIENT_ID, "true", "ada@example.com", "sub"));

    assertThat(verifier.verifiedEmail("id-token")).isEqualTo("ada@example.com");
  }

  @Test
  void rejectsATokenIssuedForAnotherClient() {
    when(decoder.decode("id-token"))
        .thenReturn(
            token(
                "https://accounts.google.com",
                "someone-else.apps.googleusercontent.com",
                true,
                "ada@example.com",
                "sub"));

    assertThatThrownBy(() -> verifier.verifiedEmail("id-token"))
        .isInstanceOf(InvalidGoogleCredentialException.class);
  }

  @Test
  void rejectsAnUnverifiedEmail() {
    when(decoder.decode("id-token"))
        .thenReturn(
            token("https://accounts.google.com", CLIENT_ID, false, "ada@example.com", "sub"));

    assertThatThrownBy(() -> verifier.verifiedEmail("id-token"))
        .isInstanceOf(InvalidGoogleCredentialException.class);
  }

  @Test
  void rejectsAnUnexpectedIssuer() {
    when(decoder.decode("id-token"))
        .thenReturn(token("https://evil.example", CLIENT_ID, true, "ada@example.com", "sub"));

    assertThatThrownBy(() -> verifier.verifiedEmail("id-token"))
        .isInstanceOf(InvalidGoogleCredentialException.class);
  }

  @Test
  void rejectsATokenWithoutAnEmail() {
    when(decoder.decode("id-token"))
        .thenReturn(token("https://accounts.google.com", CLIENT_ID, true, null, "sub"));

    assertThatThrownBy(() -> verifier.verifiedEmail("id-token"))
        .isInstanceOf(InvalidGoogleCredentialException.class);
  }

  @Test
  void translatesADecoderFailureWithoutLeakingTheReason() {
    when(decoder.decode("id-token")).thenThrow(new JwtException("bad signature"));

    assertThatThrownBy(() -> verifier.verifiedEmail("id-token"))
        .isInstanceOf(InvalidGoogleCredentialException.class)
        .hasMessage("Google credential rejected");
  }

  @Test
  void refusesToVerifyWhenNoClientIdIsConfigured() {
    GoogleTokenVerifier unconfigured = new GoogleTokenVerifier(new GoogleProperties("  "), decoder);

    assertThatThrownBy(() -> unconfigured.verifiedEmail("id-token"))
        .isInstanceOf(GoogleSignInUnavailableException.class);
    verifyNoInteractions(decoder);
  }

  /**
   * Guards the production wiring: the class has a second constructor for tests, so Spring needs an
   * unambiguous injection point. Without one the whole service fails to start.
   */
  @Test
  void canBeCreatedAsASpringBean() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(GoogleProperties.class, () -> new GoogleProperties(CLIENT_ID));
      context.register(GoogleTokenVerifier.class);
      context.refresh();

      assertThat(context.getBean(GoogleTokenVerifier.class)).isNotNull();
    }
  }
}
