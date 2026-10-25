package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Response for company membership operations.
 */
@Schema(name = "CompanyMembershipResponse", description = "Company membership response")
public record CompanyMembershipResponse(
    @Schema(
            name = "id",
            description = "Membership ID",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
    @Schema(
            name = "companyId",
            description = "Company ID",
            example = "550e8400-e29b-41d4-a716-446655440001")
        UUID companyId,
    @Schema(
            name = "userId",
            description = "User ID",
            example = "550e8400-e29b-41d4-a716-446655440002")
        UUID userId,
    @Schema(
            name = "role",
            description = "Role within the company",
            example = "RECRUITER",
            required = true)
        CompanyMembershipRole role,
    @Schema(
            name = "status",
            description = "Membership status",
            example = "ACTIVE",
            required = true)
        String status) {}
