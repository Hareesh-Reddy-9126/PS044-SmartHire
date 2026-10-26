package com.smarthire.auth.api;

import com.smarthire.auth.api.dto.CompanyMembershipCreateRequest;
import com.smarthire.auth.api.dto.CompanyMembershipResponse;
import com.smarthire.auth.domain.CompanyMembership;
import com.smarthire.auth.mapper.CompanyMembershipMapper;
import com.smarthire.auth.service.CompanyMembershipService;
import com.smarthire.auth.service.CompanyNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company membership management endpoints. Covers membership requests, invitations, approvals,
 * rejections, and revocations. Management operations require an active COMPANY_ADMIN membership in
 * the target company (or the platform ADMIN role); requesting membership is open to any
 * authenticated user.
 */
@RestController
@RequestMapping("/api/v1/auth/companies/{companyId}/memberships")
@Tag(
    name = "Company Membership",
    description = "Company membership requests, invitations, approvals, and revocations")
public class CompanyMembershipController {

  private final CompanyMembershipService membershipService;

  public CompanyMembershipController(CompanyMembershipService membershipService) {
    this.membershipService = membershipService;
  }

  /**
   * Request membership in a company. Creates a PENDING membership that a Company Admin must
   * approve. Public to authenticated users.
   */
  @Operation(summary = "Request membership", description = "Request to join a company as a member")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Membership requested"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping
  public ResponseEntity<CompanyMembershipResponse> requestMembership(
      @PathVariable UUID companyId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CompanyMembershipCreateRequest request) {
    if (CompanyMembershipMapper.toDomainRole(request.role())
        == CompanyMembership.Role.COMPANY_ADMIN) {
      throw new AccessDeniedException("Company administrator membership cannot be self-requested.");
    }
    UUID userId = UUID.fromString(jwt.getSubject());
    UUID membershipId =
        membershipService.requestMembership(
            companyId, userId, CompanyMembershipMapper.toDomainRole(request.role()));
    CompanyMembershipResponse response =
        new CompanyMembershipResponse(membershipId, companyId, userId, request.role(), "PENDING");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Invite a user to join a company. Company Admin only. */
  @Operation(
      summary = "Invite a user to join the company",
      description = "Company Admin invites a user to join")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Invitation sent"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires COMPANY_ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Company or user not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/invite")
  public ResponseEntity<CompanyMembershipResponse> inviteMember(
      @PathVariable UUID companyId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CompanyMembershipCreateRequest request) {
    requireCompanyAdmin(jwt, companyId);
    UUID inviterId = UUID.fromString(jwt.getSubject());
    UUID membershipId =
        membershipService.inviteMember(
            companyId,
            request.userId(),
            CompanyMembershipMapper.toDomainRole(request.role()),
            inviterId);
    CompanyMembershipResponse response =
        new CompanyMembershipResponse(
            membershipId, companyId, request.userId(), request.role(), "PENDING");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Accept a membership invitation or request. The authenticated user accepts their own pending
   * membership.
   */
  @Operation(
      summary = "Accept membership",
      description = "Accept a membership invitation or request")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Membership accepted"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not the invited user"),
        @ApiResponse(responseCode = "404", description = "Membership not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{membershipId}/accept")
  public ResponseEntity<Void> acceptMembership(
      @PathVariable UUID companyId,
      @PathVariable UUID membershipId,
      @AuthenticationPrincipal Jwt jwt) {
    requireMembershipInCompany(companyId, membershipId);
    UUID userId = UUID.fromString(jwt.getSubject());
    membershipService.acceptMembership(membershipId, userId);
    return ResponseEntity.ok().build();
  }

  /** Reject a membership invitation or request. Company Admin only. */
  @Operation(
      summary = "Reject membership",
      description = "Company Admin rejects a membership request or invitation")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Membership rejected"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires COMPANY_ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Membership not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{membershipId}/reject")
  public ResponseEntity<Void> rejectMembership(
      @PathVariable UUID companyId,
      @PathVariable UUID membershipId,
      @AuthenticationPrincipal Jwt jwt) {
    requireCompanyAdmin(jwt, companyId);
    requireMembershipInCompany(companyId, membershipId);
    UUID approverId = UUID.fromString(jwt.getSubject());
    membershipService.rejectMembership(membershipId, approverId);
    return ResponseEntity.ok().build();
  }

  /** Revoke an active membership. Company Admin only. */
  @Operation(
      summary = "Revoke membership",
      description = "Company Admin revokes a member's membership")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Membership revoked"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires COMPANY_ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Membership not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/{membershipId}/revoke")
  public ResponseEntity<Void> revokeMembership(
      @PathVariable UUID companyId,
      @PathVariable UUID membershipId,
      @AuthenticationPrincipal Jwt jwt) {
    requireCompanyAdmin(jwt, companyId);
    requireMembershipInCompany(companyId, membershipId);
    UUID revokerId = UUID.fromString(jwt.getSubject());
    membershipService.revokeMembership(membershipId, revokerId);
    return ResponseEntity.ok().build();
  }

  /** List all members of a company. Company Admin only. */
  @Operation(summary = "List company members", description = "List all members of a company")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Company members list"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires COMPANY_ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Company not found")
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping
  public ResponseEntity<List<CompanyMembershipResponse>> listMembers(
      @PathVariable UUID companyId, @AuthenticationPrincipal Jwt jwt) {
    requireCompanyAdmin(jwt, companyId);
    List<com.smarthire.auth.domain.CompanyMembership> memberships =
        membershipService.listMembers(companyId);
    List<CompanyMembershipResponse> responses =
        memberships.stream()
            .map(
                m ->
                    new CompanyMembershipResponse(
                        m.getId(),
                        m.getCompanyId(),
                        m.getUserId(),
                        CompanyMembershipMapper.toDtoRole(m.getRole()),
                        m.getStatus().name()))
            .toList();
    return ResponseEntity.ok(responses);
  }

  /** List all active memberships for the current user (companies they belong to). */
  @Operation(
      summary = "Get my memberships",
      description = "Get all company memberships for the current user")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "User's memberships"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/my")
  public ResponseEntity<List<CompanyMembershipResponse>> getMyMemberships(
      @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    List<com.smarthire.auth.domain.CompanyMembership> memberships =
        membershipService.listUserMemberships(userId);
    List<CompanyMembershipResponse> responses =
        memberships.stream()
            .map(
                m ->
                    new CompanyMembershipResponse(
                        m.getId(),
                        m.getCompanyId(),
                        m.getUserId(),
                        CompanyMembershipMapper.toDtoRole(m.getRole()),
                        m.getStatus().name()))
            .toList();
    return ResponseEntity.ok(responses);
  }

  /** Platform Admins may manage any company; otherwise the caller must be a company admin. */
  private void requireCompanyAdmin(Jwt jwt, UUID companyId) {
    if (isPlatformAdmin(jwt)) {
      return;
    }
    UUID userId = UUID.fromString(jwt.getSubject());
    boolean companyAdmin =
        membershipService.listUserMemberships(userId).stream()
            .anyMatch(
                membership ->
                    companyId.equals(membership.getCompanyId())
                        && membership.getRole() == CompanyMembership.Role.COMPANY_ADMIN);
    if (!companyAdmin) {
      throw new AccessDeniedException(
          "An active company administrator membership is required for this company.");
    }
  }

  /** Reject/revoke must not reach across tenants through a guessed membership id. */
  private void requireMembershipInCompany(UUID companyId, UUID membershipId) {
    boolean belongs =
        membershipService.listMembers(companyId).stream()
            .anyMatch(membership -> membershipId.equals(membership.getId()));
    if (!belongs) {
      throw new CompanyNotFoundException("Membership not found: " + membershipId);
    }
  }

  private static boolean isPlatformAdmin(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    return roles != null && roles.contains("ADMIN");
  }
}
