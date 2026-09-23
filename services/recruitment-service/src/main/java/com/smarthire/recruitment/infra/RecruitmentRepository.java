package com.smarthire.recruitment.infra;

import com.smarthire.recruitment.domain.Recruitment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for {@link Recruitment} (Spring Data JPA). Lives in {@code infra} per governance
 * §3.
 *
 * <p>The two paged finders are the authorization-scoped listings: a recruiter sees only rows whose
 * {@code owner_org_id} is their organization, a candidate only rows whose {@code candidate_id} is
 * them. Scoping in the query rather than after it means another party's row is never loaded in the
 * first place. Both are backed by the indexes V1 creates.
 */
public interface RecruitmentRepository extends JpaRepository<Recruitment, UUID> {

  Optional<Recruitment> findByRecruitmentId(UUID recruitmentId);

  /** Backs the one-recruitment-per-application rule; {@code UNIQUE(application_id)} enforces it. */
  Optional<Recruitment> findByApplicationId(UUID applicationId);

  Page<Recruitment> findByOwnerOrgId(UUID ownerOrgId, Pageable pageable);

  Page<Recruitment> findByCandidateId(UUID candidateId, Pageable pageable);
}
