package com.smarthire.ai.config;

import com.smarthire.ai.infra.AiProvider;
import com.smarthire.ai.infra.MockAiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AI provider configuration. Selects the provider implementation based on {@code
 * smarthire.ai.provider} property.
 *
 * <p>Supported providers:
 *
 * <ul>
 *   <li>{@code mock} — deterministic mock for local development and testing (default)
 *   <li>{@code claude} — Anthropic Claude (requires {@code AI_CLAUDE_API_KEY}, not implemented)
 * </ul>
 *
 * <p>Real provider implementations must be added here when approved.
 */
@Configuration
public class AiProviderConfig {

  @Value("${smarthire.ai.provider:mock}")
  private String provider;

  @Bean
  @Profile("!prod")
  public AiProvider aiProviderLocal() {
    // Local/dev always uses mock regardless of config
    return new MockAiProvider();
  }

  @Bean
  @Profile("prod")
  public AiProvider aiProviderProd() {
    return switch (provider.toLowerCase()) {
      case "mock" -> new MockAiProvider();
      case "claude" ->
          // Fail fast rather than silently serving fabricated mock results in production.
          throw new IllegalStateException(
              "AI_PROVIDER=claude is not implemented in this build; set AI_PROVIDER=mock"
                  + " explicitly or implement the Claude provider before enabling it.");
      default ->
          throw new IllegalArgumentException(
              "Unknown AI provider: " + provider + ". Supported: mock, claude");
    };
  }
}
