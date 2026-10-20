package com.smarthire.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI configuration for AI Service.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI aiServiceOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("SmartHire AI Service API")
            .description(
                "AI-assisted recruitment: match scoring, candidate summaries, JD assistance, "
                    + "resume parsing. All AI outputs are advisory only — human-in-the-loop required "
                    + "for any recruitment decision (ADR-0015).")
            .version("0.0.1-SNAPSHOT")
            .license(new License().name("Proprietary").url("https://smarthire.local/license")))
        .servers(List.of(
            new Server().url("http://localhost:8087").description("Local development"),
            new Server().url("http://localhost:8080").description("Via API Gateway (port 8080)")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("RS256 JWT issued by auth-service. Include as Authorization: Bearer <token>")));
  }
}