package com.smarthire.job.api.dto;

import com.smarthire.job.domain.Job;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * API representation of a {@link Job}. Records are the mandated DTO form (governance §3).
 * Controllers expose this, never the entity (enforced by ArchUnit), keeping the persistence model
 * decoupled from the wire contract.
 */
@Schema(name = "JobResponse", description = "Job posting response with details about the position")
public record JobResponse(
    @Schema(
            name = "jobId",
            description = "Unique identifier of the job posting",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,
    @Schema(
            name = "companyName",
            description = "Name of the company posting the job",
            example = "Tech Corp")
        String companyName,
    @Schema(name = "role", description = "Job role/title", example = "Senior Software Engineer")
        String role,
    @Schema(name = "location", description = "Job location", example = "New York, NY")
        String location,
    @Schema(name = "status", description = "Current status of the job posting", example = "OPEN")
        String status,
    @Schema(
            name = "ownerOrgId",
            description = "ID of the organization that owns this job posting",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID ownerOrgId) {

  public static JobResponse from(Job job) {
    return new JobResponse(
        job.getJobId(),
        job.getCompanyName(),
        job.getRole(),
        job.getLocation(),
        job.getStatus().name(),
        job.getOwnerOrgId());
  }
}
