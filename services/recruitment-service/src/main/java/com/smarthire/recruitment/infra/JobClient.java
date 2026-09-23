package com.smarthire.recruitment.infra;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for job-service, resolved through Eureka by service id.
 *
 * <p>Read-only, and used on exactly one path: shortlisting. An application names a job but not the
 * organization that owns it, so the owning organization is read from the job here, checked against
 * the recruiter's verified {@code orgId} claim, and then stored on the recruitment row. Job
 * ownership is fixed at creation in job-service (no endpoint exposes it for update), so the stored
 * copy cannot go stale.
 *
 * <p>{@code url} is intentionally an empty-defaulted placeholder — see {@link ApplicationClient}.
 */
@FeignClient(name = "JOB-SERVICE", url = "${job-service.url:}")
public interface JobClient {

  /**
   * Reads one job.
   *
   * @param jobId the job UUID
   * @return the job, or a {@code FeignException.NotFound} when it does not exist
   */
  @GetMapping("/api/v1/jobs/{jobId}")
  JobResponse getJob(@PathVariable("jobId") UUID jobId);

  /** Mirror of job-service's {@code JobResponse}; only {@code ownerOrgId} is actually consulted. */
  record JobResponse(
      UUID jobId,
      String companyName,
      String role,
      String location,
      String status,
      UUID ownerOrgId) {}
}
