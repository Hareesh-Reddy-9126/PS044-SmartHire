package com.smarthire.auth.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * JWT revocation denylist backed by Redis (ADR-0011). On logout the access token's {@code jti} is
 * recorded with a TTL equal to its remaining lifetime, so the entry expires exactly when the token
 * would have anyway — the denylist never grows unbounded. Enforced primarily at the gateway
 * (decision 4); this service is also consulted by auth-service's own decoder (defense-in-depth).
 */
@Service
public class RevocationService {

  /** Shared with the gateway's reactive revocation check; keep the two in sync. */
  static final String KEY_PREFIX = "revoked:jti:";

  private final StringRedisTemplate redis;
  private final Clock clock;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "redis is an application-scoped Spring singleton supplied by the container via constructor"
              + " injection; sharing the RedisTemplate is the intended DI pattern, not exposure of"
              + " internal mutable state (governance §29).")
  public RevocationService(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
  }

  /** Denylists {@code jti} until {@code expiresAt}. A no-op for an already-expired token. */
  public void revoke(String jti, Instant expiresAt) {
    if (jti == null || expiresAt == null) {
      return;
    }
    Duration ttl = Duration.between(Instant.now(clock), expiresAt);
    if (ttl.isZero() || ttl.isNegative()) {
      return;
    }
    redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
  }

  public boolean isRevoked(String jti) {
    return jti != null && Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
  }
}
