package com.smarthire.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate-limiting support. The {@link KeyResolver} buckets requests by client IP so the {@code
 * RequestRateLimiter} filter on the auth route (see {@code application.yml}) throttles
 * credential-stuffing / brute-force attempts per source address (decision 4). Requests with no
 * resolvable remote address share a single {@code "unknown"} bucket rather than bypassing the
 * limit.
 */
@Configuration
public class GatewayRateLimitConfig {

  @Bean
  public KeyResolver ipKeyResolver() {
    return exchange ->
        Mono.just(
            exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown");
  }
}
