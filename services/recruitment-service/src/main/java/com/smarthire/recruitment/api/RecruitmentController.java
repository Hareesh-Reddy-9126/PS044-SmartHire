package com.smarthire.recruitment.api;

import com.smarthire.recruitment.api.dto.FinalStatusUpdateRequest;
import com.smarthire.recruitment.api.dto.InterviewStatusUpdateRequest;
import com.smarthire.recruitment.api.dto.RecruitmentCreateRequest;
import com.smarthire.recruitment.api.dto.RecruitmentResponse;
import com.smarthire.recruitment.domain.Recruitment;
import com.smarthire.recruitment.security.RecruitmentPrincipal;
import com.smarthire.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
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
 * Recruitment management endpoints. Versioned base path {@code /api/v1} from day one (governance
 * §11).
 *
 * <p>Every method derives the caller's identity from {@code @AuthenticationPrincipal Jwt} — the
 * token the resource-server filter already verified. The request body, query string and headers are
 * never consulted for ownership, so a caller cannot claim an organization or a candidate identity
 * they do not hold. There is no anonymous path here: unlike an OPEN job posting, no recruitment is
 * public, so the filter chain requires authentication on all of it.
 *
 * <p>The three writes are gated by {@code @PreAuthorize("hasRole('RECRUITER')")}. ADMIN is
 * deliberately excluded: it is a platform-operations role and explicitly not a recruitment actor
 * (ADR-0010), so it may read a recruitment for support and audit but never shortlist, schedule or
 * decide one. Ownership itself is enforced one layer deeper, in {@code RecruitmentService}.
 */
@RestController
@RequestMapping("/api/v1/recruitments")
public class RecruitmentController {

  /** Bound so a caller cannot ask for an unbounded page. */
  private static final int MAX_PAGE_SIZE = 100;

  private final RecruitmentService recruitmentService;

  public RecruitmentController(RecruitmentService recruitmentService) {
    this.recruitmentService = recruitmentService;
  }

  /**
   * Shortlists an application — the action that creates the recruitment record, at {@code
   * NOT_SCHEDULED}/{@code PENDING}.
   *
   * <p>Only a recruiter of the organization that owns the underlying job posting may shortlist;
   * anyone else receives 404, so the endpoint cannot be used to probe for another organization's
   * application identifiers.
   */
  @PostMapping
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<RecruitmentResponse> shortlist(
      @Valid @RequestBody RecruitmentCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    Recruitment recruitment =
        recruitmentService.shortlist(request.applicationId(), principal.orgId());
    return ResponseEntity.created(
            URI.create("/api/v1/recruitments/" + recruitment.getRecruitmentId()))
        .body(RecruitmentResponse.from(recruitment));
  }

  /**
   * Reads one recruitment.
   *
   * <p>Visible to a recruiter of the owning organization, to the candidate whose application it
   * tracks, and to ADMIN for operational support. Everyone else receives 404 rather than 403, so
   * identifiers cannot be probed. No {@code @PreAuthorize} here on purpose: the decision is
   * object-level, not role-level, and is made in the service against the stored owner.
   */
  @GetMapping("/{recruitmentId}")
  public ResponseEntity<RecruitmentResponse> getRecruitment(
      @PathVariable UUID recruitmentId, @AuthenticationPrincipal Jwt jwt) {
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    return ResponseEntity.ok(
        RecruitmentResponse.from(
            recruitmentService.get(
                recruitmentId, principal.candidateId(), principal.orgId(), principal.isAdmin())));
  }

  /**
   * Lists the recruitments the caller may see: their organization's for a recruiter, their own for
   * a candidate tracking an outcome (AC-5), all of them for ADMIN's operational read.
   *
   * <p>The scope is taken from the verified token, so there is no query parameter that could widen
   * it. Returns the Spring Data page envelope because a pageable listing has to report total size
   * and page position somewhere.
   */
  @GetMapping
  public PagedModel<RecruitmentResponse> listRecruitments(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal Jwt jwt) {
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    // Sorted by recruitmentId so paging is stable across requests.
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by("recruitmentId"));
    return new PagedModel<>(
        recruitmentService
            .list(principal.candidateId(), principal.orgId(), principal.isAdmin(), pageable)
            .map(RecruitmentResponse::from));
  }

  /**
   * Advances the interview lifecycle: {@code NOT_SCHEDULED → SCHEDULED}, {@code SCHEDULED →
   * COMPLETED}, {@code SCHEDULED → NO_SHOW}. Any other transition is rejected with 409.
   */
  @PatchMapping("/{recruitmentId}/interview-status")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<RecruitmentResponse> updateInterviewStatus(
      @PathVariable UUID recruitmentId,
      @Valid @RequestBody InterviewStatusUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    return ResponseEntity.ok(
        RecruitmentResponse.from(
            recruitmentService.updateInterviewStatus(
                recruitmentId, request.status(), principal.orgId())));
  }

  /**
   * Decides the outcome: {@code PENDING → HIRED} or {@code PENDING → REJECTED}. Both are terminal,
   * so a second decision is rejected with 409.
   */
  @PatchMapping("/{recruitmentId}/final-status")
  @PreAuthorize("hasRole('RECRUITER')")
  public ResponseEntity<RecruitmentResponse> updateFinalStatus(
      @PathVariable UUID recruitmentId,
      @Valid @RequestBody FinalStatusUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    return ResponseEntity.ok(
        RecruitmentResponse.from(
            recruitmentService.updateFinalStatus(
                recruitmentId, request.status(), principal.orgId())));
  }
}
