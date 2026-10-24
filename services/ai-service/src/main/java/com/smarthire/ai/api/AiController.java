package com.smarthire.ai.api;

import com.smarthire.ai.api.dto.CandidateSummaryRequest;
import com.smarthire.ai.api.dto.CandidateSummaryResponse;
import com.smarthire.ai.api.dto.JdAssistRequest;
import com.smarthire.ai.api.dto.JdAssistResponse;
import com.smarthire.ai.api.dto.MatchScoreRequest;
import com.smarthire.ai.api.dto.MatchScoreResponse;
import com.smarthire.ai.api.dto.ResumeParseRequest;
import com.smarthire.ai.api.dto.ResumeParseResponse;
import com.smarthire.ai.service.AiMatchingService;
@RestController
  @RequestMapping("/api/v1/ai")
  @Tag(
      name = "AI-Assisted Recruitment",
      description = "Advisory AI features for recruitment workflows")
  @SecurityRequirement(name = "bearerAuth")
  public class AiController {

    private final AiMatchingService aiMatchingService;

    public AiController(AiMatchingService aiMatchingService) {
      this.aiMatchingService = aiMatchingService;
    }

    @Operation(
        summary = "Health check",
        description = "Returns the health status of the AI service")
    @ApiResponses(
        value = {
          @ApiResponse(responseCode = "200", description = "Service healthy"),
          @ApiResponse(responseCode = "503", description = "Service unavailable")
        })
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('RECRUITER', 'CANDIDATE', 'ADMIN')")
    public ResponseEntity<Map<String, String>> health() {
      return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**

/**
 * AI-assisted recruitment endpoints. All outputs are advisory only — human-in-the-loop required for
 * any recruitment decision (ADR-0015). Features gated by {@code smarthire.ai.enabled}.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(
    name = "AI-Assisted Recruitment",
    description = "Advisory AI features for recruitment workflows")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

  private final AiMatchingService aiMatchingService;

  public AiController(AiMatchingService aiMatchingService) {
    this.aiMatchingService = aiMatchingService;
  }

  /**
   * Computes a candidate–job match score with rationale.
   *
   * <p>Recruiter must belong to the organization that owns the job. Returns 404 if the
   * application/job doesn't exist or belongs to another organization (prevents probing).
   */
  @Operation(
      summary = "Compute candidate–job match score",
      description =
          "Returns an AI-generated match score (0-100) with rationale for the given application and job. "
              + "Advisory only — recruiter must manually shortlist.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Match score computed",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MatchScoreResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden — requires RECRUITER role and organization ownership",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "404",
            description = "Application or job not found, or not owned by caller's organization",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "503",
            description = "AI service unavailable",
            content = @Content(mediaType = "application/problem+json"))
      })
  @PostMapping("/match-score")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<MatchScoreResponse> computeMatchScore(
      @Valid @RequestBody MatchScoreRequest request, @AuthenticationPrincipal Jwt jwt) {

    UUID recruiterOrgId = getOrgId(jwt);
    MatchScoreResponse response = aiMatchingService.computeMatchScore(request, recruiterOrgId);
    return ResponseEntity.ok(response);
  }

  /**
   * Generates a structured candidate summary.
   *
   * <p>Recruiter must belong to the organization that owns the job linked to the application.
   */
  @Operation(
      summary = "Generate candidate summary",
      description =
          "Returns an AI-generated structured summary of the candidate's profile. Advisory only.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Candidate summary generated",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CandidateSummaryResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "503",
            description = "AI service unavailable",
            content = @Content(mediaType = "application/problem+json"))
      })
  @PostMapping("/candidate-summary")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<CandidateSummaryResponse> generateCandidateSummary(
      @Valid @RequestBody CandidateSummaryRequest request, @AuthenticationPrincipal Jwt jwt) {

    UUID recruiterOrgId = getOrgId(jwt);
    CandidateSummaryResponse response =
        aiMatchingService.generateCandidateSummary(request, recruiterOrgId);
    return ResponseEntity.ok(response);
  }

  /**
   * Assists in drafting or improving a job description.
   *
   * <p>Any authenticated RECRUITER or ADMIN may call this. No organization ownership check needed
   * as this is a drafting assistant.
   */
  @Operation(
      summary = "Assist job description drafting",
      description =
          "Returns an AI-improved version of a draft job description. Advisory only — recruiter reviews before publishing.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "JD suggestion generated",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = JdAssistResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden — requires RECRUITER or ADMIN role",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "503",
            description = "AI service unavailable",
            content = @Content(mediaType = "application/problem+json"))
      })
  @PostMapping("/jd-assist")
  @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
  public ResponseEntity<JdAssistResponse> assistJd(
      @Valid @RequestBody JdAssistRequest request, @AuthenticationPrincipal Jwt jwt) {

    UUID recruiterOrgId = getOrgId(jwt); // For audit
    JdAssistResponse response = aiMatchingService.assistJd(request, recruiterOrgId);
    return ResponseEntity.ok(response);
  }

  /**
   * Parses a resume document into structured fields.
   *
   * <p>Candidate can parse their own documents. Recruiter can parse documents for their
   * organization's candidates. ADMIN can parse any document.
   */
  @Operation(
      summary = "Parse resume document",
      description =
          "Returns AI-parsed structured fields from a resume document. Candidate owns their documents; "
              + "recruiter accesses organization's candidates' documents.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Resume parsed",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResumeParseResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden — not document owner",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "503",
            description = "AI service unavailable",
            content = @Content(mediaType = "application/problem+json"))
      })
  @PostMapping("/resume-parse")
  @PreAuthorize("hasAnyRole('CANDIDATE', 'RECRUITER', 'ADMIN')")
  public ResponseEntity<ResumeParseResponse> parseResume(
      @Valid @RequestBody ResumeParseRequest request, @AuthenticationPrincipal Jwt jwt) {

    UUID actorId = UUID.fromString(jwt.getSubject());
    boolean isCandidate = jwt.getClaimAsStringList("roles").contains("CANDIDATE");
    ResumeParseResponse response = aiMatchingService.parseResume(request, actorId, isCandidate);
    return ResponseEntity.ok(response);
  }

  /**
   * Retrieves a previously computed match score.
   *
   * <p>Recruiter must belong to the organization that owns the job. Candidate can view their own
   * match scores. ADMIN can view any.
   */
  @Operation(
      summary = "Get match score by ID",
      description = "Returns a previously computed match score with override status.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Match score retrieved",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MatchScoreResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden",
            content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = @Content(mediaType = "application/problem+json"))
      })
  @GetMapping("/match-score/{matchScoreId}")
  @PreAuthorize("hasAnyRole('RECRUITER', 'CANDIDATE', 'ADMIN')")
  public ResponseEntity<MatchScoreResponse> getMatchScore(
      @PathVariable UUID matchScoreId, @AuthenticationPrincipal Jwt jwt) {

    // The service layer does ownership check
    var matchScore = aiMatchingService.getMatchScore(matchScoreId);
    // Convert domain entity to response DTO
    MatchScoreResponse response =
        new MatchScoreResponse(
            matchScore.getMatchScoreId(),
            matchScore.getApplicationId(),
            matchScore.getJobId(),
            matchScore.getScore(),
            matchScore.getRationale(),
            matchScore.getModel(),
            matchScore.getPromptVersion(),
            matchScore.getConfidence());
    return ResponseEntity.ok(response);
  }

  /**
   * Records a recruiter override of an AI match score recommendation.
   *
   * <p>Only the recruiting organization that owns the job may override.
   */
  @Operation(
      summary = "Override AI match score",
      description = "Records a recruiter override of an AI match score recommendation with reason.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Override recorded"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not found")
      })
  @PostMapping("/match-score/{matchScoreId}/override")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<Void> overrideMatchScore(
      @PathVariable UUID matchScoreId,
      @RequestBody OverrideRequest request,
      @AuthenticationPrincipal Jwt jwt) {

    aiMatchingService.overrideMatchScore(matchScoreId, getRecruiterId(jwt), request.reason());
    return ResponseEntity.ok().build();
  }

  // ===================== Helpers =====================

  private UUID getOrgId(Jwt jwt) {
    String orgId = jwt.getClaimAsString("orgId");
    return orgId != null ? UUID.fromString(orgId) : null;
  }

  private UUID getRecruiterId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  record OverrideRequest(String reason) {}
}
