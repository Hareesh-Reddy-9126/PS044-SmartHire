package com.smarthire.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.job.api.dto.JobResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test for the job-service slice against a real PostgreSQL (Testcontainers,
 * governance §23 — no H2). Exercises Flyway migration + seed → JPA → service → controller → HTTP,
 * proving the {@code GET /api/v1/jobs} endpoint the gateway routes to actually works.
 *
 * <p>{@code disabledWithoutDocker = true}: JUnit reports this class as <em>skipped</em> (never
 * passed) on any host where docker-java cannot reach a Docker daemon, and runs it for real
 * everywhere it can (Linux CI). It is skipped on the Windows + Docker Desktop 29.8 dev host, whose
 * Engine API 1.56 the bundled docker-java 3.4.0 npipe transport cannot handshake; the same
 * integration path is proven live there via {@code docker compose up} + curl through the gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class JobApiIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // No registry in the test context — do not attempt Eureka registration.
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("eureka.client.register-with-eureka", () -> "false");
    registry.add("eureka.client.fetch-registry", () -> "false");
  }

  @Autowired private TestRestTemplate rest;

  @Test
  void listJobsReturnsSeededRowsThroughTheFullStack() {
    ResponseEntity<List<JobResponse>> response =
        rest.exchange(
            "/api/v1/jobs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<JobResponse>>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody())
        .extracting(JobResponse::companyName)
        .contains("Acme Corp", "Globex Inc");
    assertThat(response.getBody())
        .allSatisfy(job -> assertThat(job.status()).isIn("DRAFT", "OPEN", "CLOSED"));
  }
}
