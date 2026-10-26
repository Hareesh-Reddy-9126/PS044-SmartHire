package com.smarthire.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.auth.service.CompanyNotFoundException;
import com.smarthire.auth.service.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class AuthExceptionHandlerTest {

  @Test
  void invalidTokenDetailsAreGeneric() {
    var problem = new AuthExceptionHandler().handleInvalidToken(new InvalidTokenException("reuse"));

    assertThat(problem.getDetail()).isEqualTo("The supplied token is invalid.");
  }

  @Test
  void roleDenialsBecomeForbiddenNotServerErrors() {
    var problem =
        new AuthExceptionHandler()
            .handleAccessDenied(new AccessDeniedException("requires ROLE_ADMIN"));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(problem.getTitle()).isEqualTo("Forbidden");
    assertThat(problem.getDetail())
        .isEqualTo("Your account is not permitted to perform this action.");
  }

  @Test
  void missingCompaniesBecomeNotFoundNotServerErrors() {
    var problem =
        new AuthExceptionHandler()
            .handleCompanyNotFound(new CompanyNotFoundException("Company not found: 123"));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problem.getTitle()).isEqualTo("Company not found");
  }
}
