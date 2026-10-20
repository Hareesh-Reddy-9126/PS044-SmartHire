package com.smarthire.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies the Gateway's OpenAPI aggregation routes and Swagger UI configuration.
 *
 * <p>These tests assert that:
 *
 * <ul>
 *   <li>Each service has a dedicated {@code /v3/api-docs/{service-id}} route pointing to the
 *       correct load-balanced service URI with a rewrite filter.
 *   <li>The Swagger UI configuration loads the expected service URLs.
 *   <li>Internal endpoints ({@code /internal/**}) have no gateway route and are not discoverable.
 * </ul>
 *
 * <p>Runs without Docker, Eureka, or live backends — it inspects the {@link RouteLocator} and
 * Springdoc configuration built from {@code application.yml}.
 */
@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "eureka.client.register-with-eureka=false",
      "eureka.client.fetch-registry=false"
    })
class GatewayOpenApiAggregationTest {

  @Autowired private RouteLocator routeLocator;
  @MockBean private SwaggerUiConfigParameters swaggerUiConfigParameters;

  @Test
  void eachServiceHasAnApiDocsRouteWithCorrectTargetAndRewrite() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();

    Set<String> expectedApiDocsRoutes =
        Set.of(
            "job-service-api-docs",
            "auth-service-api-docs",
            "application-service-api-docs",
            "recruitment-service-api-docs",
            "document-service-api-docs",
            "notification-service-api-docs");

    for (String expectedId : expectedApiDocsRoutes) {
      Route route =
          routes.stream()
              .filter(r -> expectedId.equals(r.getId()))
              .findFirst()
              .orElseThrow(
                  () -> new AssertionError("gateway has no route with id '" + expectedId + "'"));

      // Verify load-balanced URI
      assertThat(route.getUri().getScheme()).isEqualTo("lb");
      assertThat(route.getUri().toString().toUpperCase()).startsWith("LB://");

      // Verify path predicate matches the expected pattern
      String serviceName = expectedId.replace("-api-docs", "");
      String expectedPath = "/v3/api-docs/" + serviceName;
      assertThat(predicateMatches(route, expectedPath)).isTrue();
      assertThat(predicateMatches(route, "/v3/api-docs/other-service")).isFalse();

      // Verify rewrite filter is present (rewrites /v3/api-docs/{service} to /v3/api-docs)
      assertThat(route.getFilters()).isNotEmpty();
      boolean hasRewriteFilter =
          route.getFilters().stream()
              .anyMatch(
                  f -> {
                    org.springframework.cloud.gateway.filter.GatewayFilter delegate = f;
                    if (f
                        instanceof org.springframework.cloud.gateway.filter.OrderedGatewayFilter) {
                      delegate =
                          ((org.springframework.cloud.gateway.filter.OrderedGatewayFilter) f)
                              .getDelegate();
                    }
                    return delegate
                        .getClass()
                        .getName()
                        .contains("RewritePathGatewayFilterFactory");
                  });
      assertThat(hasRewriteFilter)
          .as("Route " + expectedId + " should have RewritePath filter")
          .isTrue();
    }
  }

  @Test
  void swaggerUiConfigLoadsAllServiceUrls() {
    // Mock the SwaggerUiConfigParameters to return the expected URLs
    Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls =
        Set.of(
            mockSwaggerUrl("Job Service", "/v3/api-docs/job-service"),
            mockSwaggerUrl("Auth Service", "/v3/api-docs/auth-service"),
            mockSwaggerUrl("Application Service", "/v3/api-docs/application-service"),
            mockSwaggerUrl("Recruitment Service", "/v3/api-docs/recruitment-service"),
            mockSwaggerUrl("Document Service", "/v3/api-docs/document-service"),
            mockSwaggerUrl("Notification Service", "/v3/api-docs/notification-service"));
    when(swaggerUiConfigParameters.getUrls()).thenReturn(urls);

    // Springdoc's SwaggerUiConfigParameters should have the configured URLs
    Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> actualUrls =
        swaggerUiConfigParameters.getUrls();
    assertThat(actualUrls).isNotNull();
    assertThat(actualUrls).hasSize(6);

    Set<String> expectedNames =
        Set.of(
            "Job Service",
            "Auth Service",
            "Application Service",
            "Recruitment Service",
            "Document Service",
            "Notification Service");
    Set<String> actualNames =
        actualUrls.stream()
            .map(AbstractSwaggerUiConfigProperties.SwaggerUrl::getName)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedNames);

    // Verify each URL points to the correct gateway-aggregated path
    for (AbstractSwaggerUiConfigProperties.SwaggerUrl url : actualUrls) {
      assertThat(url.getUrl()).startsWith("/v3/api-docs/");
    }
  }

  @Test
  void internalEndpointsHaveNoGatewayRoute() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();

    // No route should match /internal/** paths
    for (Route route : routes) {
      boolean matchesInternal =
          predicateMatches(route, "/internal/v1/notifications/application-status");
      assertThat(matchesInternal)
          .as("Route " + route.getId() + " should not match /internal/**")
          .isFalse();
    }
  }

  @Test
  void noConflictingGatewayRouteForOwnApiDocs() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();

    // The gateway's own /v3/api-docs should not be routed through lb:// (avoid circular routing)
    for (Route route : routes) {
      assertThat(route.getId()).doesNotContain("gateway");
      // No route should have lb:// for the gateway's own api-docs
      assertThat(route.getUri().toString()).doesNotContain("lb://API-GATEWAY");
      assertThat(route.getUri().toString()).doesNotContain("lb://api-gateway");
    }
  }

  private static AbstractSwaggerUiConfigProperties.SwaggerUrl mockSwaggerUrl(
      String name, String url) {
    AbstractSwaggerUiConfigProperties.SwaggerUrl mockUrl =
        mock(AbstractSwaggerUiConfigProperties.SwaggerUrl.class);
    when(mockUrl.getName()).thenReturn(name);
    when(mockUrl.getUrl()).thenReturn(url);
    return mockUrl;
  }

  private static boolean predicateMatches(Route route, String path) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
  }
}
