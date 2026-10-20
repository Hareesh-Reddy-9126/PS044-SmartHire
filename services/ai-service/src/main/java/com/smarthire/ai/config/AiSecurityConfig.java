package com.smarthire.ai.config;

import com.smarthire.ai.properties.AiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

/**
 * AI Service security configuration.
 *
 * <p>RS256 JWT validation against auth-service JWKS with issuer/audience pinning. Deny-by-default
 * with allow-list for public endpoints. Role-based method security via {@code @PreAuthorize}.
 *
 * <p>Kill switch: {@code smarthire.ai.enabled=false} disables AI endpoints but keeps health/info
 * available.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AiProperties.class)
public class AiSecurityConfig {

  private final AiProperties aiProperties;

  public AiSecurityConfig(AiProperties aiProperties) {
    this.aiProperties = aiProperties;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // Public endpoints (health, info, OpenAPI)
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                "/webjars/swagger-ui/**", "/webjars/swagger-ui.html").permitAll()
            // AI endpoints require authentication (enforced by @PreAuthorize on controllers)
            .requestMatchers("/api/v1/ai/**").authenticated()
            .anyExchange().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                .jwtDecoder(jwtDecoder()))
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .accessDeniedHandler(new AccessDeniedHandlerImpl()))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .accessDeniedHandler(new AccessDeniedHandlerImpl()));

    return http.build();
  }

  @Bean
  public NimbusJwtDecoder jwtDecoder(AiProperties.JwtProperties jwtProps) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwtProps.getJwkSetUri()).build();
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(jwtProps.getIssuer());
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(jwtProps.getAudience()));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
    return decoder;
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}