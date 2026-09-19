package com.smarthire.common.authz;

/**
 * Canonical SmartHire role names (RBAC, ADR-0010). Framework-free string constants shared by every
 * service so token issuance, {@code hasRole(...)} checks, and {@link OwnershipChecker} agree on one
 * spelling. {@code CANDIDATE} and {@code RECRUITER} are the Project-44 source actors; {@code ADMIN}
 * is the approved platform-operations role (ADR-0010) — not a recruitment decision-maker.
 *
 * <p>Values are the bare role names carried in the JWT {@code roles} claim. Spring Security's
 * {@code hasRole("X")} matches the authority {@code ROLE_X}; converters prepend {@link #PREFIX}
 * when mapping claim values to granted authorities.
 */
public final class Roles {

  public static final String CANDIDATE = "CANDIDATE";
  public static final String RECRUITER = "RECRUITER";
  public static final String ADMIN = "ADMIN";

  /** Spring Security authority prefix ({@code ROLE_}). */
  public static final String PREFIX = "ROLE_";

  private Roles() {}
}
