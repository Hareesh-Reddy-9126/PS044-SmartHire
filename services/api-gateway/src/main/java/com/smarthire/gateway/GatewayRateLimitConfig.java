package com.smarthire.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Rate-limiting key resolvers and fail-closed Redis rate limiter. Provides two strategies:
 *
 * <ul>
 *   <li>{@link #userKeyResolver} — keys by authenticated user's JWT {@code sub} claim when
 *       available, ensuring per-user quotas for authenticated traffic.
 *   <li>{@link #ipKeyResolver} — keys by client IP address, used as a fallback for anonymous routes
 *       (e.g., auth endpoints) and when no user identity can be resolved.
 * </ul>
 *
 * <p>Requests with no resolvable identity share a single {@code "unknown"} bucket rather than
 * bypassing the limit entirely.
 *
 * <p>The {@link #failClosedRedisRateLimiter} bean replaces the standard {@code redisRateLimiter} to
 * enforce fail-closed behavior: when Redis is unavailable, rate-limited routes return 503 instead
 * of silently allowing traffic through.
 */
@Configuration
public class GatewayRateLimitConfig {

  /**
   * Caddy is the only public entry point and appends the real client address to X-Forwarded-For.
   * Reading the last trusted hop prevents every anonymous caller from sharing Caddy's container IP
   * (which would make the rate limit effectively global).
   */
  private final XForwardedRemoteAddressResolver forwardedAddressResolver =
      XForwardedRemoteAddressResolver.maxTrustedIndex(1);

  /**
   * Keys requests by the authenticated user's JWT subject ({@code sub} claim). When no
   * authentication is present or the principal is not a JWT, returns {@code "unknown"} so the
   * request still receives a limit (fail-closed for rate limiting).
   */
  @Bean
  public KeyResolver userKeyResolver() {
    return exchange ->
        exchange
            .getPrincipal()
            .filter(p -> p instanceof Authentication)
            .map(p -> (Authentication) p)
            .filter(a -> a.getPrincipal() instanceof JwtAuthenticationToken)
            .map(a -> ((JwtAuthenticationToken) a.getPrincipal()).getToken())
            .map(token -> token.getClaimAsString("sub"))
            .defaultIfEmpty("unknown");
  }

  /**
   * Keys requests by client IP address. Used for anonymous endpoints (e.g., auth routes) and as a
   * fallback when user identity cannot be resolved. Requests with no resolvable remote address
   * share a single {@code "unknown"} bucket.
   *
   * <p>Marked {@code @Primary} so the {@code RequestRateLimiterGatewayFilterFactory} has a single
   * default resolver; routes that need per-user limits reference {@code userKeyResolver} by name.
   */
  @Bean
  @Primary
  public KeyResolver ipKeyResolver() {
    return exchange ->
        Mono.justOrEmpty(forwardedAddressResolver.resolve(exchange))
            .map(addr -> addr.getAddress() != null ? addr.getAddress().getHostAddress() : "unknown")
            .defaultIfEmpty("unknown");
  }

  /**
   * Standard Redis rate limiter bean with default configuration. Exposed with a specific name so
   * the fail-closed wrapper can reference it without circular dependency.
   */
  @Bean
  public RedisRateLimiter standardRedisRateLimiter() {
    return new RedisRateLimiter(10, 20); // default replenishRate=10, burstCapacity=20
  }

  /**
   * Fail-closed Redis rate limiter. Wraps the standard {@link RedisRateLimiter} to reject requests
   * with 503 when Redis is unreachable, instead of failing open (which is the default Spring Cloud
   * Gateway behavior).
   *
   * <p>Routes continue to reference {@code "#{@redisRateLimiter}"} in configuration — this bean is
   * named {@code redisRateLimiter} and marked {@code @Primary} so it takes precedence over the
   * auto-configured standard bean.
   */
  @Bean
  @Primary
  public RedisRateLimiter redisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
    return new FailClosedRedisRateLimiter(standardRedisRateLimiter(), redisTemplate);
  }
}
