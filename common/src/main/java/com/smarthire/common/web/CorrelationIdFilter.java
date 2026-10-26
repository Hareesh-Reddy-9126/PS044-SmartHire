package com.smarthire.common.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every inbound request carries a correlation id. Reads {@link CorrelationId#HEADER} if the
 * caller (typically the API gateway) supplied one, otherwise generates a UUID. The id is placed in
 * the SLF4J MDC for the duration of the request and echoed back on the response, then cleared so it
 * cannot leak across pooled threads.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  private static final String UUID_REGEX =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  @Override
  @SuppressFBWarnings(
      value = "HRS_REQUEST_PARAMETER_TO_HTTP_HEADER",
      justification =
          "Correlation ID is read from a header (set by API gateway), validated as UUID format, and echoed back. This is standard correlation ID propagation, not a request parameter injection.")
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = request.getHeader(CorrelationId.HEADER);
    if (!StringUtils.hasText(correlationId) || !correlationId.matches(UUID_REGEX)) {
      correlationId = UUID.randomUUID().toString();
    }
    MDC.put(CorrelationId.MDC_KEY, correlationId);
    response.setHeader(CorrelationId.HEADER, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }
}
