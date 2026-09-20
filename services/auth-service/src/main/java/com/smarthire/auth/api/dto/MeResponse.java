package com.smarthire.auth.api.dto;

import java.util.List;

/**
 * Identity of the authenticated caller, sourced from the current database record (not the token),
 * so it reflects live state. Backs the {@code GET /api/v1/auth/me} and {@code /admin/whoami}
 * demonstration endpoints (decision 6). {@code orgId} is null for accounts not scoped to an
 * organization (e.g. candidates, platform admins).
 */
public record MeResponse(String sub, String email, List<String> roles, String orgId) {

  public MeResponse {
    roles = roles == null ? null : List.copyOf(roles);
  }
}
