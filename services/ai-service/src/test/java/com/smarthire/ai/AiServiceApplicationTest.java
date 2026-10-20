package com.smarthire.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring context startup test for AI Service.
 *
 * <p>Verifies that the application context loads with the mock provider and local profile.
 */
@SpringBootTest
@ActiveProfiles("local")
class AiServiceApplicationTest {

  @Test
  void contextLoads() {
    // Context loads successfully
  }
}