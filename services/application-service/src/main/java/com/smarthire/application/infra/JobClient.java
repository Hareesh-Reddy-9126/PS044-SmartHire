package com.smarthire.application.infra;

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
  JobResponse getJob(@PathVariable("jobId") UUID jobId);

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
}
