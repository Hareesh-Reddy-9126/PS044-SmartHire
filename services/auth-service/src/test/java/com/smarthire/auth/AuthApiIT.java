package com.smarthire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.auth.api.dto.LoginRequest;
import com.smarthire.auth.api.dto.RegisterRequest;
import com.smarthire.auth.api.dto.TokenResponse;
import com.smarthire.auth.infra.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack security integration test for auth-service against real PostgreSQL + Redis
 * (Testcontainers, governance §23 — no H2). Exercises Flyway → JPA → service → controller → HTTP
 * for the core token lifecycle: register → login → authenticated {@code /me} → refresh rotation →
 * refresh-reuse rejection (the ADR-0009 theft-detection guarantee).
 *
 * <p>{@code disabledWithoutDocker = true}: JUnit reports this class as <em>skipped</em> (never
 * passed) on any host where docker-java cannot reach a Docker daemon, and runs it for real
 * everywhere it can (Linux CI). It is skipped on the Windows + Docker Desktop dev host whose Engine
 * API the bundled docker-java npipe transport cannot handshake; the same path is proven live there
 * via {@code docker compose up} + curl through the gateway. Rate limiting is enforced at the
 * gateway (decision 4), not this service, so it is not exercised here.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.profiles.active=local")
@Testcontainers(disabledWithoutDocker = true)
class AuthApiIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    // No registry in the test context — do not attempt Eureka registration.
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("eureka.client.register-with-eureka", () -> "false");
    registry.add("eureka.client.fetch-registry", () -> "false");
  }

  @Autowired private TestRestTemplate rest;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Test
  void registerLoginMeRefreshRotationAndReuseRejection() {
    String email = "candidate@example.com";
    String password = "sufficiently-long-password";

    ResponseEntity<Void> register =
        rest.postForEntity(
            "/api/v1/auth/register", new RegisterRequest(email, password), Void.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<TokenResponse> login =
        rest.postForEntity(
            "/api/v1/auth/login", new LoginRequest(email, password), TokenResponse.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    TokenResponse tokens = login.getBody();
    assertThat(tokens).isNotNull();
    assertThat(tokens.accessToken()).isNotBlank();
    String refreshCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(refreshCookie).contains("HttpOnly", "Secure", "SameSite=Strict");

    // Authenticated identity endpoint: email is DB-sourced, not a token claim (ADR-0009).
    HttpHeaders bearer = new HttpHeaders();
    bearer.setBearerAuth(tokens.accessToken());
    ResponseEntity<String> me =
        rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(bearer), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).contains(email);

    // Rotation issues a different refresh token...
    HttpHeaders refreshHeaders = new HttpHeaders();
    refreshHeaders.add(HttpHeaders.COOKIE, refreshCookie.split(";", 2)[0]);
    ResponseEntity<TokenResponse> rotated =
        rest.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            new HttpEntity<>(refreshHeaders),
            TokenResponse.class);
    assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rotated.getBody()).isNotNull();
    String rotatedCookie = rotated.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(rotatedCookie).contains("HttpOnly", "Secure", "SameSite=Strict");

    // ...and reusing the now-revoked original is rejected as theft.
    ResponseEntity<String> reuse =
        rest.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            new HttpEntity<>(refreshHeaders),
            String.class);
    assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(refreshTokenRepository.findAll())
        .isNotEmpty()
        .allMatch(com.smarthire.auth.domain.RefreshToken::isRevoked);
  }

  @Test
  void loginWithUnknownEmailReturnsUnauthorizedWithoutServerError() {
    String unknownEmail = "unknown@example.com";
    String anyPassword = "some-password-123";

    ResponseEntity<String> login =
        rest.postForEntity(
            "/api/v1/auth/login", new LoginRequest(unknownEmail, anyPassword), String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(login.getBody()).contains("Invalid email or password");
  }
}
