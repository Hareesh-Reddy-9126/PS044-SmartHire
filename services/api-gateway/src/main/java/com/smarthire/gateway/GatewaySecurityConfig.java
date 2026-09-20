package com.smarthire.gateway;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

/**
 * Reactive edge security (decisions 4–6). Deny-by-default: only the public allow-list — GET {@code
 * /api/v1/jobs/**} (Inc 0 behaviour, decision 5), the anonymous auth entry points
 * (register/login/refresh, decision 7), the JWKS endpoint and health probes — is anonymous; every
 * other exchange requires a valid RS256 access token, verified offline against the auth-service
 * JWKS with the {@code iss}/{@code aud} claims pinned (ADR-0009). Role-level authorization
 * ({@code @PreAuthorize}) stays in the owning service (defense-in-depth); the gateway is the first
 * line — authentication, revocation ({@link JwtRevocationWebFilter}) and rate limiting.
 *
 * <p>401/403 are rendered as RFC 9457 {@code application/problem+json} (ADR-0016), matching
 * auth-service. Response security headers stay owned by the Inc 0 {@code SecureHeaders} gateway
 * filter — Spring Security's own header writer is disabled to avoid emitting each header twice.
 * CORS preflight is permitted so the existing {@code globalcors} config keeps working ahead of the
 * deny-by-default rule.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter,
      JwtRevocationWebFilter revocationFilter,
      ServerAuthenticationEntryPoint authenticationEntryPoint,
      ServerAccessDeniedHandler accessDeniedHandler) {
    http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .headers(ServerHttpSecurity.HeaderSpec::disable)
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers(HttpMethod.OPTIONS)
                    .permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/jobs/**")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    .pathMatchers(HttpMethod.GET, "/oauth2/jwks")
                    .permitAll()
                    .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyExchange()
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
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterAfter(revocationFilter, SecurityWebFiltersOrder.AUTHENTICATION);
    return http.build();
  }

  /**
   * Offline RS256 decoder keyed off the auth-service JWKS, additionally pinning {@code iss} (via
   * the default-with-issuer validators, which also enforce {@code exp}) and {@code aud}.
   */
  @Bean
  public ReactiveJwtDecoder jwtDecoder(GatewaySecurityProperties props) {
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(props.issuer());
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(props.audience()));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
    return decoder;
  }

  /** Maps the JWT {@code roles} claim (e.g. {@code ["ADMIN"]}) to {@code ROLE_*} authorities. */
  @Bean
  public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return new ReactiveJwtAuthenticationConverterAdapter(converter);
  }

  @Bean
  public ServerAuthenticationEntryPoint authenticationEntryPoint(GatewayProblemWriter problems) {
    return (exchange, ex) ->
        problems.write(
            exchange,
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            "Authentication is required to access this resource.");
  }

  @Bean
  public ServerAccessDeniedHandler accessDeniedHandler(GatewayProblemWriter problems) {
    return (exchange, denied) ->
        problems.write(
            exchange,
            HttpStatus.FORBIDDEN,
            "Forbidden",
            "You do not have permission to access this resource.");
  }

  /**
   * Declared as a bean (rather than {@code @Component}) so the revocation filter is created only
   * within this security configuration and wired explicitly into the chain via {@code
   * addFilterAfter} — it must never be picked up as a free-standing {@code WebFilter}.
   */
  @Bean
  public JwtRevocationWebFilter jwtRevocationWebFilter(
      org.springframework.data.redis.core.ReactiveStringRedisTemplate redis,
      GatewayProblemWriter problems) {
    return new JwtRevocationWebFilter(redis, problems);
  }
}
