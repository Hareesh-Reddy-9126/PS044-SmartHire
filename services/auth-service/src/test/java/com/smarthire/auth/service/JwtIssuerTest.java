package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.smarthire.auth.config.JwtKeyConfig;
import com.smarthire.auth.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * RS256 sign→verify roundtrip for {@link JwtIssuer}, wired through the real Nimbus encoder/decoder
 * from {@link JwtKeyConfig} (decision 9 — direct Nimbus, not Spring Authorization Server). Proves
 * the token this service issues actually verifies offline against its own JWK, carries the ADR-0009
 * claims, honours the approved 15-minute access TTL (decision 2), and is signed under a {@code kid}
 * equal to the RFC 7638 JWK thumbprint (so JWKS consumers can select the key).
 */
class JwtIssuerTest {

  private static final JwtProperties PROPS =
      new JwtProperties(
          "https://smarthire.local/auth",
          "smarthire",
          Duration.ofMinutes(15),
          Duration.ofDays(7),
          ""); // blank private key → JwtKeyConfig generates an ephemeral RSA pair.

  private record Wiring(JwtIssuer issuer, JwtDecoder decoder, RSAKey rsaKey) {}

  /** Builds the full issuer→decoder chain over a fresh ephemeral key and a fixed clock. */
  private static Wiring wire() throws Exception {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("local");
    JwtKeyConfig keyConfig = new JwtKeyConfig(PROPS, environment);
    RSAKey rsaKey = keyConfig.rsaKey();
    JWKSource<SecurityContext> jwkSource = keyConfig.jwkSource(rsaKey);
    JwtEncoder encoder = keyConfig.jwtEncoder(jwkSource);

    RevocationService revocationService = mock(RevocationService.class);
    when(revocationService.isRevoked(anyString())).thenReturn(false);
    JwtDecoder decoder = keyConfig.jwtDecoder(rsaKey, revocationService);

    Clock clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
    return new Wiring(new JwtIssuer(encoder, PROPS, clock), decoder, rsaKey);
  }

  @Test
  void rejectsAnUnconfiguredKeyOutsideTheLocalProfile() {
    assertThatThrownBy(() -> new JwtKeyConfig(PROPS, new MockEnvironment()).rsaKey())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("AUTH_JWT_PRIVATE_KEY must be configured outside the local profile");
  }

  @Test
  void issuesAnRs256TokenThatVerifiesWithTheExpectedClaims() throws Exception {
    Wiring w = wire();
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();

    IssuedToken issued = w.issuer().issue(userId, List.of("ADMIN", "RECRUITER"), orgId);
    Jwt jwt = w.decoder().decode(issued.value());

    assertThat(jwt.getSubject()).isEqualTo(userId.toString());
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "RECRUITER");
    assertThat(jwt.getClaimAsString("orgId")).isEqualTo(orgId.toString());
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("https://smarthire.local/auth");
    assertThat(jwt.getAudience()).containsExactly("smarthire");
    assertThat(jwt.getId()).isEqualTo(issued.jti());
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void signsUnderAKidEqualToTheRfc7638Thumbprint() throws Exception {
    Wiring w = wire();

    IssuedToken issued = w.issuer().issue(UUID.randomUUID(), List.of("CANDIDATE"), null);
    Jwt jwt = w.decoder().decode(issued.value());

    assertThat(jwt.getHeaders().get("kid")).isEqualTo(w.rsaKey().getKeyID());
    assertThat(w.rsaKey().getKeyID()).isEqualTo(w.rsaKey().computeThumbprint().toString());
  }

  @Test
  void omitsTheOrgIdClaimForUsersWithoutAnOrganization() throws Exception {
    Wiring w = wire();

    IssuedToken issued = w.issuer().issue(UUID.randomUUID(), List.of("CANDIDATE"), null);
    Jwt jwt = w.decoder().decode(issued.value());

    assertThat(jwt.getClaims()).doesNotContainKey("orgId");
  }
}
