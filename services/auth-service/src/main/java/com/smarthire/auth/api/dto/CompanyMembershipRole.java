package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Role within a company membership.
 */
@Schema(name = "CompanyMembershipRole", description = "Role within a company")
public enum CompanyMembershipRole {
  COMPANY_ADMIN,
  RECRUITER
}