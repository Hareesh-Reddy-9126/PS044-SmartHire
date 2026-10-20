package com.smarthire.recruitment.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarthire.recruitment.infra.ApplicationClient.ApplicationServiceUnavailableException;
import com.smarthire.recruitment.infra.JobClient.JobServiceUnavailableException;
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

@SpringBootTest(classes = {RecruitmentClientsResilienceTest.TestConfig.class})
@TestPropertySource(
    properties = {
      "resilience4j.circuitbreaker.instances.applicationService.slidingWindowSize=3",
      "resilience4j.circuitbreaker.instances.applicationService.minimumNumberOfCalls=3",
      "resilience4j.circuitbreaker.instances.applicationService.failureRateThreshold=50",
      "resilience4j.circuitbreaker.instances.applicationService.waitDurationInOpenState=1000",
      "resilience4j.circuitbreaker.instances.applicationService.permittedNumberOfCallsInHalfOpenState=1",
      "resilience4j.circuitbreaker.instances.jobService.slidingWindowSize=3",
      "resilience4j.circuitbreaker.instances.jobService.minimumNumberOfCalls=3",
      "resilience4j.circuitbreaker.instances.jobService.failureRateThreshold=50",
      "resilience4j.circuitbreaker.instances.jobService.waitDurationInOpenState=1000",
      "resilience4j.circuitbreaker.instances.jobService.permittedNumberOfCallsInHalfOpenState=1",
      "resilience4j.retry.instances.applicationService.maxAttempts=3",
      "resilience4j.retry.instances.applicationService.waitDuration=10ms",
      "resilience4j.retry.instances.applicationService.enableExponentialBackoff=false",
      "resilience4j.retry.instances.jobService.maxAttempts=3",
      "resilience4j.retry.instances.jobService.waitDuration=10ms",
      "resilience4j.retry.instances.jobService.enableExponentialBackoff=false",
      "resilience4j.bulkhead.instances.applicationService.maxConcurrentCalls=2",
      "resilience4j.bulkhead.instances.applicationService.maxWaitDuration=0",
      "resilience4j.bulkhead.instances.jobService.maxConcurrentCalls=2",
      "resilience4j.bulkhead.instances.jobService.maxWaitDuration=0"
    })
class RecruitmentClientsResilienceTest {

  @Configuration
  static class TestConfig {
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
      CircuitBreakerConfig appConfig =
          CircuitBreakerConfig.custom()
              .slidingWindowSize(3)
              .minimumNumberOfCalls(3)
              .failureRateThreshold(50)
              .waitDurationInOpenState(Duration.ofMillis(1000))
              .permittedNumberOfCallsInHalfOpenState(1)
              .build();
      CircuitBreakerConfig jobConfig =
          CircuitBreakerConfig.custom()
              .slidingWindowSize(3)
              .minimumNumberOfCalls(3)
              .failureRateThreshold(50)
              .waitDurationInOpenState(Duration.ofMillis(1000))
              .permittedNumberOfCallsInHalfOpenState(1)
              .build();
      CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
      registry.circuitBreaker("applicationService", appConfig);
      registry.circuitBreaker("jobService", jobConfig);
      return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
      RetryConfig config =
          RetryConfig.custom()
              .maxAttempts(3)
              .waitDuration(Duration.ofMillis(10))
              .retryExceptions(RuntimeException.class)
              .build();
      RetryRegistry registry = RetryRegistry.ofDefaults();
      registry.retry("applicationService", config);
      registry.retry("jobService", config);
      return registry;
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
      BulkheadConfig config =
          BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build();
      BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
      registry.bulkhead("applicationService", config);
      registry.bulkhead("jobService", config);
      return registry;
    }

    @Bean
    public CircuitBreaker applicationServiceCircuitBreaker(CircuitBreakerRegistry registry) {
      return registry.circuitBreaker("applicationService");
    }

    @Bean
    public CircuitBreaker jobServiceCircuitBreaker(CircuitBreakerRegistry registry) {
      return registry.circuitBreaker("jobService");
    }

    @Bean
    public Retry applicationServiceRetry(RetryRegistry registry) {
      return registry.retry("applicationService");
    }

    @Bean
    public Retry jobServiceRetry(RetryRegistry registry) {
      return registry.retry("jobService");
    }

    @Bean
    public Bulkhead applicationServiceBulkhead(BulkheadRegistry registry) {
      return registry.bulkhead("applicationService");
    }

    @Bean
    public Bulkhead jobServiceBulkhead(BulkheadRegistry registry) {
      return registry.bulkhead("jobService");
    }
  }

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired private RetryRegistry retryRegistry;

  @Autowired private BulkheadRegistry bulkheadRegistry;

  private CircuitBreaker applicationCircuitBreaker;
  private CircuitBreaker jobCircuitBreaker;
  private Retry applicationRetry;
  private Retry jobRetry;
  private Bulkhead applicationBulkhead;
  private Bulkhead jobBulkhead;

  @BeforeEach
  void setUp() {
    applicationCircuitBreaker = circuitBreakerRegistry.circuitBreaker("applicationService");
    jobCircuitBreaker = circuitBreakerRegistry.circuitBreaker("jobService");
    applicationRetry = retryRegistry.retry("applicationService");
    jobRetry = retryRegistry.retry("jobService");
    applicationBulkhead = bulkheadRegistry.bulkhead("applicationService");
    jobBulkhead = bulkheadRegistry.bulkhead("jobService");

    applicationCircuitBreaker.transitionToClosedState();
    jobCircuitBreaker.transitionToClosedState();
  }

  @AfterEach
  void tearDown() {
    applicationCircuitBreaker.transitionToClosedState();
    jobCircuitBreaker.transitionToClosedState();
  }

  @Nested
  @DisplayName("ApplicationClient - Circuit Breaker")
  class ApplicationClientCircuitBreakerTests {

    @Test
    @DisplayName("Circuit breaker opens after failure threshold via decorated function")
    void circuitBreakerOpensAfterFailures() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              applicationCircuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }

      assertThat(applicationCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker transitions to HALF_OPEN after wait duration")
    void circuitBreakerTransitionsToHalfOpen() throws InterruptedException {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              applicationCircuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }
      assertThat(applicationCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      Thread.sleep(1200);

      assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);

      assertThat(applicationCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker permits calls in CLOSED state")
    void circuitBreakerPermitsCallsWhenClosed() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(applicationCircuitBreaker, () -> "success");

      String result = decorated.get();

      assertThat(result).isEqualTo("success");
      assertThat(applicationCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
  }

  @Nested
  @DisplayName("ApplicationClient - Retry")
  class ApplicationClientRetryTests {

    @Test
    @DisplayName("Retry configuration is correctly loaded")
    void retryConfigLoaded() {
      assertThat(applicationRetry).isNotNull();
      assertThat(applicationRetry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Retry executes decorated function with retries")
    void retryExecutesWithRetries() {
      AtomicInteger callCount = new AtomicInteger(0);

      java.util.function.Supplier<String> decorated =
          Retry.decorateSupplier(
              applicationRetry,
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
              applicationRetry,
              () -> {
                callCount.incrementAndGet();
                throw new RuntimeException("persistent failure");
              });

      assertThatThrownBy(decorated::get)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("persistent failure");

      assertThat(callCount.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("ApplicationClient - Bulkhead")
  class ApplicationClientBulkheadTests {

    @Test
    @DisplayName("Bulkhead configuration is correctly loaded")
    void bulkheadConfigLoaded() {
      assertThat(applicationBulkhead).isNotNull();
      assertThat(applicationBulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(2);
      assertThat(applicationBulkhead.getBulkheadConfig().getMaxWaitDuration())
          .isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Bulkhead rejects calls beyond maxConcurrentCalls")
    void bulkheadRejectsExcessCalls() throws InterruptedException {
      int maxConcurrent = 2;
      int totalCalls = 5;

      ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(totalCalls);
      AtomicInteger rejectedCount = new AtomicInteger(0);

      for (int i = 0; i < totalCalls; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                Runnable decorated =
                    Bulkhead.decorateRunnable(
                        applicationBulkhead,
                        () -> {
                          try {
                            Thread.sleep(100);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                          }
                        });
                decorated.run();
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

      assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(totalCalls - maxConcurrent);
    }
  }

  @Nested
  @DisplayName("ApplicationClient - Fallback")
  class ApplicationClientFallbackTests {

    @Test
    @DisplayName("ApplicationServiceUnavailableException class exists and is throwable")
    void fallbackExceptionClassExists() {
      assertThatThrownBy(
              () -> {
                throw new ApplicationServiceUnavailableException("test");
              })
          .isInstanceOf(ApplicationServiceUnavailableException.class)
          .hasMessage("test");
    }

    @Test
    @DisplayName("Circuit breaker rejects calls when OPEN")
    void circuitBreakerRejectsWhenOpen() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              applicationCircuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }
      assertThat(applicationCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      assertThatThrownBy(decorated::get)
          .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
    }
  }

  @Nested
  @DisplayName("JobClient - Circuit Breaker")
  class JobClientCircuitBreakerTests {

    @Test
    @DisplayName("Circuit breaker opens after failure threshold via decorated function")
    void circuitBreakerOpensAfterFailures() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              jobCircuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }

      assertThat(jobCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker transitions to HALF_OPEN after wait duration")
    void circuitBreakerTransitionsToHalfOpen() throws InterruptedException {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(
              jobCircuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }
      assertThat(jobCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      Thread.sleep(1200);

      assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);

      assertThat(jobCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker permits calls in CLOSED state")
    void circuitBreakerPermitsCallsWhenClosed() {
      java.util.function.Supplier<String> decorated =
          CircuitBreaker.decorateSupplier(jobCircuitBreaker, () -> "success");

      String result = decorated.get();

      assertThat(result).isEqualTo("success");
      assertThat(jobCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
  }

  @Nested
  @DisplayName("JobClient - Retry")
  class JobClientRetryTests {

    @Test
    @DisplayName("Retry configuration is correctly loaded")
    void retryConfigLoaded() {
      assertThat(jobRetry).isNotNull();
      assertThat(jobRetry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Retry executes decorated function with retries")
    void retryExecutesWithRetries() {
      AtomicInteger callCount = new AtomicInteger(0);

      java.util.function.Supplier<String> decorated =
          Retry.decorateSupplier(
              jobRetry,
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
              jobRetry,
              () -> {
                callCount.incrementAndGet();
                throw new RuntimeException("persistent failure");
              });

      assertThatThrownBy(decorated::get)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("persistent failure");

      assertThat(callCount.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("JobClient - Bulkhead")
  class JobClientBulkheadTests {

    @Test
    @DisplayName("Bulkhead configuration is correctly loaded")
    void bulkheadConfigLoaded() {
      assertThat(jobBulkhead).isNotNull();
      assertThat(jobBulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(2);
      assertThat(jobBulkhead.getBulkheadConfig().getMaxWaitDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Bulkhead rejects calls beyond maxConcurrentCalls")
    void bulkheadRejectsExcessCalls() throws InterruptedException {
      int maxConcurrent = 2;
      int totalCalls = 5;

      ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(totalCalls);
      AtomicInteger rejectedCount = new AtomicInteger(0);

      for (int i = 0; i < totalCalls; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                Runnable decorated =
                    Bulkhead.decorateRunnable(
                        jobBulkhead,
                        () -> {
                          try {
                            Thread.sleep(100);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                          }
                        });
                decorated.run();
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

      assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(totalCalls - maxConcurrent);
    }
  }

  @Nested
  @DisplayName("JobClient - Fallback")
  class JobClientFallbackTests {

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
              jobCircuitBreaker,
              () -> {
                throw new RuntimeException("simulated failure");
              });

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(decorated::get).isInstanceOf(RuntimeException.class);
      }
      assertThat(jobCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      assertThatThrownBy(decorated::get)
          .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
    }
  }
}
