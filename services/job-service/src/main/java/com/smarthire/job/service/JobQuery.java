package com.smarthire.job.service;

import com.smarthire.job.domain.JobStatus;

/**
 * Filter criteria for a job listing, expressed in domain terms. Deliberately declared in the
 * service package rather than reusing the API request record: the service layer must not depend on
 * {@code ..api..} (ArchUnit rule {@code service_does_not_depend_on_api}, governance §46), and
 * keeping the HTTP-shaped record at the edge lets the transport evolve without touching the query
 * model.
 *
 * <p>All fields are optional; a {@code null} or blank value means "no constraint on this
 * attribute". Visibility is <em>not</em> part of this record — it is derived from the verified JWT
 * by {@link JobService} and can never be widened by a query parameter.
 */
public record JobQuery(JobStatus status, String location, String q) {

  /** An unfiltered query — every job the caller is allowed to see. */
  public static JobQuery unfiltered() {
    return new JobQuery(null, null, null);
  }
}
