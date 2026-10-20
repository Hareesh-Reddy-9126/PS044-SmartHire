package com.smarthire.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 configuration for api-gateway (reactive WebFlux). Provides metadata, security scheme
 * (bearerAuth), and server URLs.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SmartHire API Gateway")
                .description(
                    "Single ingress for all SmartHire microservices. Routes requests to job-service, auth-service, application-service, recruitment-service, document-service, and notification-service.")
                .version("0.0.1-SNAPSHOT")
                .license(new License().name("Proprietary").url("https://smarthire.local/license")))
        .servers(
            List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("API Gateway (primary entry point)")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "RS256 JWT issued by auth-service. Include as Authorization: Bearer <token>")))
        .security(List.of(new SecurityRequirement().addList("bearerAuth")));
  }
}
