package com.smarthire.application.infra;

import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Persistence port for {@link Application} (Spring Data JPA). Lives in {@code infra} per governance
 * §3.
 *
 * <p>{@link JpaSpecificationExecutor} supplies the paged {@code findAll(Specification, Pageable)}
 * for future listing use; not yet needed for the minimal Review-1 API but included for consistency.
 */
public interface ApplicationRepository
    extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

  Optional<Application> findByApplicationId(UUID applicationId);

  Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);

  List<Application> findByCandidateId(UUID candidateId);

  List<Application> findByJobId(UUID jobId);

  List<Application> findByStatus(ApplicationStatus status);
}
