package com.smarthire.recruitment.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/** Unit tests for {@link RecruitmentPrincipal} JWT claim extraction. */
class RecruitmentPrincipalTest {

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
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(null);
    assertThat(principal).isEqualTo(RecruitmentPrincipal.ANONYMOUS);
  }

  @Test
  void extractsCandidateIdFromSubClaim() {
    Jwt jwt = jwtWithClaims(CANDIDATE_ID.toString(), null, null);
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.candidateId()).isEqualTo(CANDIDATE_ID);
  }

  @Test
  void extractsOrgIdFromOrgIdClaim() {
    Jwt jwt = jwtWithClaims(null, ORG_ID.toString(), null);
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.orgId()).isEqualTo(ORG_ID);
  }

  @Test
  void extractsRolesFromRolesClaim() {
    Jwt jwt = jwtWithClaims(null, null, List.of("CANDIDATE", "RECRUITER"));
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.roles()).containsExactly("CANDIDATE", "RECRUITER");
    assertThat(principal.isCandidate()).isTrue();
    assertThat(principal.isRecruiter()).isTrue();
    assertThat(principal.isAdmin()).isFalse();
  }

  @Test
  void missingSubReturnsNullCandidateId() {
    Jwt jwt = jwt();
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.candidateId()).isNull();
  }

  @Test
  void missingOrgIdReturnsNullOrgId() {
    Jwt jwt = jwt();
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.orgId()).isNull();
  }

  @Test
  void missingRolesReturnsEmptyList() {
    Jwt jwt = jwt();
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.roles()).isEmpty();
    assertThat(principal.isCandidate()).isFalse();
    assertThat(principal.isRecruiter()).isFalse();
    assertThat(principal.isAdmin()).isFalse();
  }

  @Test
  void malformedSubReturnsNull() {
    Jwt jwt = jwtWithClaims("not-a-uuid", null, null);
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.candidateId()).isNull();
  }

  @Test
  void malformedOrgIdReturnsNull() {
    Jwt jwt = jwtWithClaims(null, "not-a-uuid", null);
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.orgId()).isNull();
  }

  @Test
  void adminRoleDetected() {
    Jwt jwt = jwtWithClaims(null, null, List.of("ADMIN"));
    RecruitmentPrincipal principal = RecruitmentPrincipal.from(jwt);
    assertThat(principal.isAdmin()).isTrue();
  }
}
