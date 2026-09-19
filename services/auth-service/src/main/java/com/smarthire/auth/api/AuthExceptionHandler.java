package com.smarthire.auth.api;

import com.smarthire.auth.service.EmailAlreadyExistsException;
import com.smarthire.auth.service.InvalidCredentialsException;
import com.smarthire.auth.service.InvalidTokenException;
import com.smarthire.common.web.CorrelationId;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", ex.getMessage());
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "Invalid token", ex.getMessage());
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
