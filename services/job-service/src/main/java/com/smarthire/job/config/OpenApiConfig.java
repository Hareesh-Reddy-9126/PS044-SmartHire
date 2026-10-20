package com.smarthire.job.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 configuration for job-service. Provides metadata, security scheme (bearerAuth), and
 * server URLs. Global security is NOT applied - individual controllers define their requirements.
 * GET /api/v1/jobs/** endpoints are public; mutations require RECRUITER role.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SmartHire Job Service API")
                .description(
                    "Job posting management: public browsing of OPEN jobs, recruiter CRUD for DRAFT/OPEN/CLOSED postings.")
                .version("0.0.1-SNAPSHOT")
                .license(new License().name("Proprietary").url("https://smarthire.local/license")))
        .servers(
            List.of(
                new Server().url("http://localhost:8081").description("Local development"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Via API Gateway (port 8080)")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "RS256 JWT issued by auth-service. Include as Authorization: Bearer <token>")));
  }
}
