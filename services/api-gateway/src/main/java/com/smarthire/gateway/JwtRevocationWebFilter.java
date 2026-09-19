package com.smarthire.gateway;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Enforces JWT revocation at the edge (decision 4). Installed immediately after authentication in
 * the security chain, so an access token whose {@code jti} auth-service denylisted on logout is
 * rejected with 401 before the request is ever routed downstream. It reads the same Redis key
 * auth-service writes ({@code revoked:jti:<jti>}), keeping the denylist a single source of truth;
 * auth-service's own decoder re-checks it as defense-in-depth. Anonymous (public-route) requests
 * carry no authenticated {@code jti} and pass straight through.
 */
public class JwtRevocationWebFilter implements WebFilter {

  /** Cross-service contract: auth-service's {@code RevocationService} writes this exact prefix. */
  static final String KEY_PREFIX = "revoked:jti:";

  private final ReactiveStringRedisTemplate redis;
  private final GatewayProblemWriter problems;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "redis and problems are application-scoped Spring singletons supplied by the container via"
              + " constructor injection; sharing these collaborators is the intended DI pattern, not"
              + " exposure of internal mutable state (governance §29).")
  public JwtRevocationWebFilter(ReactiveStringRedisTemplate redis, GatewayProblemWriter problems) {
    this.redis = redis;
    this.problems = problems;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .filter(JwtAuthenticationToken.class::isInstance)
        .map(authentication -> ((JwtAuthenticationToken) authentication).getToken().getId())
        .filter(StringUtils::hasText)
        .flatMap(jti -> redis.hasKey(KEY_PREFIX + jti))
        .defaultIfEmpty(Boolean.FALSE)
        .flatMap(
            revoked ->
                Boolean.TRUE.equals(revoked)
                    ? problems.write(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        "This token has been revoked.")
                    : chain.filter(exchange));
  }
}
