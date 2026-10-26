package com.smarthire.auth.api;

import com.smarthire.auth.api.dto.CompanyCreateRequest;
import com.smarthire.auth.api.dto.CompanyResponse;
import com.smarthire.auth.service.CompanyMembershipService;
import com.smarthire.auth.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company management endpoints. Company registration is available to recruiters; approval,
 * rejection and suspension are Platform Admin operations; a company is visible to its active
 * members and to Platform Admins.
 */
@RestController
@RequestMapping("/api/v1/auth/companies")
@Tag(name = "Company Management", description = "Company registration, approval, and management")
public class CompanyController {

  private final CompanyService companyService;
  private final CompanyMembershipService membershipService;

  public CompanyController(
      CompanyService companyService, CompanyMembershipService membershipService) {
    this.companyService = companyService;
    this.membershipService = membershipService;
  }

  /**
   * Register a new company. Creates a PENDING_APPROVAL company; the caller becomes a pending
   * COMPANY_ADMIN member. Requires RECRUITER role: the caller is the owner.
   */
  @Operation(
      summary = "Register a new company",
      description =
          "Creates a company in PENDING_APPROVAL status owned by the authenticated recruiter. Requires RECRUITER role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Company registration submitted",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CompanyResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping
  @Secured("ROLE_RECRUITER")
  public ResponseEntity<CompanyResponse> registerCompany(
      @Valid @RequestBody CompanyCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID ownerId = UUID.fromString(jwt.getSubject());
    UUID companyId = companyService.registerCompany(request.companyName(), ownerId);
    com.smarthire.auth.domain.Company company = companyService.getCompany(companyId);
    return ResponseEntity.status(HttpStatus.CREATED).body(CompanyResponse.from(company));
  }

  /** Approve a pending company. Platform Admin only. */
  @Operation(
      summary = "Approve a company",
      description = "Platform Admin approves a pending company")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company approved"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{companyId}/approve")
  @Secured("ROLE_ADMIN")
  public ResponseEntity<Void> approveCompany(
      @PathVariable UUID companyId, @AuthenticationPrincipal Jwt jwt) {
    UUID approverId = UUID.fromString(jwt.getSubject());
    companyService.approveCompany(companyId, approverId);
    return ResponseEntity.ok().build();
  }

  /** Reject a pending company. Platform Admin only. */
  @Operation(summary = "Reject a company", description = "Platform Admin rejects a pending company")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company rejected"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{companyId}/reject")
  @Secured("ROLE_ADMIN")
  public ResponseEntity<Void> rejectCompany(
      @PathVariable UUID companyId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody String rejectionReason) {
    UUID approverId = UUID.fromString(jwt.getSubject());
    companyService.rejectCompany(companyId, approverId, rejectionReason);
    return ResponseEntity.ok().build();
  }

  /** Suspend an active company. Platform Admin only. */
  @Operation(
      summary = "Suspend a company",
      description = "Platform Admin suspends an active company")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company suspended"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{companyId}/suspend")
  @Secured("ROLE_ADMIN")
  public ResponseEntity<Void> suspendCompany(
      @PathVariable UUID companyId, @AuthenticationPrincipal Jwt jwt) {
    UUID suspenderId = UUID.fromString(jwt.getSubject());
    companyService.suspendCompany(companyId, suspenderId);
    return ResponseEntity.ok().build();
  }

  /** Reactivate a suspended company. Platform Admin only. */
  @Operation(
      summary = "Reactivate a company",
      description = "Platform Admin reactivates a suspended company")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company reactivated"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{companyId}/reactivate")
  @Secured("ROLE_ADMIN")
  public ResponseEntity<Void> reactivateCompany(
      @PathVariable UUID companyId, @AuthenticationPrincipal Jwt jwt) {
    UUID reactorId = UUID.fromString(jwt.getSubject());
    companyService.reactivateCompany(companyId, reactorId);
    return ResponseEntity.ok().build();
  }

  /** Get a company by ID. Visible to Platform Admins and that company's active members. */
  @Operation(
      summary = "Get company details",
      description = "Get company details by ID. Platform Admin or an active member of the company.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company details"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not a member"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/{companyId}")
  public ResponseEntity<CompanyResponse> getCompany(
      @PathVariable UUID companyId, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    if (!isAdmin(jwt) && !isMember(companyId, userId)) {
      throw new AccessDeniedException("This company is not visible to your account.");
    }
    com.smarthire.auth.domain.Company company = companyService.getCompany(companyId);
    return ResponseEntity.ok(CompanyResponse.from(company));
  }

  /** List all companies (optionally filtered by status). Platform Admin only. */
  @Operation(
      summary = "List companies",
      description = "List all companies, optionally filtered by status. Platform Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company list"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping
  @Secured("ROLE_ADMIN")
  public ResponseEntity<List<CompanyResponse>> listCompanies(
      @org.springframework.web.bind.annotation.RequestParam(required = false)
          com.smarthire.auth.domain.Company.Status status) {
    List<com.smarthire.auth.domain.Company> companies = companyService.listCompanies(status);
    List<CompanyResponse> responses = companies.stream().map(CompanyResponse::from).toList();
    return ResponseEntity.ok(responses);
  }

  /** Get companies the current user is an active member of. */
  @Operation(
      summary = "Get my companies",
      description = "Get companies the current user is an active member of")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "User's companies"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/my")
  public ResponseEntity<List<CompanyResponse>> getMyCompanies(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    List<com.smarthire.auth.domain.Company> companies = companyService.getUserCompanies(userId);
    List<CompanyResponse> responses = companies.stream().map(CompanyResponse::from).toList();
    return ResponseEntity.ok(responses);
  }

  private static boolean isAdmin(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    return roles != null && roles.contains("ADMIN");
  }

  private boolean isMember(UUID companyId, UUID userId) {
    return membershipService.listUserMemberships(userId).stream()
        .anyMatch(membership -> companyId.equals(membership.getCompanyId()));
  }
}
