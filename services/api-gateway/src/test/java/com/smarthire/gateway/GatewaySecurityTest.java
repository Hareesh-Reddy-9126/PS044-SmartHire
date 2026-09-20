package com.smarthire.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Edge authorization behaviour without Docker, Redis or a live backend. Spring Security's web
 * filter chain runs ahead of the gateway's routing filters, so every DENY decision (401/403) is
 * produced by the gateway itself before any proxy hop — which is what these HTTP assertions pin:
 * deny-by-default for protected and unrouted paths, the RFC 9457 problem body, and the public GET
 * {@code /api/v1/jobs/**} allow-list surviving (decisions 4–6, DoD AC-1).
 *
 * <p>The auth route carries a {@code RequestRateLimiter} that talks to Redis, so it is verified
 * structurally (via {@link RouteLocator}) rather than over HTTP — driving it here would require a
 * live Redis. The authenticated happy path (valid token → 200, revoked jti → 401, rate-limit → 429)
 * needs real Redis + JWKS + backends and is proven via {@code docker compose} + curl; it is not
 * reachable from a unit test on this host.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "eureka.client.register-with-eureka=false",
      "eureka.client.fetch-registry=false"
    })
class GatewaySecurityTest {

  @Autowired private WebTestClient client;
  @Autowired private RouteLocator routeLocator;

  @Test
  void protectedAuthEndpointWithoutATokenIsUnauthorizedProblemJson() {
    client
        .get()
        .uri("/api/v1/auth/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Unauthorized")
        .jsonPath("$.status")
        .isEqualTo(401);
  }

  @Test
  void adminEndpointWithoutATokenIsUnauthorized() {
    // Deny-by-default authenticates first; the ADMIN role check itself stays in auth-service.
    client
        .get()
        .uri("/api/v1/auth/admin/whoami")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void unroutedPathIsDeniedByDefault() {
    // No gateway route maps this path; security still rejects it before routing is consulted.
    client.get().uri("/api/v1/applications/1").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void publicGetJobsIsNotBlockedBySecurity() {
    // Security permits it; with no job-service instance registered the load-balancer hop yields a
    // routing 5xx — never 401/403. That distinction is exactly what proves the public allow-list.
    int status =
        client.get().uri("/api/v1/jobs/42").exchange().returnResult(Void.class).getStatus().value();
    assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(status).isNotEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void authRouteIsWiredToTheLoadBalancedAuthService() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();

    Route authRoute =
        routes.stream()
            .filter(r -> "auth-service".equals(r.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("gateway has no route with id 'auth-service'"));

    assertThat(authRoute.getUri().getScheme()).isEqualTo("lb");
    assertThat(authRoute.getUri().toString()).isEqualToIgnoringCase("lb://AUTH-SERVICE");
    assertThat(predicateMatches(authRoute, "/api/v1/auth/login")).isTrue();
    assertThat(predicateMatches(authRoute, "/api/v1/auth/me")).isTrue();
    assertThat(predicateMatches(authRoute, "/api/v1/jobs")).isFalse();
  }

  private static boolean predicateMatches(Route route, String path) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
  }
}
