package com.smarthire.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes edge security failures as RFC 9457 {@code application/problem+json} (ADR-0016), matching
 * the shape auth-service emits so a client sees one consistent error contract whether a 401/403 is
 * raised at the gateway or behind it. Reused by the authentication entry point, the access-denied
 * handler and {@link JwtRevocationWebFilter}. The correlation id is included only when the caller
 * supplied one inbound — the gateway's own {@link CorrelationIdGlobalFilter} runs later, after the
 * security chain has already committed an error response.
 */
@Component
public class GatewayProblemWriter {

  private final ObjectMapper objectMapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "objectMapper is an application-scoped Spring singleton supplied by the container via"
              + " constructor injection; retaining the shared JSON serializer is the intended DI"
              + " pattern, not exposure of internal mutable state (governance §29).")
  public GatewayProblemWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String title, String detail) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
    String correlationId =
        exchange
            .getRequest()
            .getHeaders()
            .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
    if (StringUtils.hasText(correlationId)) {
      problem.setProperty("correlationId", correlationId);
    }
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(problem);
    } catch (JsonProcessingException e) {
      return Mono.error(e);
    }
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }
}
