package com.smarthire.ai.infra;

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
 * <p>Calls {@code GET /api/v1/jobs/{jobId}} to read job details for match scoring. Protected by
 * Resilience4j circuit breaker, retry, and bulkhead for fault tolerance.
 */
@FeignClient(name = "JOB-SERVICE")
public interface JobClient {

  /**
   * Reads a job by ID.
   *
   * @param jobId the job UUID
   * @return job details including status and ownerOrgId
   */
  @GetMapping("/api/v1/jobs/{jobId}")
  @CircuitBreaker(name = "jobService", fallbackMethod = "getJobFallback")
  @Retry(name = "jobService")
  @Bulkhead(name = "jobService")
  JobResponse getJob(@PathVariable("jobId") UUID jobId);

  /**
   * Fallback for circuit breaker / bulkhead when Job Service is unavailable.
   */
  default JobResponse getJobFallback(UUID jobId, Exception ex) {
    throw new JobServiceUnavailableException("Job service unavailable: " + ex.getMessage());
  }

  /** Mirror of job-service's {@code JobResponse}. */
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

  /** Exception thrown when Job Service is unavailable. */
  class JobServiceUnavailableException extends RuntimeException {
    public JobServiceUnavailableException(String message) {
      super(message);
    }
  }
}