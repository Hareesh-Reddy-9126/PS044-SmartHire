package com.smarthire.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.smarthire.job.api.dto.JobResponse;
import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.infra.JobRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test for the job-service slice against a real PostgreSQL (Testcontainers,
 * governance §23 — no H2). Exercises Flyway V1 + V2 → JPA → service → controller → HTTP, proving
 * both the Inc 0 {@code GET /api/v1/jobs} path the gateway routes to and the Inc 2 additions: the
 * {@code owner_org_id} backfill, its NOT NULL constraint, and the rule that DRAFT and CLOSED
 * postings never reach an anonymous caller.
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

  /**
   * The organization auth-service seeds in its own V1 migration; V2 backfills the Inc 0 rows to it.
   */
  private static final UUID DEMO_ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  private static final UUID DRAFT_JOB_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

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

  @Autowired private JobRepository jobRepository;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedANonPublicJob() {
    jobRepository.save(
        Job.createDraft(DRAFT_JOB_ID, "Initech", "Platform Engineer", "Remote", DEMO_ORG_ID));
  }

  @AfterEach
  void removeTheNonPublicJob() {
    jobRepository.deleteById(DRAFT_JOB_ID);
  }

  private ResponseEntity<PagedModel<JobResponse>> getJobs(String query) {
    return rest.exchange(
        "/api/v1/jobs" + query,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<PagedModel<JobResponse>>() {});
  }

  @Test
  void listJobsReturnsSeededRowsThroughTheFullStack() {
    ResponseEntity<PagedModel<JobResponse>> response = getJobs("");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    List<JobResponse> jobs = List.copyOf(response.getBody().getContent());
    assertThat(jobs).extracting(JobResponse::companyName).contains("Acme Corp", "Globex Inc");
    assertThat(jobs).allSatisfy(job -> assertThat(job.status()).isIn("DRAFT", "OPEN", "CLOSED"));
  }

  @Test
  void anonymousCallersSeeOnlyOpenJobs() {
    ResponseEntity<PagedModel<JobResponse>> response = getJobs("");

    List<JobResponse> jobs = List.copyOf(response.getBody().getContent());
    assertThat(jobs).allSatisfy(job -> assertThat(job.status()).isEqualTo("OPEN"));
    assertThat(jobs).extracting(JobResponse::jobId).doesNotContain(DRAFT_JOB_ID);
  }

  @Test
  void anonymousDetailOfADraftJobIsNotFound() {
    ResponseEntity<String> response =
        rest.getForEntity("/api/v1/jobs/" + DRAFT_JOB_ID, String.class);

    // 404, not 403: a non-owner must not be able to confirm that the identifier exists.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("Initech");
  }

  @Test
  void filtersNarrowThePublicListing() {
    ResponseEntity<PagedModel<JobResponse>> response = getJobs("?q=data%20analyst");

    List<JobResponse> jobs = List.copyOf(response.getBody().getContent());
    assertThat(jobs).extracting(JobResponse::companyName).containsExactly("Globex Inc");
  }

  @Test
  void locationFilterIsCaseInsensitiveAndPartial() {
    ResponseEntity<PagedModel<JobResponse>> response = getJobs("?location=bengaluru");

    List<JobResponse> jobs = List.copyOf(response.getBody().getContent());
    assertThat(jobs).extracting(JobResponse::companyName).containsExactly("Globex Inc");
  }

  @Test
  void aPublicCallerCannotRequestDraftJobsByStatus() {
    ResponseEntity<PagedModel<JobResponse>> response = getJobs("?status=DRAFT");

    // The status filter narrows within what the caller may see; it never widens it.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getContent()).isEmpty();
  }

  @Test
  void mutatingWithoutATokenIsRejectedByTheResourceServer() {
    ResponseEntity<String> response =
        rest.postForEntity("/api/v1/jobs/" + DRAFT_JOB_ID + "/open", null, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void migrationV2BackfillsTheInc0RowsWithTheSeededOrganisation() {
    List<UUID> owners =
        jdbc.queryForList(
            "SELECT owner_org_id FROM job_svc.jobs WHERE job_id IN (?, ?)",
            UUID.class,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"));

    assertThat(owners).hasSize(2).containsOnly(DEMO_ORG_ID);
  }

  @Test
  void ownerOrgIdIsNotNullableAndHasNoDefault() {
    assertThatExceptionOfType(DataIntegrityViolationException.class)
        .isThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO job_svc.jobs (job_id, company_name, role, location, status)"
                        + " VALUES (?, 'Orphan Ltd', 'Nobody', 'Nowhere', 'DRAFT')",
                    UUID.randomUUID()));
  }

  @Test
  void theStatusCheckConstraintFromV1SurvivesV2() {
    assertThatExceptionOfType(DataIntegrityViolationException.class)
        .isThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO job_svc.jobs (job_id, company_name, role, location, status,"
                        + " owner_org_id) VALUES (?, 'Acme', 'Dev', 'Remote', 'ARCHIVED', ?)",
                    UUID.randomUUID(),
                    DEMO_ORG_ID));
  }

  @Test
  void theRepositoryPersistsAndReadsBackOwnership() {
    Job stored = jobRepository.findByJobId(DRAFT_JOB_ID).orElseThrow();

    assertThat(stored.getOwnerOrgId()).isEqualTo(DEMO_ORG_ID);
    assertThat(stored.getStatus()).isEqualTo(JobStatus.DRAFT);
    assertThat(stored.isOwnedBy(DEMO_ORG_ID)).isTrue();
  }
}
