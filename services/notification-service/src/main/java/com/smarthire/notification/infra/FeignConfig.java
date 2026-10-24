package com.smarthire.notification.infra;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Feign configuration to forward the caller's Authorization header to auth-service. */
@Configuration
public class FeignConfig {

  @Bean
  public RequestInterceptor authorizationHeaderForwardingInterceptor() {
    return new RequestInterceptor() {
      @Override
      public void apply(RequestTemplate template) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
          template.header("Authorization", "Bearer " + jwtToken.getToken().getTokenValue());
        }
      }
    };
  }
}
