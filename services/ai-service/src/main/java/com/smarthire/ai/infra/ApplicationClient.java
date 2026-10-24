package com.smarthire.ai.infra;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for Application Service.
 *
 * <p>Calls {@code GET /api/v1/applications/{applicationId}} to read application details for match
 * scoring and candidate summaries. Protected by Resilience4j circuit breaker, retry, and bulkhead
 * for fault tolerance.
 */
@FeignClient(name = "APPLICATION-SERVICE")
public interface ApplicationClient {

  /**
   * Reads an application by ID.
   *
   * @param applicationId the application UUID
   * @return application details
   */
  @GetMapping("/api/v1/applications/{applicationId}")
  @CircuitBreaker(name = "applicationService", fallbackMethod = "getApplicationFallback")
  @Retry(name = "applicationService")
  @Bulkhead(name = "applicationService")
  ApplicationResponse getApplication(@PathVariable("applicationId") UUID applicationId);

  /** Fallback for circuit breaker / bulkhead when Application Service is unavailable. */
  default ApplicationResponse getApplicationFallback(UUID applicationId, Exception ex) {
    throw new ApplicationServiceUnavailableException(
        "Application service unavailable: " + ex.getMessage());
  }

  /** Mirror of application-service's {@code ApplicationResponse}. */
  record ApplicationResponse(
      UUID applicationId, UUID jobId, UUID candidateId, String resumeLink, String status) {}

  /** Exception thrown when Application Service is unavailable. */
  class ApplicationServiceUnavailableException extends RuntimeException {
    public ApplicationServiceUnavailableException(String message) {
      super(message);
    }
  }
}
