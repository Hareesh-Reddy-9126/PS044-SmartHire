package com.smarthire.auth.mapper;

import com.smarthire.auth.api.dto.CompanyMembershipRole;
import com.smarthire.auth.domain.CompanyMembership;

/** Mapper for converting between DTO and domain enums for company membership. */
public class CompanyMembershipMapper {

  public static CompanyMembershipRole toDtoRole(CompanyMembership.Role role) {
    return switch (role) {
      case COMPANY_ADMIN -> CompanyMembershipRole.COMPANY_ADMIN;
      case RECRUITER -> CompanyMembershipRole.RECRUITER;
    };
  }

  public static CompanyMembership.Role toDomainRole(CompanyMembershipRole role) {
    return switch (role) {
      case COMPANY_ADMIN -> CompanyMembership.Role.COMPANY_ADMIN;
      case RECRUITER -> CompanyMembership.Role.RECRUITER;
    };
  }
}
