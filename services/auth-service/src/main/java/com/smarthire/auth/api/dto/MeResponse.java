package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Identity of the authenticated caller, sourced from the current database record (not the token),
 * so it reflects live state. Backs the {@code GET /api/v1/auth/me} and {@code /admin/whoami}
 * demonstration endpoints (decision 6). {@code orgId} is null for accounts not scoped to an
 * organization (e.g. candidates, platform admins).
 */
@Schema(name = "MeResponse", description = "Authenticated user identity and roles")
public record MeResponse(
    @Schema(
            name = "sub",
            description = "User subject ID (UUID)",
            example = "550e8400-e29b-41d4-a716-446655440000")
        String sub,
    @Schema(name = "email", description = "User's email address", example = "user@example.com")
        String email,
    @Schema(name = "roles", description = "List of user roles", example = "[\"CANDIDATE\"]")
        List<String> roles,
    @Schema(
            name = "orgId",
            description = "Organization ID if user belongs to an organization, null otherwise",
            example = "550e8400-e29b-41d4-a716-446655440000")
        String orgId) {

  public MeResponse {
    roles = roles == null ? null : List.copyOf(roles);
  }
}
