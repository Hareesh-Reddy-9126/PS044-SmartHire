package com.smarthire.auth.infra;

import com.smarthire.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence port for {@link User}. Lives in {@code infra} per governance §3. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
