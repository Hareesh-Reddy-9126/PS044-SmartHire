package com.smarthire.auth.service;

import com.smarthire.auth.domain.CompanyMembership;
import com.smarthire.auth.infra.CompanyMembershipRepository;
import com.smarthire.auth.infra.CompanyRepository;
import com.smarthire.auth.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for company membership management. Handles membership requests, invitations, approvals,
 * rejections, and revocations. Membership is the link between a user and a company with a specific
 * role (COMPANY_ADMIN or RECRUITER).
 */
@Service
public class CompanyMembershipService {

  private final CompanyMembershipRepository membershipRepository;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public CompanyMembershipService(
      CompanyMembershipRepository membershipRepository,
      CompanyRepository companyRepository,
      UserRepository userRepository,
      Clock clock) {
    this.membershipRepository = membershipRepository;
    this.companyRepository = companyRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * Request membership in a company. Creates a PENDING membership that must be approved by a
   * Company Admin.
   *
   * @param companyId the company to join
   * @param userId the user requesting membership
   * @param role the role requested (COMPANY_ADMIN or RECRUITER)
   * @return the created membership ID
   */
  @Transactional
  public UUID requestMembership(UUID companyId, UUID userId, CompanyMembership.Role role) {
    // Check if membership already exists
    if (membershipRepository.findByCompanyIdAndUserId(companyId, userId).isPresent()) {
      throw new IllegalStateException("You already have a membership in this company");
    }

    CompanyMembership membership =
        new CompanyMembership(
            UUID.randomUUID(),
            companyId,
            userId,
            role,
            CompanyMembership.Status.PENDING,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(clock));

    return membershipRepository.save(membership).getId();
  }

  /**
   * Invite a user to join a company as a specific role. Creates a PENDING membership with an
   * invitation token that the user can use to accept.
   *
   * @param companyId the company
   * @param userId the user to invite
   * @param role the role to invite them as
   * @param invitedBy the Company Admin sending the invitation
   * @return the created membership ID
   */
  @Transactional
  public UUID inviteMember(
      UUID companyId, UUID userId, CompanyMembership.Role role, UUID invitedBy) {
    if (membershipRepository.findByCompanyIdAndUserId(companyId, userId).isPresent()) {
      throw new IllegalStateException("User already has a membership in this company");
    }

    String invitationToken = java.util.UUID.randomUUID().toString();
    Instant invitationExpiresAt = Instant.now(clock).plusSeconds(7 * 24 * 60 * 60); // 7 days

    CompanyMembership membership =
        new CompanyMembership(
            UUID.randomUUID(),
            companyId,
            userId,
            role,
            CompanyMembership.Status.PENDING,
            invitedBy,
            Instant.now(clock),
            null,
            null,
            invitationToken,
            invitationExpiresAt,
            Instant.now(clock));

    return membershipRepository.save(membership).getId();
  }

  /**
   * Accept a membership invitation or request. Changes status from PENDING to ACTIVE.
   *
   * @param membershipId the membership to accept
   * @param userId the user accepting (must match membership.userId)
   */
  @Transactional
  public void acceptMembership(UUID membershipId, UUID userId) {
    CompanyMembership membership =
        membershipRepository
            .findById(membershipId)
            .orElseThrow(() -> new IllegalStateException("Membership not found: " + membershipId));

    if (!membership.getUserId().equals(userId)) {
      throw new IllegalStateException("You are not the invited user for this membership");
    }

    if (membership.getStatus() != CompanyMembership.Status.PENDING) {
      throw new IllegalStateException("Only PENDING memberships can be accepted");
    }

    if (membership.getInvitationExpiresAt() != null
        && membership.getInvitationExpiresAt().isBefore(Instant.now(clock))) {
      membership.expire();
      membershipRepository.save(membership);
      throw new IllegalStateException("Invitation has expired");
    }

    membership.approve(userId, Instant.now(clock));
    membershipRepository.save(membership);
  }

  /**
   * Reject a membership invitation or request. Changes status from PENDING to REJECTED.
   *
   * @param membershipId the membership to reject
   * @param approvedBy the Company Admin rejecting
   */
  @Transactional
  public void rejectMembership(UUID membershipId, UUID approvedBy) {
    CompanyMembership membership =
        membershipRepository
            .findById(membershipId)
            .orElseThrow(() -> new IllegalStateException("Membership not found: " + membershipId));

    if (membership.getStatus() != CompanyMembership.Status.PENDING) {
      throw new IllegalStateException("Only PENDING memberships can be rejected");
    }

    membership.reject(approvedBy, Instant.now(clock));
    membershipRepository.save(membership);
  }

  /**
   * Revoke an active membership. Only a Company Admin may do this.
   *
   * @param membershipId the membership to revoke
   * @param revokedBy the Company Admin revoking
   */
  @Transactional
  public void revokeMembership(UUID membershipId, UUID revokedBy) {
    CompanyMembership membership =
        membershipRepository
            .findById(membershipId)
            .orElseThrow(() -> new IllegalStateException("Membership not found: " + membershipId));

    if (membership.getStatus() != CompanyMembership.Status.ACTIVE) {
      throw new IllegalStateException("Only ACTIVE memberships can be revoked");
    }

    membership.revoke(revokedBy, Instant.now(clock));
    membershipRepository.save(membership);
  }

  /** List all memberships for a company. */
  @Transactional(readOnly = true)
  public List<CompanyMembership> listMembers(UUID companyId) {
    return membershipRepository.findByCompanyId(companyId);
  }

  /** List all active memberships for a user (companies they belong to). */
  @Transactional(readOnly = true)
  public List<CompanyMembership> listUserMemberships(UUID userId) {
    return membershipRepository.findByUserIdAndStatus(userId, CompanyMembership.Status.ACTIVE);
  }

  /** Find an active membership by company and user. */
  @Transactional(readOnly = true)
  public CompanyMembership getMembership(UUID companyId, UUID userId) {
    return membershipRepository
        .findByCompanyIdAndUserId(companyId, userId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No membership found for company " + companyId + " and user " + userId));
  }

  /** Count active members in a company. */
  @Transactional(readOnly = true)
  public long countActiveMembers(UUID companyId) {
    return membershipRepository.countByCompanyIdAndStatus(
        companyId, CompanyMembership.Status.ACTIVE);
  }
}
