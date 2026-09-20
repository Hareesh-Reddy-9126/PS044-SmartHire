package com.smarthire.common.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.common.authz.OwnershipChecker.AccessPrincipal;
import com.smarthire.common.authz.OwnershipChecker.ResourceOwner;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the object-level authorization rule (governance §22: every authz rule tested for
 * owner / non-owner / cross-org). Pure logic — no Spring context.
 */
class OwnershipCheckerTest {

  private static final String ORG_A = "org-a";
  private static final String ORG_B = "org-b";
  private static final String CANDIDATE_1 = "cand-1";
  private static final String CANDIDATE_2 = "cand-2";

  @Test
  void admin_can_access_any_resource() {
    AccessPrincipal admin = new AccessPrincipal("admin-1", null, Set.of(Roles.ADMIN));

    assertThat(OwnershipChecker.canAccess(admin, new ResourceOwner(ORG_A, CANDIDATE_1))).isTrue();
    assertThat(OwnershipChecker.canAccess(admin, new ResourceOwner(ORG_B, CANDIDATE_2))).isTrue();
    assertThat(OwnershipChecker.canAccess(admin, new ResourceOwner(null, null))).isTrue();
  }

  @Test
  void recruiter_can_access_own_org_resource() {
    AccessPrincipal recruiter = new AccessPrincipal("rec-1", ORG_A, Set.of(Roles.RECRUITER));

    assertThat(OwnershipChecker.canAccess(recruiter, new ResourceOwner(ORG_A, null))).isTrue();
    assertThat(OwnershipChecker.canAccess(recruiter, new ResourceOwner(ORG_A, CANDIDATE_2)))
        .isTrue();
  }

  @Test
  void recruiter_cannot_access_other_org_resource() {
    AccessPrincipal recruiter = new AccessPrincipal("rec-1", ORG_A, Set.of(Roles.RECRUITER));

    assertThat(OwnershipChecker.canAccess(recruiter, new ResourceOwner(ORG_B, null))).isFalse();
  }

  @Test
  void recruiter_with_null_org_cannot_access_org_resource() {
    AccessPrincipal recruiter = new AccessPrincipal("rec-1", null, Set.of(Roles.RECRUITER));

    assertThat(OwnershipChecker.canAccess(recruiter, new ResourceOwner(ORG_A, null))).isFalse();
    // A resource with no owning org is never matched by the same-org rule either.
    assertThat(OwnershipChecker.canAccess(recruiter, new ResourceOwner(null, null))).isFalse();
  }

  @Test
  void candidate_can_access_own_resource() {
    AccessPrincipal candidate = new AccessPrincipal(CANDIDATE_1, null, Set.of(Roles.CANDIDATE));

    assertThat(OwnershipChecker.canAccess(candidate, new ResourceOwner(null, CANDIDATE_1)))
        .isTrue();
    assertThat(OwnershipChecker.canAccess(candidate, new ResourceOwner(ORG_A, CANDIDATE_1)))
        .isTrue();
  }

  @Test
  void candidate_cannot_access_other_candidates_resource() {
    AccessPrincipal candidate = new AccessPrincipal(CANDIDATE_1, null, Set.of(Roles.CANDIDATE));

    assertThat(OwnershipChecker.canAccess(candidate, new ResourceOwner(null, CANDIDATE_2)))
        .isFalse();
  }

  @Test
  void candidate_cannot_access_org_only_resource() {
    AccessPrincipal candidate = new AccessPrincipal(CANDIDATE_1, null, Set.of(Roles.CANDIDATE));

    assertThat(OwnershipChecker.canAccess(candidate, new ResourceOwner(ORG_A, null))).isFalse();
  }

  @Test
  void principal_with_no_known_role_is_denied() {
    AccessPrincipal noRole = new AccessPrincipal("x", ORG_A, Set.of());

    assertThat(OwnershipChecker.canAccess(noRole, new ResourceOwner(ORG_A, "x"))).isFalse();
  }

  @Test
  void null_principal_or_resource_is_denied() {
    AccessPrincipal admin = new AccessPrincipal("admin-1", null, Set.of(Roles.ADMIN));

    assertThat(OwnershipChecker.canAccess(null, new ResourceOwner(ORG_A, null))).isFalse();
    assertThat(OwnershipChecker.canAccess(admin, null)).isFalse();
  }

  @Test
  void roles_defensively_copied_and_null_safe() {
    AccessPrincipal nullRoles = new AccessPrincipal("x", ORG_A, null);

    assertThat(nullRoles.roles()).isEmpty();
    assertThat(OwnershipChecker.canAccess(nullRoles, new ResourceOwner(ORG_A, null))).isFalse();
  }
}
