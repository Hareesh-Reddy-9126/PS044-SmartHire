package com.smarthire.application.api;

import com.smarthire.application.api.dto.ApplicationCreateRequest;
import com.smarthire.application.api.dto.ApplicationResponse;
import com.smarthire.application.security.ApplicationPrincipal;
import com.smarthire.application.service.ApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
public class ApplicationController {

  private final ApplicationService applicationService;

  public ApplicationController(ApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  /**
   * Submits a new application.
   *
   * <p>Candidate must be authenticated. The {@code candidateId} comes from the verified JWT {@code
   * sub} claim — never from the request body. The requested job is validated via Job Service to
   * ensure it exists and is OPEN.
   */
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
  @PostMapping("/{applicationId}/withdraw")
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<ApplicationResponse> withdrawApplication(
      @PathVariable UUID applicationId, @AuthenticationPrincipal Jwt jwt) {
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    var application = applicationService.withdraw(applicationId, principal.candidateId());
    return ResponseEntity.ok(ApplicationResponse.from(application));
  }
}
