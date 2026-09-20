package com.smarthire.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Pins the exact JWT→authority mapping the resource server relies on for role gating: the {@code
 * roles} claim (ADR-0009) is mapped to {@code ROLE_}-prefixed authorities so {@code
 * hasRole('ADMIN')} / {@code @PreAuthorize} work. A change to either the claim name or the prefix
 * would silently break every authorization rule, so it is asserted here in isolation.
 */
class JwtAuthenticationConverterTest {

  private final JwtAuthenticationConverter converter =
      new SecurityConfig(new ObjectMapper()).jwtAuthenticationConverter();

  @Test
  void mapsTheRolesClaimToRolePrefixedAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("11111111-1111-1111-1111-111111111111")
            .claim("roles", List.of("ADMIN", "CANDIDATE"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CANDIDATE");
  }
}
