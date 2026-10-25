package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Request to request membership in a company.
 */
@Schema(name = "CompanyMembershipCreateRequest", description = "Request to join a company as a member")
public record CompanyMembershipCreateRequest(
    @Schema(
            name = "role",
            description = "Role to request within the company",
            example = "RECRUITER",
            required = true)
        CompanyMembershipRole role,
    @Schema(
            name = "userId",
            description = "User ID to invite (for admin invitations)",
            example = "550e8400-e29b-41d4-a716-446655440002")
        UUID userId) {}
