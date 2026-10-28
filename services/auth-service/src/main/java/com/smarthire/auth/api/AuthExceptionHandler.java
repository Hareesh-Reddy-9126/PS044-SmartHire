package com.smarthire.auth.api;

import com.smarthire.auth.service.AdminRegistrationDeniedException;
import com.smarthire.auth.service.CompanyNotFoundException;
import com.smarthire.auth.service.EmailAlreadyExistsException;
import com.smarthire.auth.service.GoogleSignInUnavailableException;
import com.smarthire.auth.service.InvalidCredentialsException;
import com.smarthire.auth.service.InvalidGoogleCredentialException;
import com.smarthire.auth.service.InvalidRegistrationException;
import com.smarthire.auth.service.InvalidTokenException;
import com.smarthire.auth.service.UserNotFoundException;
import com.smarthire.common.web.CorrelationId;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps auth use-case exceptions to RFC 9457 Problem Details (ADR-0016). Without these more-specific
 * handlers the common {@code GlobalExceptionHandler} would render them all as generic 500s.
 * Credential and token failures deliberately share a single opaque message so responses do not
 * reveal whether an email exists or which half of a credential was wrong.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ProblemDetail handleEmailExists(EmailAlreadyExistsException ex) {
    return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
  }

  @ExceptionHandler(InvalidRegistrationException.class)
  public ProblemDetail handleInvalidRegistration(InvalidRegistrationException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Registration not accepted", ex.getMessage());
  }

  @ExceptionHandler(AdminRegistrationDeniedException.class)
  public ProblemDetail handleAdminRegistrationDenied(AdminRegistrationDeniedException ex) {
    return problem(HttpStatus.FORBIDDEN, "Administrator registration denied", ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", ex.getMessage());
  }

  @ExceptionHandler(InvalidGoogleCredentialException.class)
  public ProblemDetail handleInvalidGoogleCredential(InvalidGoogleCredentialException ex) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Google sign-in failed",
        "The Google credential could not be verified. Please try again.");
  }

  @ExceptionHandler(GoogleSignInUnavailableException.class)
  public ProblemDetail handleGoogleUnavailable(GoogleSignInUnavailableException ex) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in unavailable", ex.getMessage());
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "Invalid token", "The supplied token is invalid.");
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "User not found", ex.getMessage());
  }

  @ExceptionHandler(CompanyNotFoundException.class)
  public ProblemDetail handleCompanyNotFound(CompanyNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "Company not found", ex.getMessage());
  }

  /**
   * Role and ownership denials raised by method security ({@code @Secured}) or by a controller's
   * own authorization check. Without this handler they would surface as 500s through the generic
   * fallback.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(
        HttpStatus.FORBIDDEN, "Forbidden", "Your account is not permitted to perform this action.");
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    return problem;
  }
}
