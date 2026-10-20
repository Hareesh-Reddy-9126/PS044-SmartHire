package com.smarthire.application.api.dto;

import com.smarthire.application.domain.Application;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * API representation of an {@link Application}. Records are the mandated DTO form (governance §3).
 */
@Schema(
    name = "ApplicationResponse",
    description = "Job application response with application details")
public record ApplicationResponse(
    @Schema(
            name = "applicationId",
            description = "Unique identifier of the application",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID applicationId,
    @Schema(
            name = "jobId",
            description = "ID of the job the application is for",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,
    @Schema(
            name = "candidateId",
            description = "ID of the candidate who submitted the application",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID candidateId,
    @Schema(
            name = "resumeLink",
            description = "Link to the candidate's resume",
            example = "https://example.com/resume.pdf")
        String resumeLink,
    @Schema(name = "status", description = "Current status of the application", example = "APPLIED")
        String status) {

  public static ApplicationResponse from(Application application) {
    return new ApplicationResponse(
        application.getApplicationId(),
        application.getJobId(),
        application.getCandidateId(),
        application.getResumeLink(),
        application.getStatus().name());
  }
}
