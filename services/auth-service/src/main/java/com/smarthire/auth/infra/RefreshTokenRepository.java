package com.smarthire.auth.infra;

import com.smarthire.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence port for {@link RefreshToken}. Lives in {@code infra} per governance §3. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  List<RefreshToken> findByFamilyId(UUID familyId);

  /**
   * Reuse-detection response: revoke every still-live token in a compromised family in one shot.
   */
  @Modifying
  @Query(
      "update RefreshToken t set t.revoked = true where t.familyId = :familyId and t.revoked = false")
  int revokeFamily(@Param("familyId") UUID familyId);
}
