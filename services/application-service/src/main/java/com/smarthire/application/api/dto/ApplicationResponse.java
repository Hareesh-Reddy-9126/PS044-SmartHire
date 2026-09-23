package com.smarthire.application.api.dto;

import com.smarthire.application.domain.Application;
import java.util.UUID;

/**
 * API representation of an {@link Application}. Records are the mandated DTO form (governance §3).
 */
public record ApplicationResponse(
    UUID applicationId, UUID jobId, UUID candidateId, String resumeLink, String status) {

  public static ApplicationResponse from(Application application) {
    return new ApplicationResponse(
        application.getApplicationId(),
        application.getJobId(),
        application.getCandidateId(),
        application.getResumeLink(),
        application.getStatus().name());
  }
}
