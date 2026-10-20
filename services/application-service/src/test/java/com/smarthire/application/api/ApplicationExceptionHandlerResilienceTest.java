package com.smarthire.application.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.application.api.dto.ApplicationCreateRequest;
import com.smarthire.application.infra.JobClient;
import com.smarthire.application.security.SecurityConfig;
import com.smarthire.application.service.ApplicationService;
import com.smarthire.common.web.CorrelationId;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApplicationController.class)
@Import({SecurityConfig.class, ApplicationExceptionHandler.class})
@TestPropertySource(
    properties = {
      "smarthire.application.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.application.jwt.issuer=https://smarthire.local/auth",
      "smarthire.application.jwt.audience=smarthire",
      "resilience4j.circuitbreaker.instances.jobService.slidingWindowSize=3",
      "resilience4j.circuitbreaker.instances.jobService.minimumNumberOfCalls=3",
      "resilience4j.circuitbreaker.instances.jobService.failureRateThreshold=50",
      "resilience4j.circuitbreaker.instances.jobService.waitDurationInOpenState=1000",
      "resilience4j.circuitbreaker.instances.jobService.permittedNumberOfCallsInHalfOpenState=1"
    })
class ApplicationExceptionHandlerResilienceTest {

  private static final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String RESUME_LINK = "https://example.com/resume.pdf";
  private static final String CANDIDATE = "candidate-token";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private ApplicationService applicationService;
  @MockitoBean private CircuitBreakerRegistry circuitBreakerRegistry;
  @MockitoBean private CircuitBreaker circuitBreaker;

  private void givenToken(String token) {
    Jwt decoded =
        switch (token) {
          case CANDIDATE -> jwt(token, CANDIDATE_ID, null, List.of("CANDIDATE"));
          default -> throw new IllegalArgumentException(token);
        };
    when(jwtDecoder.decode(token)).thenReturn(decoded);
  }

  private static Jwt jwt(String token, UUID candidateId, UUID orgId, List<String> roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject(candidateId != null ? candidateId.toString() : "not-a-uuid")
            .issuer("https://smarthire.local/auth")
            .audience(List.of("smarthire"))
            .claim("roles", roles)
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(900));
    if (orgId != null) {
      builder.claim("orgId", orgId.toString());
    }
    return builder.build();
  }

  @BeforeEach
  void setUp() {
    when(circuitBreakerRegistry.circuitBreaker("jobService")).thenReturn(circuitBreaker);
    when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
    // Set correlation ID in MDC for the test
    org.slf4j.MDC.put(CorrelationId.MDC_KEY, "test-correlation-id-123");
  }

  @Test
  @DisplayName("JobServiceUnavailableException returns 503 with RFC 9457 problem+json")
  void jobServiceUnavailableReturns503() throws Exception {
    givenToken(CANDIDATE);

    ApplicationCreateRequest request = new ApplicationCreateRequest(JOB_ID, RESUME_LINK);

    when(applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID))
        .thenThrow(new JobClient.JobServiceUnavailableException("Job service unavailable"));

    mvc.perform(
            post("/api/v1/applications")
                .header("Authorization", "Bearer " + CANDIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            jsonPath("$.detail")
                .value("Job service is temporarily unavailable. Please try again later."))
        .andExpect(jsonPath("$.correlationId").value("test-correlation-id-123"));
  }
}
