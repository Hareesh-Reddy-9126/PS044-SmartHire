package com.smarthire.recruitment.infra;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for application-service, resolved through Eureka by service id (ADR:
 * inter-service comm is OpenFeign over discovery).
 *
 * <p>Recruitment Service reads an application at shortlist time, and updates the application status
 * when the recruitment final status changes. Uses {@code @FeignClient(name =
 * "APPLICATION-SERVICE")} so calls are load-balanced through Eureka. Protected by Resilience4j
 * circuit breaker, retry, and bulkhead for fault tolerance.
 */
@FeignClient(name = "APPLICATION-SERVICE", url = "${application-service.url:}")
public interface ApplicationClient {

  /**
   * Reads one application.
   *
   * @param applicationId the application UUID
   * @return the application, or a {@code FeignException.NotFound} when it does not exist
   */
  @GetMapping("/api/v1/applications/{applicationId}")
  @CircuitBreaker(name = "applicationService", fallbackMethod = "getApplicationFallback")
  @Retry(name = "applicationService")
  @Bulkhead(name = "applicationService")
  ApplicationResponse getApplication(@PathVariable("applicationId") UUID applicationId);

  /**
   * Updates an application's status (recruiter-driven transition).
   *
   * @param applicationId the application UUID
   * @param request the status update request
   * @return the updated application
   */
  @PatchMapping("/api/v1/applications/{applicationId}/status")
  @CircuitBreaker(name = "applicationService", fallbackMethod = "updateStatusFallback")
  @Bulkhead(name = "applicationService")
  ApplicationResponse updateStatus(
      @PathVariable("applicationId") UUID applicationId, @RequestBody StatusUpdateRequest request);

  /** Fallback for circuit breaker / bulkhead when Application Service is unavailable. */
  default ApplicationResponse getApplicationFallback(UUID applicationId, Exception ex) {
    throw new ApplicationServiceUnavailableException(
        "Application service unavailable: " + ex.getMessage());
  }

  /** Fallback for circuit breaker / bulkhead when Application Service is unavailable on update. */
  default ApplicationResponse updateStatusFallback(
      UUID applicationId, StatusUpdateRequest request, Exception ex) {
    throw new ApplicationServiceUnavailableException(
        "Application service unavailable: " + ex.getMessage());
  }

  /** Mirror of application-service's {@code ApplicationResponse}. */
  record ApplicationResponse(
      UUID applicationId, UUID jobId, UUID candidateId, String resumeLink, String status) {}

  /** Request to update application status. */
  record StatusUpdateRequest(String status) {}

  /**
   * Exception thrown when Application Service is unavailable (circuit breaker open or bulkhead
   * full). Mapped to RFC 9457 problem detail by the global exception handler.
   */
  class ApplicationServiceUnavailableException extends RuntimeException {
    public ApplicationServiceUnavailableException(String message) {
      super(message);
    }
  }
}
