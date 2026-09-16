package com.smarthire.gateway;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stamps every request entering the mesh with a correlation id, honouring an inbound one if
 * present. The id is forwarded to the downstream service (which logs it via {@code
 * CorrelationIdFilter}) and echoed on the response, giving end-to-end traceability (architecture
 * §20).
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

  // Kept in sync with com.smarthire.common.web.CorrelationId.HEADER. Duplicated deliberately so the
  // reactive gateway need not depend on the servlet-oriented smarthire-common library.
  static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String inbound = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
    String correlationId = StringUtils.hasText(inbound) ? inbound : UUID.randomUUID().toString();

    ServerHttpRequest mutated =
        exchange.getRequest().mutate().header(CORRELATION_ID_HEADER, correlationId).build();
    // Set the response header at commit time, AFTER Spring Cloud Gateway has merged the downstream
    // service's own echoed X-Correlation-Id. Setting it eagerly here would leave both in place
    // (the gateway appends downstream response headers), so the id would appear twice; set() at
    // commit collapses them to a single authoritative value.
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
              return Mono.empty();
            });
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
