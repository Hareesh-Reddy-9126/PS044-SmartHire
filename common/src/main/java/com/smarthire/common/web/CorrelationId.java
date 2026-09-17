package com.smarthire.common.web;

/**
 * Correlation-id constants shared by every SmartHire service. The gateway generates the id at the
 * edge (or honours an inbound one) and it is propagated through discovery to downstream services
 * and into their logs (MDC) for end-to-end request tracing (architecture §20).
 */
public final class CorrelationId {

  /** HTTP header carrying the correlation id across service hops. */
  public static final String HEADER = "X-Correlation-Id";

  /** SLF4J MDC key under which the correlation id is exposed to log patterns. */
  public static final String MDC_KEY = "correlationId";

  private CorrelationId() {}
}
