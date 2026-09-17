package com.smarthire.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void generatesCorrelationIdWhenHeaderAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) ->
            // The id must be present in the MDC while the chain executes.
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isNotBlank();

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    // ...and cleared afterwards so it cannot leak onto a pooled thread's next request.
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }

  @Test
  void propagatesInboundCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationId.HEADER, "edge-abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("edge-abc-123");
  }
}
