package com.smarthire.job.api;

import com.smarthire.job.api.dto.JobCreateRequest;
import com.smarthire.job.api.dto.JobListCriteria;
import com.smarthire.job.api.dto.JobResponse;
import com.smarthire.job.api.dto.JobUpdateRequest;
import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.security.JobPrincipal;
import com.smarthire.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Job management endpoints (Inc 2). Versioned base path {@code /api/v1} from day one (governance
 * §11).
 *
 * <p>Every method derives the caller's identity from {@code @AuthenticationPrincipal Jwt} — the
 * token the resource-server filter already verified. The request body, query string and headers are
 * never consulted for ownership, so a caller cannot claim an organization they do not hold. The
 * reads take the principal optionally ({@code null} for anonymous callers, which the filter chain
 * permits); the writes are gated by {@code @PreAuthorize("hasRole('RECRUITER')")}, which also
 * excludes ADMIN — a platform-operations role, not a recruitment actor (ADR-0010). Ownership itself
 * is enforced one layer deeper, in {@code JobService}.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Job Management", description = "Endpoints for managing job postings")
public class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  /**
   * Lists the jobs the caller may see: OPEN postings for everyone, plus the caller's own DRAFT and
   * CLOSED postings when they are a recruiter. Supports {@code page}, {@code size}, {@code status},
   * {@code location} and a free-text {@code q} over company name and role.
   *
   * <p>Returns the Spring Data page envelope ({@code content} plus a {@code page} block) rather
   * than a bare array, because a pageable listing has to report total size and page position
   * somewhere.
   */
  @Operation(
      summary = "List jobs with filtering and pagination",
      description =
          "Returns a paginated list of jobs visible to the caller. OPEN jobs are visible to everyone (including unauthenticated users). DRAFT and CLOSED jobs are visible only to recruiters of the owning organization. Supports filtering by status, location, and text search.",
      parameters = {
        @Parameter(name = "status", description = "Filter by job status", example = "OPEN"),
        @Parameter(name = "location", description = "Filter by job location", example = "New York"),
        @Parameter(
            name = "q",
            description = "Free-text search over company name and role",
            example = "software engineer"),
        @Parameter(
            name = "page",
            description = "Zero-based page index (default: 0)",
            example = "0"),
        @Parameter(name = "size", description = "Page size (default: 20, max: 100)", example = "20")
      })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Jobs retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PagedModel.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping
  public PagedModel<JobResponse> listJobs(
      @RequestParam(required = false) JobStatus status,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    JobListCriteria criteria = new JobListCriteria(status, location, q, page, size);
    // Sorted by jobId so paging is stable across requests.
    Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), Sort.by("jobId"));
    return new PagedModel<>(
        jobService
            .list(criteria.toQuery(), principal.orgId(), principal.isRecruiter(), pageable)
            .map(JobResponse::from));
  }

  /**
   * Reads one job. OPEN jobs are public; DRAFT and CLOSED jobs are visible only to a recruiter of
   * the owning organization, and are reported as 404 to anyone else so identifiers cannot be
   * probed.
   */
  @Operation(
      summary = "Get a job by ID",
      description =
          "Returns a single job. OPEN jobs are accessible to everyone; DRAFT and CLOSED jobs require an authenticated recruiter from the owning organization. Unknown or inaccessible IDs return 404.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/{jobId}")
  public ResponseEntity<JobResponse> getJob(
      @PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    return ResponseEntity.ok(
        JobResponse.from(jobService.get(jobId, principal.orgId(), principal.isRecruiter())));
  }

  /** Creates a job in DRAFT, owned by the organization named in the caller's verified token. */
  @Operation(
      summary = "Create a new job posting",
      description =
          "Creates a job in DRAFT status owned by the organization from the caller's JWT. Requires RECRUITER role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Job created successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<JobResponse> createJob(
      @Valid @RequestBody JobCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    JobResponse job =
        JobResponse.from(
            jobService.create(
                request.companyName(), request.role(), request.location(), principal.orgId()));
    return ResponseEntity.created(URI.create("/api/v1/jobs/" + job.jobId())).body(job);
  }

  /**
   * Edits a job's descriptive fields. Status is not editable here — it moves only through the
   * lifecycle endpoints below — and ownership is not editable at all.
   */
  @Operation(
      summary = "Update a job posting",
      description =
          "Updates the companyName, role, and location of an existing job. Status changes are not allowed here and must use the lifecycle endpoints. Requires RECRUITER role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job updated successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - not owned by caller's organization",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @PutMapping("/{jobId}")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<JobResponse> updateJob(
      @PathVariable UUID jobId,
      @Valid @RequestBody JobUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    return ResponseEntity.ok(
        JobResponse.from(
            jobService.update(
                jobId,
                request.companyName(),
                request.role(),
                request.location(),
                principal.orgId())));
  }

  /** Publishes a draft: {@code DRAFT → OPEN}. */
  @Operation(
      summary = "Open a draft job",
      description =
          "Transitions a job from DRAFT to OPEN status, making it visible to candidates. Requires RECRUITER role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job opened successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - illegal state transition (not DRAFT)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{jobId}/open")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<JobResponse> openJob(
      @PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    return ResponseEntity.ok(JobResponse.from(jobService.open(jobId, principal.orgId())));
  }

  /** Stops accepting applications: {@code OPEN → CLOSED}. */
  @Operation(
      summary = "Close a job posting",
      description =
          "Transitions a job from OPEN to CLOSED status, stopping new applications. Requires RECRUITER role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job closed successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - illegal state transition (not OPEN)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{jobId}/close")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<JobResponse> closeJob(
      @PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    return ResponseEntity.ok(JobResponse.from(jobService.close(jobId, principal.orgId())));
  }

  /** Re-publishes a closed posting: {@code CLOSED → OPEN}. */
  @Operation(
      summary = "Reopen a closed job",
      description =
          "Transitions a job from CLOSED to OPEN status, re-accepting applications. Requires RECRUITER role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job reopened successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - illegal state transition (not CLOSED)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{jobId}/reopen")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<JobResponse> reopenJob(
      @PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
    JobPrincipal principal = JobPrincipal.from(jwt);
    return ResponseEntity.ok(JobResponse.from(jobService.reopen(jobId, principal.orgId())));
  }
}
