package com.smarthire.document.api;

import com.smarthire.common.web.CorrelationId;
import com.smarthire.document.service.DocumentForbiddenException;
import com.smarthire.document.service.DocumentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Centralized exception handling for the Document API.
 *
 * <p>Order is highest precedence so it wins over the common module's {@link
 * com.smarthire.common.web.GlobalExceptionHandler} for the exceptions it declares.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class DocumentExceptionHandler {

  @ExceptionHandler(DocumentNotFoundException.class)
  public ProblemDetail handleNotFound(DocumentNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
  }

  @ExceptionHandler(DocumentForbiddenException.class)
  public ProblemDetail handleForbidden(DocumentForbiddenException ex, HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid Request", ex.getMessage(), request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleMaxUploadSize(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "File Too Large",
        "Uploaded file exceeds the maximum allowed size.",
        request);
  }

  @ExceptionHandler(IOException.class)
  public ProblemDetail handleIOException(IOException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Storage Error",
        "Failed to process document storage operation.",
        request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return problem(HttpStatus.BAD_REQUEST, "Validation Failed", detail, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid Parameter",
        "Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue(),
        request);
  }

  /** A multipart part the endpoint requires was absent (e.g. no {@code metadata} part). */
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ProblemDetail handleMissingPart(
      MissingServletRequestPartException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid Request",
        "Required multipart part '" + ex.getRequestPartName() + "' is missing.",
        request);
  }

  /** A required query or form parameter was absent. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid Request",
        "Required parameter '" + ex.getParameterName() + "' is missing.",
        request);
  }

  /**
   * An unreadable request body or multipart part — malformed JSON, or a document type the enum does
   * not define. Reported as a client error with a fixed message: the parser's own description would
   * name internal types for what is simply a bad value.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid Request",
        "The request payload could not be read.",
        request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuth(AuthenticationException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Unauthorized",
        "Authentication is required to access this resource.",
        request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Forbidden",
        "You do not have permission to access this resource.",
        request);
  }

  private ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    if (request.getRequestURI() != null) {
      problem.setInstance(URI.create(request.getRequestURI()));
    }
    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    return problem;
  }
}
