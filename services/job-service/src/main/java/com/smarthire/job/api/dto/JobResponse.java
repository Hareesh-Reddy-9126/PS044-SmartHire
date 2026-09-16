package com.smarthire.job.api.dto;

import com.smarthire.job.domain.Job;
import java.util.UUID;

/**
 * API representation of a {@link Job}. Records are the mandated DTO form (governance §3).
 * Controllers expose this, never the entity (enforced by ArchUnit), keeping the persistence model
 * decoupled from the wire contract.
 */
public record JobResponse(
    UUID jobId, String companyName, String role, String location, String status) {

  public static JobResponse from(Job job) {
    return new JobResponse(
        job.getJobId(),
        job.getCompanyName(),
        job.getRole(),
        job.getLocation(),
        job.getStatus().name());
  }
}
