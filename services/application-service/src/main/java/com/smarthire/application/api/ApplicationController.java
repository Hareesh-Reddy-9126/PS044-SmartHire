package com.smarthire.application.api;

import com.smarthire.application.api.dto.ApplicationCreateRequest;
import com.smarthire.application.api.dto.ApplicationResponse;
import com.smarthire.application.api.dto.ApplicationStatusUpdateRequest;
import com.smarthire.application.security.ApplicationPrincipal;
import com.smarthire.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application management endpoints. Versioned base path {@code /api/v1} from day one (governance
 * §11).
 *
 * <p>Every method derives the caller's identity from {@code @AuthenticationPrincipal Jwt} — the
 * token the resource-server filter already verified. The request body, query string and headers are
 * never consulted for ownership, so a caller cannot claim an identity they do not hold.
 */
@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Application Management", description = "Endpoints for managing job applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

  private final ApplicationService applicationService;

  public ApplicationController(ApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  /** Lists only the applications owned by the authenticated candidate. */
  @Operation(
      summary = "List the current candidate's applications",
      description =
          "Returns a paginated application list scoped to the candidate identity in the verified JWT.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Applications retrieved successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping
  @PreAuthorize("hasRole('CANDIDATE')")
  public PagedModel<ApplicationResponse> listApplications(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal Jwt jwt) {
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    Pageable pageable =
        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("applicationId"));
    return new PagedModel<>(
        applicationService
            .listForCandidate(principal.candidateId(), pageable)
            .map(ApplicationResponse::from));
  }

  /**
   * Lists the applications submitted to one of the caller's jobs.
   *
   * <p>This is the recruiter's entry point into the application workflow: without it the only way
   * to reach an application would be to already know its identifier. The job is read from Job
   * Service and the caller's organization must own it; anyone else receives 404.
   */
  @Operation(
      summary = "List applications for a job",
      description =
          "Returns a paginated list of applications submitted to the given job. Requires RECRUITER (owning organization) or ADMIN.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Applications retrieved successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER or ADMIN role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found or not owned by the caller's organization",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/by-job/{jobId}")
  @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
  public PagedModel<ApplicationResponse> listApplicationsForJob(
      @PathVariable UUID jobId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal Jwt jwt) {
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    Pageable pageable =
        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("applicationId"));
    return new PagedModel<>(
        applicationService
            .listForJob(jobId, principal.orgId(), principal.isAdmin(), pageable)
            .map(ApplicationResponse::from));
  }

  /**
   * Submits a new application.
   *
   * <p>Candidate must be authenticated. The {@code candidateId} comes from the verified JWT {@code
   * sub} claim — never from the request body. The requested job is validated via Job Service to
   * ensure it exists and is OPEN.
   */
  @Operation(
      summary = "Submit a job application",
      description =
          "Creates a new job application for an authenticated candidate. Requires CANDIDATE role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Application submitted successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApplicationResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed or job not open",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description =
                "Candidate already has an active application for this job (withdrawn and rejected applications do not block a new one)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @PostMapping
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<ApplicationResponse> createApplication(
      @Valid @RequestBody ApplicationCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    var application =
        applicationService.create(request.jobId(), request.resumeLink(), principal.candidateId());
    return ResponseEntity.created(
            URI.create("/api/v1/applications/" + application.getApplicationId()))
        .body(ApplicationResponse.from(application));
  }

  /**
   * Reads one application.
   *
   * <p>Candidate can view their own application. Recruiter can view applications for their
   * organization's jobs. ADMIN can view any application. Others receive 404 to prevent probing.
   */
  @Operation(
      summary = "Get an application by ID",
      description =
          "Returns a single application. Candidates can view their own applications, recruiters can view applications for their organization's jobs, and admins can view any application. Others receive 404.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Application retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApplicationResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Application not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/{applicationId}")
  public ResponseEntity<ApplicationResponse> getApplication(
      @PathVariable UUID applicationId, @AuthenticationPrincipal Jwt jwt) {
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    var application =
        applicationService.get(
            applicationId, principal.candidateId(), principal.orgId(), principal.isAdmin());
    return ResponseEntity.ok(ApplicationResponse.from(application));
  }

  /**
   * Candidate withdraws their application.
   *
   * <p>Only the owning candidate may withdraw. Transitions to WITHDRAWN state. Illegal transitions
   * (e.g., withdrawing a HIRED application) are rejected with 409.
   */
  @Operation(
      summary = "Withdraw an application",
      description =
          "Withdraws an application submitted by the authenticated candidate. Transitions the application to WITHDRAWN state.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Application withdrawn successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApplicationResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role and application ownership",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Application not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - illegal state transition",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @PostMapping("/{applicationId}/withdraw")
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<ApplicationResponse> withdrawApplication(
      @PathVariable UUID applicationId, @AuthenticationPrincipal Jwt jwt) {
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    var application = applicationService.withdraw(applicationId, principal.candidateId());
    return ResponseEntity.ok(ApplicationResponse.from(application));
  }

  /**
   * Updates an application's status (recruiter-driven transitions).
   *
   * <p>Allowed transitions:
   *
   * <ul>
   *   <li>APPLIED → UNDER_REVIEW
   *   <li>UNDER_REVIEW → SHORTLISTED
   *   <li>SHORTLISTED → HIRED
   *   <li>SHORTLISTED → REJECTED
   * </ul>
   *
   * <p>Authorization:
   *
   * <ul>
   *   <li>RECRUITER must belong to the organization that owns the job
   *   <li>ADMIN can update any application
   *   <li>CANDIDATE cannot update status (use withdraw instead)
   * </ul>
   *
   * <p>Returns the updated application representation.
   */
  @Operation(
      summary = "Update application status",
      description = "Updates the status of an application (recruiter-driven transitions).")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Application status updated successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApplicationResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires RECRUITER or ADMIN role and authorization",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Application not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - illegal state transition",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @PatchMapping("/{applicationId}/status")
  @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
  public ResponseEntity<ApplicationResponse> updateApplicationStatus(
      @PathVariable UUID applicationId,
      @Valid @RequestBody ApplicationStatusUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {

    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    var application =
        applicationService.updateStatus(
            applicationId, request.status(), principal.orgId(), principal.isAdmin());
    return ResponseEntity.ok(ApplicationResponse.from(application));
  }
}
