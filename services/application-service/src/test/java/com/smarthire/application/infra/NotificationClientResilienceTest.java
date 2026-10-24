package com.smarthire.application.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.common.contract.notification.NotificationResponse;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadProperties;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the real {@link NotificationClient} Feign proxy, wrapped by the Resilience4j Spring
 * Boot aspects, against a scripted HTTP downstream.
 *
 * <p>Instance names, retry attempts, retry/ignore exception policy and circuit-breaker thresholds
 * come from the service's own {@code application.yml}; only the backoff wait (kept short so the
 * suite stays fast) and the bulkhead size (shrunk to make saturation observable) are overridden.
 */
@SpringBootTest(
    classes = FeignResilienceTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "resilience4j.retry.instances.notificationService.waitDuration=10ms",
      "resilience4j.bulkhead.instances.notificationService.maxConcurrentCalls=1"
    })
class NotificationClientResilienceTest {

  private static final String INSTANCE = "notificationService";
  private static final String ENDPOINT = "/internal/v1/notifications/application-status";
  private static final StubDownstream DOWNSTREAM = StubDownstream.start();

  @DynamicPropertySource
  static void downstreamUrl(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.openfeign.client.config.NOTIFICATION-SERVICE.url", DOWNSTREAM::baseUrl);
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.close();
  }

  @Autowired private NotificationClient notificationClient;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
  @Autowired private CircuitBreakerProperties circuitBreakerProperties;
  @Autowired private RetryProperties retryProperties;
  @Autowired private BulkheadProperties bulkheadProperties;
  @Autowired private ObjectMapper objectMapper;

  private final ApplicationStatusChangeRequest request =
      new ApplicationStatusChangeRequest(UUID.randomUUID(), UUID.randomUUID(), "SHORTLISTED");

  private CircuitBreaker circuitBreaker;

  @BeforeEach
  void setUp() {
    DOWNSTREAM.reset();
    circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
    circuitBreaker.reset();
  }

  @Test
  @DisplayName("Annotations reference the notificationService instances defined in application.yml")
  void annotationsUseConfiguredInstanceNames() throws NoSuchMethodException {
    Method method =
        NotificationClient.class.getMethod(
            "sendNotification", ApplicationStatusChangeRequest.class);

    assertThat(method.getAnnotation(Retry.class).name()).isEqualTo(INSTANCE);
    assertThat(
            method
                .getAnnotation(
                    io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker.class)
                .name())
        .isEqualTo(INSTANCE);
    assertThat(method.getAnnotation(Bulkhead.class).name()).isEqualTo(INSTANCE);

    // A typo in the annotation name would silently fall back to resilience4j defaults; assert the
    // name resolves to an explicitly configured instance instead.
    assertThat(retryProperties.getInstances()).containsKey(INSTANCE);
    assertThat(retryProperties.getInstances().get(INSTANCE).getMaxAttempts()).isEqualTo(3);
    assertThat(circuitBreakerProperties.getInstances()).containsKey(INSTANCE);
    assertThat(bulkheadProperties.getInstances()).containsKey(INSTANCE);
  }

  @Test
  @DisplayName("Posts the status change to notification-service's internal endpoint")
  void postsToInternalNotificationEndpoint() throws JsonProcessingException {
    DOWNSTREAM.enqueue(202, accepted());

    NotificationResponse response = notificationClient.sendNotification(request);

    assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(DOWNSTREAM.requests())
        .singleElement()
        .satisfies(
            recorded -> {
              assertThat(recorded.method()).isEqualTo("POST");
              assertThat(recorded.path()).isEqualTo(ENDPOINT);
              assertThat(recorded.body())
                  .contains(request.candidateId().toString())
                  .contains(request.applicationId().toString())
                  .contains("\"status\":\"SHORTLISTED\"");
            });
  }

  @Test
  @DisplayName("Transient 503 is retried and the downstream's real response is returned")
  void retriesTransientFailureThenSucceeds() throws JsonProcessingException {
    DOWNSTREAM.enqueue(503, "");
    DOWNSTREAM.enqueue(202, accepted());

    NotificationResponse response = notificationClient.sendNotification(request);

    assertThat(DOWNSTREAM.requests()).hasSize(2);
    assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(response.errorMessage()).isNull();
  }

  @Test
  @DisplayName("Retry exhaustion makes maxAttempts calls, then returns the best-effort fallback")
  void fallsBackAfterRetryExhaustion() {
    DOWNSTREAM.respondAlways(503, "");

    NotificationResponse response = notificationClient.sendNotification(request);

    assertThat(DOWNSTREAM.requests()).hasSize(3);
    assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(response.errorMessage()).contains("unavailable");
    // One logical call, one circuit-breaker outcome — retries are not counted individually.
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("4xx is neither retried, masked by the fallback, nor counted as a CB failure")
  void clientErrorIsNotRetriedOrMasked() {
    DOWNSTREAM.respondAlways(400, "{\"detail\":\"validation failed\"}");

    assertThatThrownBy(() -> notificationClient.sendNotification(request))
        .isInstanceOf(FeignException.BadRequest.class);

    assertThat(DOWNSTREAM.requests()).hasSize(1);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  @DisplayName("Circuit opens after the configured failures and then short-circuits to fallback")
  void circuitOpensAndShortCircuits() {
    DOWNSTREAM.respondAlways(503, "");

    // minimumNumberOfCalls=5, failureRateThreshold=50 (application.yml)
    for (int i = 0; i < 5; i++) {
      notificationClient.sendNotification(request);
    }
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    int callsWhileClosed = DOWNSTREAM.requests().size();

    NotificationResponse response = notificationClient.sendNotification(request);

    assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(DOWNSTREAM.requests()).hasSize(callsWhileClosed);
  }

  @Test
  @DisplayName("Bulkhead rejects calls beyond the concurrency limit without reaching downstream")
  void bulkheadRejectsExcessConcurrentCalls() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    DOWNSTREAM.holdResponsesUntil(release);
    DOWNSTREAM.respondAlways(202, accepted());
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<NotificationResponse> inFlight =
          executor.submit(() -> notificationClient.sendNotification(request));
      awaitRequests(1);

      NotificationResponse rejected = notificationClient.sendNotification(request);

      assertThat(rejected.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
      assertThat(DOWNSTREAM.requests()).hasSize(1);
      release.countDown();
      assertThat(inFlight.get(10, TimeUnit.SECONDS).deliveryStatus())
          .isEqualTo(DeliveryStatus.SENT);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private String accepted() throws JsonProcessingException {
    Instant now = Instant.now();
    return objectMapper.writeValueAsString(
        new NotificationResponse(
            UUID.randomUUID(),
            request.candidateId(),
            request.applicationId(),
            request.status(),
            "candidate@example.com",
            "Application update",
            "Your application was shortlisted",
            DeliveryStatus.SENT,
            now,
            now,
            null));
  }

  private static void awaitRequests(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (DOWNSTREAM.requests().size() < expected && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(DOWNSTREAM.requests()).hasSize(expected);
  }
}
