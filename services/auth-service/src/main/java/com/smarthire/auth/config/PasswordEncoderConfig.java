package com.smarthire.auth.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing (decision 1): BCrypt at strength 12 behind a {@link DelegatingPasswordEncoder},
 * so stored hashes carry the {@code {bcrypt}} prefix and the scheme can be upgraded later without a
 * data migration. The default {@code PasswordEncoderFactories} encoder uses BCrypt strength 10;
 * this pins strength 12, so it is wired explicitly.
 */
@Configuration
public class PasswordEncoderConfig {

  private static final String ENCODING_ID = "bcrypt";
  private static final int BCRYPT_STRENGTH = 12;

  @Bean
  public PasswordEncoder passwordEncoder() {
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put(ENCODING_ID, new BCryptPasswordEncoder(BCRYPT_STRENGTH));
    return new DelegatingPasswordEncoder(ENCODING_ID, encoders);
  }
}
