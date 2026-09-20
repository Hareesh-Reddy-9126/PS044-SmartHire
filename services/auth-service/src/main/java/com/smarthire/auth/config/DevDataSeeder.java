package com.smarthire.auth.config;

import com.smarthire.auth.domain.Role;
import com.smarthire.auth.domain.User;
import com.smarthire.auth.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development/demo seeder for the non-self-service roles (decision 7): RECRUITER and ADMIN accounts
 * cannot be self-registered, so they are provisioned here from environment-supplied credentials.
 * Active only under the {@code seed} profile. Credentials come from {@link SeedProperties}
 * (environment-driven, never committed — decision 8); a blank email or password skips that account.
 * Idempotent: an account whose email already exists is left untouched, so repeated boots are safe.
 */
@Component
@Profile("seed")
public class DevDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

  /** The non-secret demo organization seeded by {@code V1__auth.sql}; recruiters attach to it. */
  private static final UUID DEMO_ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final SeedProperties properties;
  private final Clock clock;

  public DevDataSeeder(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      SeedProperties properties,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seed(properties.recruiterEmail(), properties.recruiterPassword(), Role.RECRUITER, DEMO_ORG_ID);
    seed(properties.adminEmail(), properties.adminPassword(), Role.ADMIN, null);
  }

  private void seed(String email, String rawPassword, Role role, UUID orgId) {
    if (isBlank(email) || isBlank(rawPassword)) {
      log.info("Seed skipped for {} — no credentials configured.", role);
      return;
    }
    String normalized = email.trim().toLowerCase();
    if (userRepository.existsByEmail(normalized)) {
      log.info("Seed user already present for {} ({}).", role, normalized);
      return;
    }
    userRepository.save(
        new User(
            UUID.randomUUID(),
            normalized,
            passwordEncoder.encode(rawPassword),
            role,
            orgId,
            Instant.now(clock)));
    log.info("Seeded {} account {}.", role, normalized);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
