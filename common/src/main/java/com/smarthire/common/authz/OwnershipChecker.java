package com.smarthire.common.authz;

import java.util.Set;

/**
 * Object-level authorization helper (RBAC + ownership, ADR-0010, governance §16). Pure,
 * framework-free decision logic so it is trivially unit-testable (governance §22) and reusable by
 * any service without dragging Spring Security onto services that do not need it.
 *
 * <p>Access rule, evaluated by role:
 *
 * <ul>
 *   <li><b>ADMIN</b> — platform operations: access to any resource.
 *   <li><b>RECRUITER</b> — access only to resources owned by the recruiter's own organization
 *       (same-org).
 *   <li><b>CANDIDATE</b> — access only to resources the candidate owns (self).
 * </ul>
 *
 * <p>Inc 1 has no object-ownership endpoint yet (its protected routes are role-only); this is the
 * shared helper the Inc 2+ resource endpoints and {@code @PreAuthorize} expressions build on. It is
 * exercised now by unit tests so the rule is locked before any endpoint depends on it.
 */
public final class OwnershipChecker {

  private OwnershipChecker() {}

  /**
   * The acting principal's authorization-relevant identity, derived from the verified JWT claims
   * ({@code sub}, {@code orgId}, {@code roles}).
   */
  public record AccessPrincipal(String subject, String orgId, Set<String> roles) {

    public AccessPrincipal {
      roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
      return roles.contains(role);
    }
  }

  /**
   * The owning identities of a target resource. A resource is typically owned by an organization
   * (e.g. a job or an application seen by a recruiter) and/or by a candidate (e.g. the candidate's
   * own application). Either may be {@code null} when not applicable.
   */
  public record ResourceOwner(String ownerOrgId, String ownerCandidateId) {}

  /**
   * @return {@code true} if {@code principal} may access {@code resource} under the RBAC +
   *     ownership rule; {@code false} for a {@code null} principal/resource or a principal with no
   *     qualifying role.
   */
  public static boolean canAccess(AccessPrincipal principal, ResourceOwner resource) {
    if (principal == null || resource == null) {
      return false;
    }
    if (principal.hasRole(Roles.ADMIN)) {
      return true;
    }
    if (principal.hasRole(Roles.RECRUITER)) {
      return resource.ownerOrgId() != null && resource.ownerOrgId().equals(principal.orgId());
    }
    if (principal.hasRole(Roles.CANDIDATE)) {
      return resource.ownerCandidateId() != null
          && resource.ownerCandidateId().equals(principal.subject());
    }
    return false;
  }
}
