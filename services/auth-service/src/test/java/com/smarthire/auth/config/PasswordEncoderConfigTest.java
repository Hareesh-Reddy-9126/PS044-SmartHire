package com.smarthire.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing contract (decision 1): BCrypt through Spring's {@link
 * org.springframework.security.crypto.password.DelegatingPasswordEncoder}, strength 12, with the
 * {@code {bcrypt}} algorithm prefix. The prefix is what lets stored hashes be migrated to a
 * stronger algorithm later without breaking existing logins.
 */
class PasswordEncoderConfigTest {

  private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

  @Test
  void encodesWithTheBcryptPrefixAndCostFactor12() {
    String hash = encoder.encode("correct-horse-battery-staple");

    // {bcrypt} = DelegatingPasswordEncoder tag; $2a = BCrypt version; $12$ = the approved strength.
    assertThat(hash).startsWith("{bcrypt}$2a$12$");
  }

  @Test
  void verifiesTheEncodedPasswordAndRejectsAWrongOne() {
    String hash = encoder.encode("correct-horse-battery-staple");

    assertThat(encoder.matches("correct-horse-battery-staple", hash)).isTrue();
    assertThat(encoder.matches("wrong-password", hash)).isFalse();
  }
}
