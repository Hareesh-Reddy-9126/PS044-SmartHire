package com.smarthire.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.common.web.CorrelationId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Stateless resource-server security for application-service. Defense-in-depth behind the gateway
 * (decision 4): the service re-verifies the RS256 access token itself against the auth-service JWKS
 * with {@code iss}/{@code aud} pinned, so ownership is always derived from a locally verified
 * token.
 *
 * <p>Authorization is layered:
 *
 * <ul>
 *   <li><b>Filter chain (coarse)</b> — all application endpoints require authentication (no public
 *       reads for applications, unlike jobs).
 *   <li><b>Method security (role)</b> — {@code @PreAuthorize} on endpoints for CANDIDATE/RECRUITER.
 *   <li><b>Service layer (object/ownership)</b> — candidate ownership check, recruiter org check
 *       via JobClient.
 * </ul>
 *
 * <p>401/403 produced by the filter chain are rendered as RFC 9457 {@code application/problem+json}
 * (ADR-0016) with no authentication detail leaked.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(ApplicationSecurityProperties.class)
public class SecurityConfig {

  private final ObjectMapper objectMapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "objectMapper is an application-scoped Spring singleton supplied by the container via"
              + " constructor injection; retaining the shared JSON serializer is the intended DI"
              + " pattern, not exposure of internal mutable state (governance §29).")
  public SecurityConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  /**
   * Offline RS256 decoder keyed off the auth-service JWKS, pinning {@code iss} (via the
   * default-with-issuer validators, which also enforce {@code exp}) and {@code aud} — the same
   * checks the gateway applies, repeated here so the service is safe even when reached directly.
   */
  @Bean
  public JwtDecoder jwtDecoder(ApplicationSecurityProperties props) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(props.issuer());
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(props.audience()));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
    return decoder;
  }

  /**
   * Maps the JWT {@code roles} claim (e.g. {@code ["CANDIDATE"]}) to {@code ROLE_*} authorities.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, authException) ->
        writeProblem(
            response,
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            "Authentication is required to access this resource.",
            request.getRequestURI());
  }

  @Bean
  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, accessDeniedException) ->
        writeProblem(
            response,
            HttpStatus.FORBIDDEN,
            "Forbidden",
            "You do not have permission to access this resource.",
            request.getRequestURI());
  }

  private void writeProblem(
      HttpServletResponse response, HttpStatus status, String title, String detail, String instance)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    if (instance != null) {
      problem.setInstance(URI.create(instance));
    }
    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
