package com.smarthire.job.api;

import com.smarthire.common.web.CorrelationId;
import com.smarthire.job.domain.IllegalJobStateException;
import com.smarthire.job.service.JobForbiddenException;
import com.smarthire.job.service.JobNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps job-service failures to RFC 9457 Problem Details (ADR-0016).
 *
 * <p>Ordered first on purpose. The shared {@code GlobalExceptionHandler} in {@code common} handles
 * {@code Exception}, which matches everything; whichever advice Spring consults first wins, so
 * without an explicit order these mappings would depend on bean-definition order and could silently
 * degrade into generic 500s.
 *
 * <p>That ordering also matters for security: a {@code @PreAuthorize} denial is raised
 * <em>inside</em> the dispatcher, so Spring Security's {@code ExceptionTranslationFilter} never
 * sees it and cannot turn it into the filter chain's 403. Handling {@link AccessDeniedException}
 * here is what keeps a candidate's attempt to post a job a 403 rather than a 500 that would leak a
 * stack trace through the generic handler.
 *
 * <p>Details are deliberately terse. A denial never names the owning organization, and an
 * authentication failure never says which part of the token was wrong.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JobExceptionHandler {

  @ExceptionHandler(JobNotFoundException.class)
  public ProblemDetail handleNotFound(JobNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
  }

  @ExceptionHandler(JobForbiddenException.class)
  public ProblemDetail handleForbidden(JobForbiddenException ex, HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
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

  /**
   * An illegal lifecycle transition is a conflict with the resource's current state, not a bad
   * request: the payload and the caller were both fine. The current status and attempted action are
   * echoed as extension members so a client can react without parsing prose.
   */
  @ExceptionHandler(IllegalJobStateException.class)
  public ProblemDetail handleIllegalTransition(
      IllegalJobStateException ex, HttpServletRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.CONFLICT, "Illegal job state transition", ex.getMessage(), request);
    if (ex.getCurrentStatus() != null) {
      problem.setProperty("currentStatus", ex.getCurrentStatus().name());
    }
    if (ex.getAttemptedAction() != null) {
      problem.setProperty("attemptedAction", ex.getAttemptedAction());
    }
    return problem;
  }

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
