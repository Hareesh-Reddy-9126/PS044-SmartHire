package com.smarthire.recruitment.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authorization-relevant facts of a verified access token: the caller's identity and roles.
 *
 * <p>Built only from a {@link Jwt} that the resource-server filter has already verified (RS256
 * signature against the auth-service JWKS, {@code iss}/{@code aud}/{@code exp} pinned). Nothing
 * else — request body, query string, or gateway header — may produce one.
 *
 * <p>Parsing is total: a missing, malformed or wrongly-typed claim yields an absent identity or an
 * empty role list rather than an exception. A caller presenting a token with a corrupt claim is
 * then denied by the ownership check (403) instead of triggering a 500 that would reveal the
 * parsing failure.
 */
public record RecruitmentPrincipal(UUID candidateId, UUID orgId, List<String> roles) {

  /** The unauthenticated caller: no identity, no roles. */
  public static final RecruitmentPrincipal ANONYMOUS =
      new RecruitmentPrincipal(null, null, List.of());

  public RecruitmentPrincipal {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }

  /** Extracts the principal from a verified token; {@code null} yields {@link #ANONYMOUS}. */
  public static RecruitmentPrincipal from(Jwt jwt) {
    if (jwt == null) {
      return ANONYMOUS;
    }
    return new RecruitmentPrincipal(readCandidateId(jwt), readOrgId(jwt), readRoles(jwt));
  }

  private static UUID readCandidateId(Jwt jwt) {
    if (!(jwt.getClaim("sub") instanceof String raw) || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  private static UUID readOrgId(Jwt jwt) {
    if (!(jwt.getClaim("orgId") instanceof String raw) || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  private static List<String> readRoles(Jwt jwt) {
    if (!(jwt.getClaim("roles") instanceof List<?> claimed)) {
      return List.of();
    }
    return claimed.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  public boolean isCandidate() {
    return roles.contains("CANDIDATE");
  }

  public boolean isRecruiter() {
    return roles.contains("RECRUITER");
  }

  public boolean isAdmin() {
    return roles.contains("ADMIN");
  }
}
