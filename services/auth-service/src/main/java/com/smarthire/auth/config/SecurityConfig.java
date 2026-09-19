package com.smarthire.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.common.web.CorrelationId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Stateless resource-server security for auth-service. Deny-by-default: only registration, login,
 * refresh, the public JWKS endpoint and health probes are anonymous; everything else needs a valid
 * RS256 access token (decoded by the {@code JwtDecoder} bean from {@link JwtKeyConfig}). This is
 * the defense-in-depth layer behind the gateway (decision 4) — token revocation is also enforced
 * here via the decoder's {@code TokenRevocationValidator}.
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} for role-gated endpoints (e.g.
 * {@code /admin/whoami}). Method security lives here in auth-service rather than the framework-free
 * {@code common} module, so job-service stays fully public (Inc 0 behaviour preserved).
 *
 * <p>401/403 responses are rendered as RFC 9457 {@code application/problem+json} (ADR-0016). The
 * handlers are set on both {@code exceptionHandling} and {@code oauth2ResourceServer} because the
 * resource-server DSL installs its own bearer-token entry point that would otherwise win.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/oauth2/jwks")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
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

  /** Maps the JWT {@code roles} claim (e.g. {@code ["ADMIN"]}) to {@code ROLE_*} authorities. */
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
