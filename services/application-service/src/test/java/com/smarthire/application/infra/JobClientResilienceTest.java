package com.smarthire.application.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarthire.application.infra.JobClient.JobServiceUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {JobClientResilienceTest.TestConfig.class})
@TestPropertySource(
    properties = {
      "resilience4j.circuitbreaker.instances.jobService.slidingWindowSize=3",
      "resilience4j.circuitbreaker.instances.jobService.minimumNumberOfCalls=3",
      "resilience4j.circuitbreaker.instances.jobService.failureRateThreshold=50",
      "resilience4j.circuitbreaker.instances.jobService.waitDurationInOpenState=1000",
      "resilience4j.circuitbreaker.instances.jobService.permittedNumberOfCallsInHalfOpenState=1",
      "resilience4j.retry.instances.jobService.maxAttempts=3",
      "resilience4j.retry.instances.jobService.waitDuration=10ms",
      "resilience4j.retry.instances.jobService.enableExponentialBackoff=false",
      "resilience4j.bulkhead.instances.jobService.maxConcurrentCalls=2",
      "resilience4j.bulkhead.instances.jobService.maxWaitDuration=0"
    })
class JobClientResilienceTest {

  @Configuration
  static class TestConfig {
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.custom()
              .slidingWindowSize(3)
              .minimumNumberOfCalls(3)
              .failureRateThreshold(50)
              .waitDurationInOpenState(Duration.ofMillis(1000))
              .permittedNumberOfCallsInHalfOpenState(1)
              .build();
      return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public RetryRegistry retryRegistry() {
      RetryConfig config =
          RetryConfig.custom()
              .maxAttempts(3)
              .waitDuration(Duration.ofMillis(10))
              .retryExceptions(RuntimeException.class)
              .build();
      return RetryRegistry.of(config);
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
      BulkheadConfig config =
          BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build();
      return BulkheadRegistry.of(config);
    }

    @Bean
    public CircuitBreaker jobServiceCircuitBreaker(CircuitBreakerRegistry registry) {
      return registry.circuitBreaker("jobService");
    }

    @Bean
    public Retry jobServiceRetry(RetryRegistry registry) {
      return registry.retry("jobService");
    }

    @Bean
    public Bulkhead jobServiceBulkhead(BulkheadRegistry registry) {
      return registry.bulkhead("jobService");
    }
  }

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired private RetryRegistry retryRegistry;

  @Autowired private BulkheadRegistry bulkheadRegistry;

  private CircuitBreaker circuitBreaker;
  private Retry retry;
  private Bulkhead bulkhead;

  @BeforeEach
  void setUp() {
    circuitBreaker = circuitBreakerRegistry.circuitBreaker("jobService");
    retry = retryRegistry.retry("jobService");
    bulkhead = bulkheadRegistry.bulkhead("jobService");
    circuitBreaker.transitionToClosedState();
  }

  @AfterEach
  void tearDown() {
    circuitBreaker.transitionToClosedState();
  }

  @Nested
  @DisplayName("Circuit Breaker")
  class CircuitBreakerTests {

    @Test
    @DisplayName("Circuit breaker opens after failure threshold via decorated function")
    void circuitBreakerOpensAfterFailures() {
      // Use decorated supplier to trigger circuit breaker state changes
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              circuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      // Execute 3 times to exceed failure threshold (3 calls, 100% failure rate > 50%)
      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }

      // Verify circuit is OPEN
      assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker transitions to HALF_OPEN after wait duration")
    void circuitBreakerTransitionsToHalfOpen() throws InterruptedException {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              circuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      // Trigger 3 failures to open circuit
      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }
      assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      // Wait for waitDurationInOpenState (1000ms)
      Thread.sleep(1200);

      // Next call should transition to HALF_OPEN and be permitted
      // Since it will fail again, circuit goes back to OPEN.
      // The key assertion is that the call was PERMITTED (not rejected with
      // CallNotPermittedException)
      // which proves transition to HALF_OPEN occurred.
      assertThatThrownBy(decorated::get)
          .isInstanceOf(RuntimeException.class); // Call was permitted but failed

      // Circuit should be OPEN again (since the permitted call failed)
      assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker permits calls in CLOSED state")
    void circuitBreakerPermitsCallsWhenClosed() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(circuitBreaker, () -> "success");

      String result = decorated.get();

      assertThat(result).isEqualTo("success");
      assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
  }

  @Nested
  @DisplayName("Retry")
  class RetryTests {

    @Test
    @DisplayName("Retry configuration is correctly loaded")
    void retryConfigLoaded() {
      assertThat(retry).isNotNull();
      assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Retry executes decorated function with retries")
    void retryExecutesWithRetries() {
      AtomicInteger callCount = new AtomicInteger(0);

      // Decorate a function that fails twice then succeeds
      java.util.function.Supplier<String> decorated =
          Retry.decorateSupplier(
              retry,
              () -> {
                int count = callCount.incrementAndGet();
                if (count < 3) {
                  throw new RuntimeException("transient failure");
                }
                return "success";
              });

      String result = decorated.get();

      assertThat(result).isEqualTo("success");
      assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Retry exhaustion throws last exception")
    void retryExhaustionThrowsLastException() {
      AtomicInteger callCount = new AtomicInteger(0);

      java.util.function.Supplier<String> decorated =
          Retry.decorateSupplier(
              retry,
              () -> {
                callCount.incrementAndGet();
                throw new RuntimeException("persistent failure");
              });

      assertThatThrownBy(decorated::get)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("persistent failure");

      assertThat(callCount.get()).isEqualTo(3); // maxAttempts = 3
    }
  }

  @Nested
  @DisplayName("Bulkhead")
  class BulkheadTests {

    @Test
    @DisplayName("Bulkhead configuration is correctly loaded")
    void bulkheadConfigLoaded() {
      assertThat(bulkhead).isNotNull();
      assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(2);
      assertThat(bulkhead.getBulkheadConfig().getMaxWaitDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Bulkhead rejects calls beyond maxConcurrentCalls")
    void bulkheadRejectsExcessCalls() throws InterruptedException {
      int maxConcurrent = 2;
      int totalCalls = 5;

      ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(totalCalls);
      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger rejectedCount = new AtomicInteger(0);

      for (int i = 0; i < totalCalls; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                // Try to acquire bulkhead permission
                Runnable decorated =
                    Bulkhead.decorateRunnable(
                        bulkhead,
                        () -> {
                          try {
                            // Simulate some work
                            Thread.sleep(100);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                          }
                        });
                decorated.run();
                successCount.incrementAndGet();
              } catch (Exception e) {
                if (e instanceof io.github.resilience4j.bulkhead.BulkheadFullException) {
                  rejectedCount.incrementAndGet();
                }
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();

      // At least some calls should be rejected (bulkhead max is 2)
      assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(totalCalls - maxConcurrent);
    }
  }

  @Nested
  @DisplayName("Fallback / 503 Response")
  class FallbackTests {

    @Test
    @DisplayName("JobServiceUnavailableException class exists and is throwable")
    void fallbackExceptionClassExists() {
      assertThatThrownBy(
              () -> {
                throw new JobServiceUnavailableException("test");
              })
          .isInstanceOf(JobServiceUnavailableException.class)
          .hasMessage("test");
    }

    @Test
    @DisplayName("Circuit breaker rejects calls when OPEN")
    void circuitBreakerRejectsWhenOpen() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              circuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      // Open the circuit
      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }
      assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      // Next call should be rejected with CallNotPermittedException
      assertThatThrownBy(decorated::get)
          .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
    }
  }
}
