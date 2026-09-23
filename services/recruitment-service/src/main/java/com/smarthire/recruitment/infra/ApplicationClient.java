package com.smarthire.recruitment.infra;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for application-service, resolved through Eureka by service id (ADR:
 * inter-service comm is OpenFeign over discovery).
 *
 * <p>Read-only. Recruitment Service never mutates application-service data; it reads an application
 * once, at shortlist time, to learn which job and which candidate the recruitment is for. Those two
 * facts are then stored on the recruitment row, so no later request re-enters this client — the
 * authorization path for reads and status changes is a purely local column comparison.
 *
 * <p>{@code url} is intentionally an empty-defaulted placeholder. Left empty, Spring Cloud
 * OpenFeign takes the load-balanced path and resolves {@code APPLICATION-SERVICE} via Eureka; the
 * property exists only so a test or a non-discovery deployment can pin an absolute {@code
 * http://...} address.
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
  ApplicationResponse getApplication(@PathVariable("applicationId") UUID applicationId);

  /**
   * Mirror of application-service's {@code ApplicationResponse}. Note that it carries no owning
   * organization — that is why the shortlist path follows {@link #getApplication} with a {@link
   * JobClient} call on {@code jobId} to resolve ownership.
   */
  record ApplicationResponse(
      UUID applicationId, UUID jobId, UUID candidateId, String resumeLink, String status) {}
}
