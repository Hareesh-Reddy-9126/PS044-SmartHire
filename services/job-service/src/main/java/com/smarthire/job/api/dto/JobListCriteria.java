package com.smarthire.job.api.dto;

import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.service.JobQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query parameters accepted by {@code GET /api/v1/jobs}. All are optional.
 *
 * <p>Values are normalised on construction so the transport cannot push a hostile page request into
 * the database: a negative page becomes the first page, and the page size is clamped to {@link
 * #MAX_SIZE} so {@code ?size=1000000} cannot be used to pull the whole table in one request.
 *
 * <p>Note what is <em>absent</em>: there is no {@code ownerOrgId} parameter. Visibility is derived
 * from the verified JWT inside the service, so no query string can widen what the caller sees.
 *
 * <p>Deliberately not a record, and deliberately free of Spring Data types. The status filter is
 * held as the domain enum — Spring converts the query parameter and rejects an unknown value with
 * 400 before this class is reached — but a record would generate a {@code status()} accessor
 * returning it, and the ArchUnit rule {@code controllers_return_dtos_not_entities} (governance §46)
 * forbids any {@code ..api..} method from returning a type in a {@code ..domain..} package. That
 * also rules out handing back a {@code Pageable}, so the normalised {@link #page()} and {@link
 * #size()} are exposed as plain ints and the controller assembles the page request.
 */
@Schema(
    name = "JobListCriteria",
    description = "Query parameters for listing jobs with pagination and filtering")
public final class JobListCriteria {

  /** Page size used when the caller does not ask for one. */
  public static final int DEFAULT_SIZE = 20;

  /** Hard upper bound on the page size a caller may request. */
  public static final int MAX_SIZE = 100;

  @Schema(name = "status", description = "Filter by job status")
  private final JobStatus status;

  @Schema(name = "location", description = "Filter by job location", example = "New York, NY")
  private final String location;

  @Schema(
      name = "q",
      description = "Text search for company name or role",
      example = "software engineer")
  private final String q;

  @Schema(name = "page", description = "Zero-based page index, default is 0")
  private final int page;

  @Schema(name = "size", description = "Page size, default is 20, max is 100")
  private final int size;

  public JobListCriteria(JobStatus status, String location, String q, int page, int size) {
    this.status = status;
    this.location = location;
    this.q = q;
    this.page = Math.max(0, page);
    this.size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
  }

  /** Zero-based page index, never negative. */
  public int page() {
    return page;
  }

  /** Requested page size, defaulted and clamped to {@link #MAX_SIZE}. */
  public int size() {
    return size;
  }

  /** The domain-facing filter, without any transport concerns. */
  public JobQuery toQuery() {
    return new JobQuery(status, location, q);
  }
}
