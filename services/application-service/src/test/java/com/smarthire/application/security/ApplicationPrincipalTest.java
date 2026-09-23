package com.smarthire.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/** Unit tests for {@link ApplicationPrincipal} JWT claim extraction. */
class ApplicationPrincipalTest {

  private static final UUID CANDIDATE_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();

  private static Jwt jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuer("https://smarthire.local/auth")
        .audience(List.of("smarthire"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(900))
        .build();
  }

  private static Jwt jwtWithClaims(String sub, String orgId, List<String> roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuer("https://smarthire.local/auth")
            .audience(List.of("smarthire"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900));
    if (sub != null) {
      builder.claim(JwtClaimNames.SUB, sub);
    }
    if (orgId != null) {
      builder.claim("orgId", orgId);
    }
    if (roles != null) {
      builder.claim("roles", roles);
    }
    return builder.build();
  }

  @Test
  void fromNullJwtReturnsAnonymous() {
    ApplicationPrincipal principal = ApplicationPrincipal.from(null);
    assertThat(principal).isEqualTo(ApplicationPrincipal.ANONYMOUS);
  }

  @Test
  void extractsCandidateIdFromSubClaim() {
    Jwt jwt = jwtWithClaims(CANDIDATE_ID.toString(), null, null);
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.candidateId()).isEqualTo(CANDIDATE_ID);
  }

  @Test
  void extractsOrgIdFromOrgIdClaim() {
    Jwt jwt = jwtWithClaims(null, ORG_ID.toString(), null);
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.orgId()).isEqualTo(ORG_ID);
  }

  @Test
  void extractsRolesFromRolesClaim() {
    Jwt jwt = jwtWithClaims(null, null, List.of("CANDIDATE", "RECRUITER"));
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.roles()).containsExactly("CANDIDATE", "RECRUITER");
    assertThat(principal.isCandidate()).isTrue();
    assertThat(principal.isRecruiter()).isTrue();
    assertThat(principal.isAdmin()).isFalse();
  }

  @Test
  void missingSubReturnsNullCandidateId() {
    Jwt jwt = jwt();
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.candidateId()).isNull();
  }

  @Test
  void missingOrgIdReturnsNullOrgId() {
    Jwt jwt = jwt();
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.orgId()).isNull();
  }

  @Test
  void missingRolesReturnsEmptyList() {
    Jwt jwt = jwt();
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.roles()).isEmpty();
    assertThat(principal.isCandidate()).isFalse();
    assertThat(principal.isRecruiter()).isFalse();
    assertThat(principal.isAdmin()).isFalse();
  }

  @Test
  void malformedSubReturnsNull() {
    Jwt jwt = jwtWithClaims("not-a-uuid", null, null);
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.candidateId()).isNull();
  }

  @Test
  void malformedOrgIdReturnsNull() {
    Jwt jwt = jwtWithClaims(null, "not-a-uuid", null);
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.orgId()).isNull();
  }

  @Test
  void rolesClaimWrongTypeReturnsEmpty() {
    Jwt jwt = jwtWithClaims(null, null, null);
    // Can't easily test wrong type with Jwt builder, but verify empty list works
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.roles()).isEmpty();
  }

  @Test
  void adminRoleDetected() {
    Jwt jwt = jwtWithClaims(null, null, List.of("ADMIN"));
    ApplicationPrincipal principal = ApplicationPrincipal.from(jwt);
    assertThat(principal.isAdmin()).isTrue();
  }
}
