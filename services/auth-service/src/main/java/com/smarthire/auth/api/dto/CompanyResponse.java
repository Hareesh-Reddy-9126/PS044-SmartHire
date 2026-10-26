package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Response for company operations. */
@Schema(name = "CompanyResponse", description = "Company information response")
public record CompanyResponse(
    @Schema(
            name = "id",
            description = "Company ID",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
    @Schema(name = "name", description = "Company name", example = "Acme Corp", required = true)
        String name,
    @Schema(name = "status", description = "Company status", example = "ACTIVE", required = true)
        String status,
    @Schema(
            name = "createdAt",
            description = "Creation timestamp",
            example = "2024-01-15T10:30:00Z")
        Instant createdAt,
    @Schema(
            name = "approvedAt",
            description = "Approval timestamp",
            example = "2024-01-16T09:00:00Z")
        Instant approvedAt,
    @Schema(
            name = "rejectionReason",
            description = "Rejection reason if rejected",
            example = "Incomplete documentation")
        String rejectionReason) {

  public static CompanyResponse from(com.smarthire.auth.domain.Company company) {
    return new CompanyResponse(
        company.getId(),
        company.getName(),
        company.getStatus().name(),
        company.getCreatedAt(),
        company.getApprovedAt(),
        company.getRejectionReason());
  }
}
