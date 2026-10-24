package com.smarthire.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI Service: AI-assisted recruitment features.
 *
 * <p>Provides candidate–job match scoring, candidate summaries, JD drafting assistance, and resume
 * parsing. All AI outputs are advisory only — human-in-the-loop required for any recruitment
 * decision (ADR-0015, governance §43). Features are gated by the {@code ai.enabled} feature flag
 * and a provider-neutral {@link com.smarthire.ai.infra.AiProvider} abstraction.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AiServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiServiceApplication.class, args);
  }
}
