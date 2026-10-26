package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request to register a new company. Creates a PENDING_APPROVAL company. */
@Schema(name = "CompanyCreateRequest", description = "Request to register a new company")
public record CompanyCreateRequest(
    @Schema(
            name = "companyName",
            description = "The legal name of the company",
            example = "Acme Corp",
            required = true,
            maxLength = 255)
        String companyName) {}
