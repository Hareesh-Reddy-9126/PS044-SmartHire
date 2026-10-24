package com.smarthire.ai.infra;

import com.smarthire.ai.domain.MatchScore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link MatchScore} entities. */
@Repository
public interface MatchScoreRepository extends JpaRepository<MatchScore, UUID> {

  /**
   * Finds a match score by application and job ID.
   *
   * @param applicationId the application UUID
   * @param jobId the job UUID
   * @return the match score if present
   */
  @Query(
      "SELECT ms FROM MatchScore ms WHERE ms.applicationId = :applicationId AND ms.jobId = :jobId")
  Optional<MatchScore> findByApplicationIdAndJobId(UUID applicationId, UUID jobId);

  /**
   * Finds all match scores for a given application.
   *
   * @param applicationId the application UUID
   * @return list of match scores
   */
  List<MatchScore> findByApplicationId(UUID applicationId);

  /**
   * Finds all match scores for a given job.
   *
   * @param jobId the job UUID
   * @return list of match scores
   */
  List<MatchScore> findByJobId(UUID jobId);

  /**
   * Finds match scores with pagination.
   *
   * @param pageable pagination parameters
   * @return page of match scores
   */
  Page<MatchScore> findAll(Pageable pageable);
}
