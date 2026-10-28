package com.smarthire.application.api;

import com.smarthire.application.infra.JobClient;
import com.smarthire.application.service.ApplicationForbiddenException;
import com.smarthire.application.service.ApplicationNotFoundException;
import com.smarthire.application.service.DuplicateApplicationException;
import com.smarthire.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Centralized exception handling for RFC 9457 {@code application/problem+json} responses
 * (ADR-0016). Mirrors the pattern in job-service and auth-service.
 *
 * <p>Ordered first on purpose. The shared {@code GlobalExceptionHandler} in {@code common} handles
 * {@code Exception}, which matches everything; whichever advice Spring consults first wins, so
 * without an explicit order these mappings would depend on bean-definition order and could silently
 * degrade into generic 500s.
 *
 * <p>That ordering also matters for security: a {@code @PreAuthorize} denial is raised
 * <em>inside</em> the dispatcher, so Spring Security's {@code ExceptionTranslationFilter} never
 * sees it and cannot turn it into the filter chain's 403. Handling {@link AccessDeniedException}
 * here is what keeps a candidate's attempt to post an application a 403 rather than a 500 that
 * would leak a stack trace through the generic handler.
 */
@RestControllerAdvice
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationExceptionHandler {

  @ExceptionHandler(ApplicationNotFoundException.class)
  public ProblemDetail handleNotFound(ApplicationNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
  }

  @ExceptionHandler(ApplicationForbiddenException.class)
  public ProblemDetail handleForbidden(
      ApplicationForbiddenException ex, HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
  }

  @ExceptionHandler(DuplicateApplicationException.class)
  public ProblemDetail handleDuplicateApplication(
      DuplicateApplicationException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
  }

  /** Role denial from method security ({@code @PreAuthorize}), raised inside the dispatcher. */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Forbidden",
        "You do not have permission to perform this action.",
        request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleUnauthenticated(
      AuthenticationException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Unauthorized",
        "Authentication is required to access this resource.",
        request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
  }

  @ExceptionHandler(JobClient.JobServiceUnavailableException.class)
  public ProblemDetail handleJobServiceUnavailable(
      JobClient.JobServiceUnavailableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Service Unavailable",
        "Job service is temporarily unavailable. Please try again later.",
        request);
  }

  @ExceptionHandler(com.smarthire.application.domain.IllegalApplicationStateException.class)
  public ProblemDetail handleIllegalState(
      com.smarthire.application.domain.IllegalApplicationStateException ex,
      HttpServletRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.CONFLICT, "Illegal State Transition", ex.getMessage(), request);
    if (ex.getCurrentStatus() != null) {
      problem.setProperty("currentStatus", ex.getCurrentStatus().name());
    }
    if (ex.getAttemptedAction() != null) {
      problem.setProperty("attemptedAction", ex.getAttemptedAction());
    }
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return problem(HttpStatus.BAD_REQUEST, "Bad Request", detail, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "Bad Request", "Invalid parameter: " + ex.getName(), request);
  }

  // No catch-all for Exception - let GlobalExceptionHandler handle unexpected errors

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    if (request != null && request.getRequestURI() != null) {
      problem.setInstance(URI.create(request.getRequestURI()));
    }
    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    return problem;
  }
}
