package com.smarthire.auth.infra;

import com.smarthire.auth.domain.CompanyMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link CompanyMembership} entities.
 *
 * <p>Membership records link users to companies with a role and status. They are the
 * authorization basis for all company-scoped operations.
 */
@Repository
public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {

  /** Find a membership by its ID. */
  Optional<CompanyMembership> findById(UUID id);

  /** Find all memberships for a company. */
  List<CompanyMembership> findByCompanyId(UUID companyId);

  /** Find all memberships for a company and status. */
  List<CompanyMembership> findByCompanyIdAndStatus(UUID companyId, CompanyMembership.Status status);

  /** Find all memberships for a user. */
  List<CompanyMembership> findByUserId(UUID userId);

  /** Find all active memberships for a user. */
  List<CompanyMembership> findByUserIdAndStatus(UUID userId, CompanyMembership.Status status);

  /** Find a membership by company and user. */
  Optional<CompanyMembership> findByCompanyIdAndUserId(UUID companyId, UUID userId);

  /** Find all active memberships for a company. */
  List<CompanyMembership> findByCompanyIdAndStatusAndRole(
      UUID companyId, CompanyMembership.Status status, CompanyMembership.Role role);

  /** Check if a membership exists for company and user. */
  boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);

  /** Count active memberships for a company. */
  long countByCompanyIdAndStatus(UUID companyId, CompanyMembership.Status status);
}
