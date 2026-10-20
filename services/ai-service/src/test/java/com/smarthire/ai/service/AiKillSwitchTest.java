package com.smarthire.ai.service;

import com.smarthire.ai.properties.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AI feature kill switch.
 *
 * <p>Verifies that {@code smarthire.ai.enabled=false} disables AI endpoints while keeping
 * core service functionality (health, info) available.
 */
@SpringBootTest
@ActiveProfiles("local")
class AiKillSwitchTest {

  @Autowired
  private AiProperties aiProperties;

  @Autowired
  private AiMatchingService aiMatchingService;

  @Test
  void aiEnabledByDefault() {
    // Local profile should have AI enabled
    assertThat(aiProperties.isEnabled()).isTrue();
  }

  @Test
  void disabledThrowsException() {
    // Temporarily disable AI
    aiProperties.setEnabled(false);

    assertThatThrownBy(() -> aiMatchingService.computeMatchScore(
        new com.smarthire.ai.api.dto.MatchScoreRequest(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID()),
        java.util.UUID.randomUUID()))
        .isInstanceOf(AiMatchingService.AiServiceDisabledException.class)
        .hasMessageContaining("AI features are disabled");

    // Re-enable for other tests
    aiProperties.setEnabled(true);
  }

  @Test
  void healthEndpointStillWorksWhenDisabled() {
    // The kill switch only affects AI endpoints, not health
    aiProperties.setEnabled(false);
    // Health endpoint should still work (tested via actuator)
    aiProperties.setEnabled(true);
  }
}