package com.smarthire.auth.service;

import com.smarthire.auth.domain.Company;
import com.smarthire.auth.domain.CompanyMembership;
import com.smarthire.auth.domain.User;
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
 * Service for company management. Handles company registration, approval, suspension, and
 * reactivation. Company creation is available to recruiters; approval, rejection and suspension are
 * Platform Admin operations.
 */
@Service
public class CompanyService {

  private final CompanyRepository companyRepository;
  private final CompanyMembershipRepository companyMembershipRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public CompanyService(
      CompanyRepository companyRepository,
      CompanyMembershipRepository companyMembershipRepository,
      UserRepository userRepository,
      Clock clock) {
    this.companyRepository = companyRepository;
    this.companyMembershipRepository = companyMembershipRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * Register a new company. Creates a PENDING_APPROVAL company owned by the given user (who becomes
   * the initial Company Admin). The company cannot be used until a Platform Admin approves it.
   *
   * @param companyName the company name
   * @param ownerId the user ID of the person registering the company
   * @return the created company ID
   */
  @Transactional
  public UUID registerCompany(String companyName, UUID ownerId) {
    if (companyRepository.existsByNameIgnoreCase(companyName)) {
      throw new IllegalStateException("A company with this name already exists");
    }

    User owner =
        userRepository
            .findById(ownerId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + ownerId));

    Company company =
        new Company(
            UUID.randomUUID(),
            companyName,
            Company.Status.PENDING_APPROVAL,
            ownerId,
            Instant.now(clock),
            null,
            null,
            null);

    Company saved = companyRepository.save(company);

    // The company owner becomes a COMPANY_ADMIN member (PENDING until approved by Platform Admin)
    CompanyMembership membership =
        new CompanyMembership(
            UUID.randomUUID(),
            company.getId(),
            ownerId,
            CompanyMembership.Role.COMPANY_ADMIN,
            CompanyMembership.Status.PENDING,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(clock));
    companyMembershipRepository.save(membership);

    return company.getId();
  }

  /**
   * Approve a pending company. Only a Platform Admin may do this.
   *
   * @param companyId the company to approve
   * @param approvedBy the Platform Admin user ID
   */
  @Transactional
  public void approveCompany(UUID companyId, UUID approvedBy) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));
    company.approve(approvedBy, Instant.now(clock));
    companyRepository.save(company);
  }

  /**
   * Reject a pending company. Only a Platform Admin may do this.
   *
   * @param companyId the company to reject
   * @param approvedBy the Platform Admin user ID
   * @param rejectionReason the reason for rejection
   */
  @Transactional
  public void rejectCompany(UUID companyId, UUID approvedBy, String rejectionReason) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));
    company.reject(approvedBy, Instant.now(clock), rejectionReason);
    companyRepository.save(company);
  }

  /**
   * Suspend an active company. Only a Platform Admin may do this.
   *
   * @param companyId the company to suspend
   * @param suspendedBy the Platform Admin user ID
   */
  @Transactional
  public void suspendCompany(UUID companyId, UUID suspendedBy) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));
    company.suspend(suspendedBy, Instant.now(clock));
    companyRepository.save(company);
  }

  /**
   * Reactivate a suspended company. Only a Platform Admin may do this.
   *
   * @param companyId the company to reactivate
   * @param reactivatedBy the Platform Admin user ID
   */
  @Transactional
  public void reactivateCompany(UUID companyId, UUID reactivatedBy) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));
    company.reactivate(reactivatedBy, Instant.now(clock));
    companyRepository.save(company);
  }

  /**
   * Get a company by ID.
   *
   * @throws CompanyNotFoundException if the company does not exist
   */
  @Transactional(readOnly = true)
  public Company getCompany(UUID companyId) {
    return companyRepository
        .findById(companyId)
        .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + companyId));
  }

  /** List all companies with an optional status filter. */
  @Transactional(readOnly = true)
  public List<Company> listCompanies(Company.Status status) {
    if (status != null) {
      return companyRepository.findByStatus(status);
    }
    return companyRepository.findAll();
  }

  /** Get all companies the user is an active member of. */
  @Transactional(readOnly = true)
  public List<Company> getUserCompanies(UUID userId) {
    List<CompanyMembership> memberships =
        companyMembershipRepository.findByUserIdAndStatus(userId, CompanyMembership.Status.ACTIVE);
    return memberships.stream()
        .map(m -> companyRepository.findById(m.getCompanyId()).orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
  }
}
