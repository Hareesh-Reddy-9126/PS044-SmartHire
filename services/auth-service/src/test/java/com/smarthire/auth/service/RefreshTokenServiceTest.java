package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.auth.config.JwtProperties;
import com.smarthire.auth.domain.RefreshToken;
import com.smarthire.auth.infra.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Refresh-token rotation with reuse-detection (ADR-0009). Each rotation revokes the presented token
 * and issues a fresh one in the same family; presenting an already-revoked token is treated as
 * theft and revokes the entire family. Persistence is mocked so these tests pin the security logic
 * (the hash lookups, the reuse branch, the expiry branch) independently of a database.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final JwtProperties PROPS =
      new JwtProperties("iss", "aud", Duration.ofMinutes(15), Duration.ofDays(7), "");

  @Mock private RefreshTokenRepository repository;
  @Mock private RefreshTokenFamilyRevocationService familyRevocationService;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    service =
        new RefreshTokenService(
            repository, familyRevocationService, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private RefreshToken token(UUID userId, UUID familyId, Instant expiresAt) {
    return new RefreshToken(
        UUID.randomUUID(), userId, familyId, "hash", NOW.minusSeconds(10), expiresAt);
  }

  @Test
  void rotateIssuesANewTokenAndRevokesThePresentedOne() {
    UUID userId = UUID.randomUUID();
    RefreshToken current = token(userId, UUID.randomUUID(), NOW.plus(Duration.ofDays(7)));
    when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(current));
    when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

    RefreshTokenService.RotationResult result = service.rotate("raw-token");

    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.refreshToken()).isNotBlank();
    assertThat(current.isRevoked()).isTrue();
    verify(repository).save(any(RefreshToken.class));
    verify(repository).findByTokenHashForUpdate(anyString());
  }

  @Test
  void rotateDetectsReuseOfARevokedTokenAndRevokesTheWholeFamily() {
    UUID familyId = UUID.randomUUID();
    RefreshToken revoked = token(UUID.randomUUID(), familyId, NOW.plus(Duration.ofDays(7)));
    revoked.revoke(UUID.randomUUID());
    when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> service.rotate("raw-token"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Refresh token reuse detected");

    verify(familyRevocationService).revokeFamily(familyId);
    verify(repository, never()).save(any());
  }

  @Test
  void rotateRejectsAnExpiredToken() {
    RefreshToken expired = token(UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(1));
    when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.rotate("raw-token"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Expired refresh token");

    verify(repository, never()).save(any());
  }

  @Test
  void rotateRejectsAnUnknownToken() {
    when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("raw-token"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Unknown refresh token");
  }

  @Test
  void issuePersistsANewTokenAndReturnsTheRawValue() {
    when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

    String raw = service.issue(UUID.randomUUID());

    assertThat(raw).isNotBlank();
    verify(repository).save(any(RefreshToken.class));
  }

  @Test
  void revokeRevokesTheFamilyOfAKnownToken() {
    UUID familyId = UUID.randomUUID();
    RefreshToken current = token(UUID.randomUUID(), familyId, NOW.plus(Duration.ofDays(7)));
    when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

    service.revoke("raw-token");

    verify(repository).revokeFamily(familyId);
  }
}
