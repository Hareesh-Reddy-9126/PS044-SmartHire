package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * JWT revocation denylist (decision 4). A revoked access-token {@code jti} is stored in Redis under
 * {@code revoked:jti:<jti>} with a TTL equal to the token's remaining lifetime, so the entry
 * self-expires exactly when the token would anyway — no unbounded growth. The {@code revoked:jti:}
 * prefix is a cross-service contract: the API Gateway's denylist filter reads the same key, so it
 * is asserted as a literal here rather than via the (package-private) constant.
 */
@ExtendWith(MockitoExtension.class)
class RevocationServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final String PREFIX = "revoked:jti:";

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOps;

  private RevocationService service;

  @BeforeEach
  void setUp() {
    service = new RevocationService(redis, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void revokeStoresTheJtiWithATtlUntilItsExpiry() {
    when(redis.opsForValue()).thenReturn(valueOps);

    service.revoke("jti-123", NOW.plusSeconds(600));

    verify(valueOps).set(PREFIX + "jti-123", "1", Duration.ofSeconds(600));
  }

  @Test
  void revokeIsANoOpForAnAlreadyExpiredToken() {
    service.revoke("jti-123", NOW.minusSeconds(1));

    verifyNoInteractions(valueOps);
  }

  @Test
  void revokeIsANoOpWhenJtiOrExpiryIsNull() {
    service.revoke(null, NOW.plusSeconds(600));
    service.revoke("jti-123", null);

    verifyNoInteractions(valueOps);
  }

  @Test
  void isRevokedReflectsKeyPresence() {
    when(redis.hasKey(PREFIX + "live")).thenReturn(true);
    when(redis.hasKey(PREFIX + "gone")).thenReturn(false);

    assertThat(service.isRevoked("live")).isTrue();
    assertThat(service.isRevoked("gone")).isFalse();
  }

  @Test
  void isRevokedIsFalseForANullJtiWithoutTouchingRedis() {
    assertThat(service.isRevoked(null)).isFalse();

    verifyNoInteractions(redis);
  }
}
