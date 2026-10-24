package com.smarthire.application.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarthire.application.infra.JobClient.JobServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the real {@link JobClient} Feign proxy, wrapped by the Resilience4j Spring Boot aspects
 * configured from {@code application.yml}, against a scripted HTTP downstream. Complements {@link
 * JobClientResilienceTest}, which covers the Resilience4j primitives in isolation.
 */
@SpringBootTest(
    classes = FeignResilienceTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "resilience4j.retry.instances.jobService.waitDuration=10ms")
class JobClientFeignResilienceTest {

  private static final StubDownstream DOWNSTREAM = StubDownstream.start();

  @DynamicPropertySource
  static void downstreamUrl(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.openfeign.client.config.JOB-SERVICE.url", DOWNSTREAM::baseUrl);
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.close();
  }

  @Autowired private JobClient jobClient;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  private final UUID jobId = UUID.randomUUID();
  private final UUID ownerOrgId = UUID.randomUUID();
  private CircuitBreaker circuitBreaker;

  @BeforeEach
  void setUp() {
    DOWNSTREAM.reset();
    circuitBreaker = circuitBreakerRegistry.circuitBreaker("jobService");
    circuitBreaker.reset();
  }

  @Test
  @DisplayName("Reads the job from job-service's public job endpoint")
  void readsJob() {
    DOWNSTREAM.enqueue(200, jobJson());

    JobClient.JobResponse job = jobClient.getJob(jobId);

    assertThat(job.jobId()).isEqualTo(jobId);
    assertThat(job.ownerOrgId()).isEqualTo(ownerOrgId);
    assertThat(job.isOpen()).isTrue();
    assertThat(DOWNSTREAM.requests())
        .singleElement()
        .satisfies(
            recorded -> {
              assertThat(recorded.method()).isEqualTo("GET");
              assertThat(recorded.path()).isEqualTo("/api/v1/jobs/" + jobId);
            });
  }

  @Test
  @DisplayName("Transient 503 is retried and the job is returned")
  void retriesTransientFailureThenSucceeds() {
    DOWNSTREAM.enqueue(503, "");
    DOWNSTREAM.enqueue(200, jobJson());

    JobClient.JobResponse job = jobClient.getJob(jobId);

    assertThat(job.jobId()).isEqualTo(jobId);
    assertThat(DOWNSTREAM.requests()).hasSize(2);
  }

  @Test
  @DisplayName("Retry exhaustion makes maxAttempts calls, then surfaces JobServiceUnavailable")
  void exhaustedRetriesSurfaceAsUnavailable() {
    DOWNSTREAM.respondAlways(503, "");

    assertThatThrownBy(() -> jobClient.getJob(jobId))
        .isInstanceOf(JobServiceUnavailableException.class);

    assertThat(DOWNSTREAM.requests()).hasSize(3);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("404 propagates unchanged (so callers answer 404, not 503) and is not retried")
  void notFoundPropagatesUnchanged() {
    DOWNSTREAM.respondAlways(404, "{\"detail\":\"Job not found\"}");

    assertThatThrownBy(() -> jobClient.getJob(jobId)).isInstanceOf(FeignException.NotFound.class);

    assertThat(DOWNSTREAM.requests()).hasSize(1);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  @DisplayName("Circuit opens after the configured failures and then short-circuits")
  void circuitOpensAndShortCircuits() {
    DOWNSTREAM.respondAlways(503, "");
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> jobClient.getJob(jobId))
          .isInstanceOf(JobServiceUnavailableException.class);
    }
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    int callsWhileClosed = DOWNSTREAM.requests().size();

    assertThatThrownBy(() -> jobClient.getJob(jobId))
        .isInstanceOf(JobServiceUnavailableException.class);

    assertThat(DOWNSTREAM.requests()).hasSize(callsWhileClosed);
  }

  private String jobJson() {
    return """
        {"jobId":"%s","companyName":"Acme","role":"Backend Engineer","location":"Remote",\
        "status":"OPEN","ownerOrgId":"%s"}"""
        .formatted(jobId, ownerOrgId);
  }
}
