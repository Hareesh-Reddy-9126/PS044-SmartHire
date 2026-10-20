package com.smarthire.recruitment.api.dto;

import com.smarthire.recruitment.domain.Recruitment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * API representation of a {@link Recruitment}. Records are the mandated DTO form (governance §3).
 *
 * <p>{@code ownerOrgId} is deliberately absent. It is the value every authorization decision is
 * made against, and the candidate who may read their own recruitment has no business learning the
 * organization's identifier; the fields that are here are already known to every caller allowed to
 * see the record at all.
 *
 * <p>Statuses are carried as strings rather than enums so the wire contract stays stable if the
 * internal enums are ever renamed or extended.
 */
@Schema(
    name = "RecruitmentResponse",
    description = "Recruitment process response with status information")
public record RecruitmentResponse(
    @Schema(
            name = "recruitmentId",
            description = "Unique identifier of the recruitment process",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID recruitmentId,
    @Schema(
            name = "applicationId",
            description = "ID of the application this recruitment tracks",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID applicationId,
    @Schema(
            name = "jobId",
            description = "ID of the job posting",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,
    @Schema(
            name = "candidateId",
            description = "ID of the candidate",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID candidateId,
    @Schema(
            name = "interviewStatus",
            description = "Current interview status",
            example = "SCHEDULED")
        String interviewStatus,
    @Schema(name = "finalStatus", description = "Final outcome status", example = "HIRED")
        String finalStatus) {

  public static RecruitmentResponse from(Recruitment recruitment) {
    return new RecruitmentResponse(
        recruitment.getRecruitmentId(),
        recruitment.getApplicationId(),
        recruitment.getJobId(),
        recruitment.getCandidateId(),
        recruitment.getInterviewStatus().name(),
        recruitment.getFinalStatus().name());
  }
}
