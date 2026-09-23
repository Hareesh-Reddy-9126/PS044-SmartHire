package com.smarthire.job.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Claim extraction must be total. A token that survived signature, issuer and audience verification
 * can still carry a claim of the wrong shape; none of those cases may produce an exception, because
 * a 500 from the claim parser both leaks internals and turns a denial into a server fault.
 */
class JobPrincipalTest {

  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  private static Jwt.Builder token() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("11111111-1111-1111-1111-111111111111")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(900));
  }

  @Test
  void extractsOrgIdAndRoles() {
    JobPrincipal principal =
        JobPrincipal.from(
            token().claim("orgId", ORG_ID.toString()).claim("roles", List.of("RECRUITER")).build());

    assertThat(principal.orgId()).isEqualTo(ORG_ID);
    assertThat(principal.roles()).containsExactly("RECRUITER");
    assertThat(principal.isRecruiter()).isTrue();
    assertThat(principal.isCandidate()).isFalse();
    assertThat(principal.isAdmin()).isFalse();
  }

  @Test
  void recognisesEveryRole() {
    JobPrincipal principal =
        JobPrincipal.from(token().claim("roles", List.of("RECRUITER", "ADMIN")).build());

    assertThat(principal.isRecruiter()).isTrue();
    assertThat(principal.isAdmin()).isTrue();
    assertThat(principal.isCandidate()).isFalse();
  }

  @Test
  void aMissingOrgIdYieldsNoOrganisation() {
    JobPrincipal principal =
        JobPrincipal.from(token().claim("roles", List.of("CANDIDATE")).build());

    assertThat(principal.orgId()).isNull();
    assertThat(principal.isCandidate()).isTrue();
  }

  @Test
  void aBlankOrgIdYieldsNoOrganisation() {
    assertThat(JobPrincipal.from(token().claim("orgId", "   ").build()).orgId()).isNull();
  }

  @Test
  void aMalformedOrgIdIsRejectedRatherThanThrown() {
    JobPrincipal principal = JobPrincipal.from(token().claim("orgId", "not-a-uuid").build());

    // Denied downstream by the ownership check (403), never a 500 out of the claim parser.
    assertThat(principal.orgId()).isNull();
  }

  @Test
  void anOrgIdOfTheWrongTypeIsRejectedRatherThanThrown() {
    JobPrincipal principal = JobPrincipal.from(token().claim("orgId", Map.of("a", "b")).build());

    assertThat(principal.orgId()).isNull();
  }

  @Test
  void missingRolesYieldNoAuthority() {
    JobPrincipal principal = JobPrincipal.from(token().claim("orgId", ORG_ID.toString()).build());

    assertThat(principal.roles()).isEmpty();
    assertThat(principal.isRecruiter()).isFalse();
  }

  @Test
  void rolesOfTheWrongTypeYieldNoAuthority() {
    assertThatNoException()
        .isThrownBy(() -> JobPrincipal.from(token().claim("roles", "RECRUITER").build()));

    assertThat(JobPrincipal.from(token().claim("roles", "RECRUITER").build()).roles()).isEmpty();
  }

  @Test
  void nonStringRoleEntriesAreDropped() {
    JobPrincipal principal =
        JobPrincipal.from(token().claim("roles", List.of("RECRUITER", 42)).build());

    assertThat(principal.roles()).containsExactly("RECRUITER");
  }

  @Test
  void anAbsentTokenIsTheAnonymousPrincipal() {
    JobPrincipal principal = JobPrincipal.from(null);

    assertThat(principal.orgId()).isNull();
    assertThat(principal.roles()).isEmpty();
    assertThat(principal.isRecruiter()).isFalse();
  }

  @Test
  void rolesAreNotMutableThroughTheAccessor() {
    List<String> mutable = new ArrayList<>(List.of("CANDIDATE"));
    JobPrincipal principal = new JobPrincipal(ORG_ID, mutable);

    mutable.add("RECRUITER");

    assertThat(principal.roles()).containsExactly("CANDIDATE");
    assertThat(principal.isRecruiter()).isFalse();
  }
}
