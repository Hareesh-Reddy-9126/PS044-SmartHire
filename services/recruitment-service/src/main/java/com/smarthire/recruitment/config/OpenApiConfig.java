package com.smarthire.recruitment.config;

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
 * OpenAPI 3 configuration for recruitment-service. Provides metadata, security scheme (bearerAuth),
 * and server URLs. Global security is NOT applied - controller defines requirements. All endpoints
 * require authentication (RECRUITER or ADMIN).
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SmartHire Recruitment Service API")
                .description(
                    "Recruitment workflow: shortlisting, interview scheduling, and final hiring decisions.")
                .version("0.0.1-SNAPSHOT")
                .license(new License().name("Proprietary").url("https://smarthire.local/license")))
        .servers(
            List.of(
                new Server().url("http://localhost:8084").description("Local development"),
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
