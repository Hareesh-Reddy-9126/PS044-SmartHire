package com.smarthire.gateway;

import java.util.Map;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Fail-closed wrapper around {@link RedisRateLimiter}.
 *
 * <p>When Redis is available, delegates to the standard rate limiter. When Redis is unavailable
 * (connection refused, timeout, etc.), <strong>rejects the request with 503</strong> instead of
 * allowing it through (fail-open). This implements the fail-closed policy required for sensitive
 * routes (auth, mutations).
 *
 * <p>The wrapper is a drop-in replacement for the {@code redisRateLimiter} bean — routes continue
 * to reference {@code "#{@redisRateLimiter}"} in configuration.
 */
public class FailClosedRedisRateLimiter extends RedisRateLimiter {

  private final RedisRateLimiter delegate;
  private final ReactiveStringRedisTemplate redisTemplate;

  public FailClosedRedisRateLimiter(
      RedisRateLimiter delegate, ReactiveStringRedisTemplate redisTemplate) {
    // Parent constructor requires replenishRate and burstCapacity; values are irrelevant since
    // we delegate all logic to the standard bean. Config is exposed via getConfig() delegation.
    super(1, 1);
    this.delegate = delegate;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Mono<Response> isAllowed(String routeId, String id) {
    // Quick connectivity check before delegating — avoids silent fail-open on Redis failure.
    // Use ping (PING command) instead of GET to distinguish between "Redis unreachable" (error)
    // and "key not found" (empty result but connection OK).
    return redisTemplate
        .execute(connection -> connection.ping())
        .onErrorResume(e -> Mono.empty()) // Redis unreachable -> empty
        .flatMap(pong -> {
          // If ping succeeded (even if pong is null/empty), Redis is reachable
          return delegate.isAllowed(routeId, id);
        })
        .switchIfEmpty(
            Mono.error(
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Rate limiter unavailable -- Redis connection failed",
                    null)));
  }

  @Override
  public Map<String, Config> getConfig() {
    return delegate.getConfig();
  }
}
