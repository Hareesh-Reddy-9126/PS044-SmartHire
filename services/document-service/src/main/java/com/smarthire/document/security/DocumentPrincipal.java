package com.smarthire.document.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Principal extracted from the verified JWT for document-service.
 *
 * <p>Only the claims that are actually used by the service layer are exposed. The JWT {@code sub}
 * is the candidate UUID for candidates; {@code orgId} is the organization UUID for recruiters.
 */
public record DocumentPrincipal(UUID candidateId, UUID orgId, List<String> roles) {

  public DocumentPrincipal {
    roles = roles == null ? null : List.copyOf(roles);
  }

  @Override
  public List<String> roles() {
    return roles == null ? null : List.copyOf(roles);
  }

  /** Creates a principal from a verified JWT. */
  public static DocumentPrincipal from(Jwt jwt) {
    UUID candidateId = parseUuid(jwt.getSubject());
    UUID orgId = parseUuid(jwt.getClaimAsString("orgId"));
    List<String> roles = jwt.getClaimAsStringList("roles");
    return new DocumentPrincipal(candidateId, orgId, roles);
  }

  /** True if the token carries the CANDIDATE role. */
  public boolean isCandidate() {
    return roles != null && roles.contains("CANDIDATE");
  }

  /** True if the token carries the RECRUITER role. */
  public boolean isRecruiter() {
    return roles != null && roles.contains("RECRUITER");
  }

  /** True if the token carries the ADMIN role. */
  public boolean isAdmin() {
    return roles != null && roles.contains("ADMIN");
  }

  private static UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
