package com.smarthire.auth.infra;

import com.smarthire.auth.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Company} entities.
 *
 * <p>Company records are owned by the auth-service and accessed only through the CompanyService.
 * No cross-service foreign keys reference this table; each service stores its own copy of
 * owner_org_id and enforces authorization at the service boundary.
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

  /** Find a company by its ID. */
  Optional<Company> findById(UUID id);

  /** Find companies by status. */
  List<Company> findByStatus(Company.Status status);

  /** Find companies by name (case-insensitive partial match). */
  @Query(
      "SELECT c FROM Company c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
  List<Company> findByNameContainingIgnoreCase(@Param("name") String name);

  /** Check if a company name already exists (case-insensitive). */
  boolean existsByNameIgnoreCase(String name);
}
