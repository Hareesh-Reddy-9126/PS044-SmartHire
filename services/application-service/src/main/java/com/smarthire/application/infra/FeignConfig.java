package com.smarthire.application.infra;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Feign configuration to forward the Authorization header for inter-service calls.
 */
@Configuration
public class FeignConfig {

  @Bean
  public RequestInterceptor authorizationHeaderForwardingInterceptor() {
    return new RequestInterceptor() {
      @Override
      public void apply(RequestTemplate template) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
          String bearerToken = "Bearer " + jwtToken.getToken().getTokenValue();
          template.header("Authorization", bearerToken);
        }
      }
    };
  }
}