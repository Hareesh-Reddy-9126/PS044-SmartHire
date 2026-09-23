package com.smarthire.job.infra;

import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Persistence port for {@link Job} (Spring Data JPA). Lives in {@code infra} per governance §3.
 *
 * <p>{@link JpaSpecificationExecutor} supplies the paged {@code findAll(Specification, Pageable)}
 * the Inc 2 listing uses: visibility and filters are composed as specifications in {@code
 * JobService} so a single query carries both, rather than filtering in memory after the page is
 * fetched.
 */
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

  Optional<Job> findByJobId(UUID jobId);

  List<Job> findByStatus(JobStatus status);
}
