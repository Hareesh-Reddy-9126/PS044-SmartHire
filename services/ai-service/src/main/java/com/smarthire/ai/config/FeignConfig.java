package com.smarthire.ai.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Feign configuration for AI Service.
 *
 * <p>Adds the current request's JWT as an Authorization header to outgoing Feign requests for
 * service-to-service authentication.
 */
@Configuration
public class FeignConfig {

  /**
   * Request interceptor that propagates the current JWT to downstream service calls.
   *
   * <p>This enables service-to-service authentication where the downstream service validates the
   * same JWT (stateless, no service-to-service secrets).
   */
  @Bean
  public RequestInterceptor jwtPropagationInterceptor() {
    return new RequestInterceptor() {
      @Override
      public void apply(RequestTemplate template) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
          Jwt jwt = jwtAuth.getToken();
          template.header("Authorization", "Bearer " + jwt.getTokenValue());
        }
      }
    };
  }

  /** Feign logger level for debugging inter-service calls. */
  @Bean
  Logger.Level feignLoggerLevel() {
    return Logger.Level.BASIC;
  }
}
