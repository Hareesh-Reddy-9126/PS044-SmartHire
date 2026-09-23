package com.smarthire.recruitment.api.dto;

import com.smarthire.recruitment.domain.Recruitment;
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
public record RecruitmentResponse(
    UUID recruitmentId,
    UUID applicationId,
    UUID jobId,
    UUID candidateId,
    String interviewStatus,
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
