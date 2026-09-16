package com.smarthire.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Asserts the gateway's declared route to the job-service is wired as the increment requires: the
 * {@code /api/v1/jobs/**} path predicate resolves to {@code lb://JOB-SERVICE} (client-side
 * load-balanced discovery) and unrelated paths are not captured (deny-by-default; the discovery
 * locator is off). Runs without Docker or a live registry — it inspects the {@link RouteLocator}
 * the gateway builds from {@code application.yml} and exercises the real path predicate. The live
 * end-to-end hop (gateway → Eureka → job-service) is proven separately via {@code docker compose}
 * plus the routed 200.
 */
@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "eureka.client.register-with-eureka=false",
      "eureka.client.fetch-registry=false"
    })
class GatewayRoutingTest {

  @Autowired private RouteLocator routeLocator;

  @Test
  void jobsPathRoutesToTheLoadBalancedJobService() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();

    Route jobRoute =
        routes.stream()
            .filter(r -> "job-service".equals(r.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("gateway has no route with id 'job-service'"));

    assertThat(jobRoute.getUri().getScheme()).isEqualTo("lb");
    assertThat(jobRoute.getUri().toString()).isEqualToIgnoringCase("lb://JOB-SERVICE");
    assertThat(predicateMatches(jobRoute, "/api/v1/jobs")).isTrue();
    assertThat(predicateMatches(jobRoute, "/api/v1/jobs/42")).isTrue();
    assertThat(predicateMatches(jobRoute, "/api/v1/candidates")).isFalse();
  }

  private static boolean predicateMatches(Route route, String path) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
  }
}
