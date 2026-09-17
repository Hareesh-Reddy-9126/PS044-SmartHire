package com.smarthire.job.infra;

import com.smarthire.job.domain.Job;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence port for {@link Job} (Spring Data JPA). Lives in {@code infra} per governance §3. */
public interface JobRepository extends JpaRepository<Job, UUID> {}
