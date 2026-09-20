package com.smarthire.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injectable {@link Clock} so time-dependent logic (token TTLs, expiry) is deterministic in tests.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
