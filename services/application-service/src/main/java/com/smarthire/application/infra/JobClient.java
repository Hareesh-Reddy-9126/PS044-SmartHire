package com.smarthire.application.infra;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for Job Service.
 *
 * <p>Calls {@code GET /api/v1/jobs/{jobId}} to validate that a job exists and is OPEN before
 * creating an application. The client is a read-only validation step — no mutation of Job Service
 * data occurs from Application Service.
 *
 * <p>Uses {@code @FeignClient(name = "JOB-SERVICE")} so the call is load-balanced through Eureka.
 * Protected by Resilience4j circuit breaker, retry, and bulkhead for fault tolerance.
 */
@FeignClient(name = "JOB-SERVICE")
public interface JobClient {

  /**
   * Reads a job by ID.
   *
   * @param jobId the job UUID
   * @return job details including status and ownerOrgId, or 404 if not found
   */
  @GetMapping("/api/v1/jobs/{jobId}")
  @CircuitBreaker(name = "jobService", fallbackMethod = "getJobFallback")
  @Retry(name = "jobService")
  @Bulkhead(name = "jobService")
  JobResponse getJob(@PathVariable("jobId") UUID jobId);

  /**
   * Fallback for circuit breaker / bulkhead when Job Service is unavailable. Returns a problem
   * detail response indicating the downstream dependency is unavailable.
   *
   * <p>A 404 is an answer, not an outage: it is rethrown unchanged so callers can map it to their
   * own 404 instead of reporting the job service as unavailable.
   */
  default JobResponse getJobFallback(UUID jobId, Exception ex) {
    if (ex instanceof feign.FeignException.NotFound notFound) {
      throw notFound;
    }
    throw new JobServiceUnavailableException("Job service unavailable: " + ex.getMessage());
  }

  /**
   * Response DTO matching Job Service's {@code JobResponse}. Only the fields needed for validation
   * are included.
   */
  record JobResponse(
      UUID jobId,
      String companyName,
      String role,
      String location,
      String status,
      UUID ownerOrgId) {

    public boolean isOpen() {
      return "OPEN".equals(status);
    }
  }

  /**
   * Exception thrown when Job Service is unavailable (circuit breaker open or bulkhead full).
   * Mapped to RFC 9457 problem detail by the global exception handler.
   */
  class JobServiceUnavailableException extends RuntimeException {
    public JobServiceUnavailableException(String message) {
      super(message);
    }
  }
}
