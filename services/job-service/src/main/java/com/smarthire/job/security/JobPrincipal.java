package com.smarthire.job.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authorization-relevant facts of a verified access token: the caller's organization and roles.
 *
 * <p>Built only from a {@link Jwt} that the resource-server filter has already verified (RS256
 * signature against the auth-service JWKS, {@code iss}/{@code aud}/{@code exp} pinned). Nothing
 * else — request body, query string, or gateway header — may produce one, which is what makes
 * {@link #orgId()} safe to compare against the stored {@code owner_org_id}.
 *
 * <p>Parsing is total: a missing, malformed or wrongly-typed claim yields an absent organization or
 * an empty role list rather than an exception. A caller presenting a token with a corrupt {@code
 * orgId} is then denied by the ownership check (403) instead of triggering a 500 that would reveal
 * the parsing failure.
 */
public record JobPrincipal(UUID orgId, List<String> roles) {

  /** The unauthenticated caller: no organization, no roles — public visibility only. */
  public static final JobPrincipal ANONYMOUS = new JobPrincipal(null, List.of());

  public JobPrincipal {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }

  /** Extracts the principal from a verified token; {@code null} yields {@link #ANONYMOUS}. */
  public static JobPrincipal from(Jwt jwt) {
    if (jwt == null) {
      return ANONYMOUS;
    }
    return new JobPrincipal(readOrgId(jwt), readRoles(jwt));
  }

  private static UUID readOrgId(Jwt jwt) {
    if (!(jwt.getClaim("orgId") instanceof String raw) || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException malformed) {
      // A token whose orgId is not a UUID cannot own anything; deny rather than fail.
      return null;
    }
  }

  private static List<String> readRoles(Jwt jwt) {
    if (!(jwt.getClaim("roles") instanceof List<?> claimed)) {
      return List.of();
    }
    return claimed.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  public boolean isRecruiter() {
    return roles.contains("RECRUITER");
  }

  public boolean isCandidate() {
    return roles.contains("CANDIDATE");
  }

  public boolean isAdmin() {
    return roles.contains("ADMIN");
  }
}
