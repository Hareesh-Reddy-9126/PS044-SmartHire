package com.smarthire.recruitment.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.common.web.CorrelationId;
import com.smarthire.recruitment.api.dto.FinalStatusUpdateRequest;
import com.smarthire.recruitment.api.dto.InterviewStatusUpdateRequest;
import com.smarthire.recruitment.api.dto.RecruitmentCreateRequest;
import com.smarthire.recruitment.domain.FinalStatus;
import com.smarthire.recruitment.domain.InterviewStatus;
import com.smarthire.recruitment.infra.ApplicationClient;
import com.smarthire.recruitment.infra.JobClient;
import com.smarthire.recruitment.security.SecurityConfig;
import com.smarthire.recruitment.service.RecruitmentService;
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

@WebMvcTest(RecruitmentController.class)
@Import({SecurityConfig.class, RecruitmentExceptionHandler.class})
@TestPropertySource(
    properties = {
      "smarthire.recruitment.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.recruitment.jwt.issuer=https://smarthire.local/auth",
      "smarthire.recruitment.jwt.audience=smarthire",
      "resilience4j.circuitbreaker.instances.applicationService.slidingWindowSize=3",
      "resilience4j.circuitbreaker.instances.applicationService.minimumNumberOfCalls=3",
      "resilience4j.circuitbreaker.instances.applicationService.failureRateThreshold=50",
      "resilience4j.circuitbreaker.instances.applicationService.waitDurationInOpenState=1000",
      "resilience4j.circuitbreaker.instances.applicationService.permittedNumberOfCallsInHalfOpenState=1",
      "resilience4j.circuitbreaker.instances.jobService.slidingWindowSize=3",
      "resilience4j.circuitbreaker.instances.jobService.minimumNumberOfCalls=3",
      "resilience4j.circuitbreaker.instances.jobService.failureRateThreshold=50",
      "resilience4j.circuitbreaker.instances.jobService.waitDurationInOpenState=1000",
      "resilience4j.circuitbreaker.instances.jobService.permittedNumberOfCallsInHalfOpenState=1"
    })
class RecruitmentExceptionHandlerResilienceTest {

  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID APPLICATION_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID RECRUITMENT_ID =
      UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final String RECRUITER = "recruiter-token";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private RecruitmentService recruitmentService;
  @MockitoBean private CircuitBreakerRegistry circuitBreakerRegistry;
  @MockitoBean private CircuitBreaker applicationCircuitBreaker;
  @MockitoBean private CircuitBreaker jobCircuitBreaker;

  private void givenToken(String token) {
    Jwt decoded =
        switch (token) {
          case RECRUITER -> jwt(token, null, ORG_ID, List.of("RECRUITER"));
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
    when(circuitBreakerRegistry.circuitBreaker("applicationService"))
        .thenReturn(applicationCircuitBreaker);
    when(circuitBreakerRegistry.circuitBreaker("jobService")).thenReturn(jobCircuitBreaker);
    when(applicationCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
    when(jobCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
    // Set correlation ID in MDC for the test
    org.slf4j.MDC.put(CorrelationId.MDC_KEY, "test-correlation-id-123");
  }

  @Test
  @DisplayName(
      "ApplicationServiceUnavailableException returns 503 with RFC 9457 problem+json on shortlist")
  void applicationServiceUnavailableReturns503OnShortlist() throws Exception {
    givenToken(RECRUITER);

    RecruitmentCreateRequest request = new RecruitmentCreateRequest(APPLICATION_ID);

    when(recruitmentService.shortlist(APPLICATION_ID, ORG_ID))
        .thenThrow(
            new ApplicationClient.ApplicationServiceUnavailableException(
                "Application service unavailable"));

    mvc.perform(
            post("/api/v1/recruitments")
                .header("Authorization", "Bearer " + RECRUITER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            jsonPath("$.detail")
                .value("Application service is temporarily unavailable. Please try again later."))
        .andExpect(jsonPath("$.correlationId").value("test-correlation-id-123"));
  }

  @Test
  @DisplayName("JobServiceUnavailableException returns 503 with RFC 9457 problem+json on shortlist")
  void jobServiceUnavailableReturns503OnShortlist() throws Exception {
    givenToken(RECRUITER);

    RecruitmentCreateRequest request = new RecruitmentCreateRequest(APPLICATION_ID);

    when(recruitmentService.shortlist(APPLICATION_ID, ORG_ID))
        .thenThrow(new JobClient.JobServiceUnavailableException("Job service unavailable"));

    mvc.perform(
            post("/api/v1/recruitments")
                .header("Authorization", "Bearer " + RECRUITER)
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

  @Test
  @DisplayName("ApplicationServiceUnavailableException returns 503 on get recruitment")
  void applicationServiceUnavailableReturns503OnGet() throws Exception {
    givenToken(RECRUITER);

    when(recruitmentService.get(RECRUITMENT_ID, null, ORG_ID, false))
        .thenThrow(
            new ApplicationClient.ApplicationServiceUnavailableException(
                "Application service unavailable"));

    mvc.perform(
            get("/api/v1/recruitments/{recruitmentId}", RECRUITMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            jsonPath("$.detail")
                .value("Application service is temporarily unavailable. Please try again later."))
        .andExpect(jsonPath("$.correlationId").value("test-correlation-id-123"));
  }

  @Test
  @DisplayName("JobServiceUnavailableException returns 503 on get recruitment")
  void jobServiceUnavailableReturns503OnGet() throws Exception {
    givenToken(RECRUITER);

    when(recruitmentService.get(RECRUITMENT_ID, null, ORG_ID, false))
        .thenThrow(new JobClient.JobServiceUnavailableException("Job service unavailable"));

    mvc.perform(
            get("/api/v1/recruitments/{recruitmentId}", RECRUITMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            jsonPath("$.detail")
                .value("Job service is temporarily unavailable. Please try again later."))
        .andExpect(jsonPath("$.correlationId").value("test-correlation-id-123"));
  }

  @Test
  @DisplayName("ApplicationServiceUnavailableException returns 503 on interview status update")
  void applicationServiceUnavailableReturns503OnInterviewUpdate() throws Exception {
    givenToken(RECRUITER);

    InterviewStatusUpdateRequest request =
        new InterviewStatusUpdateRequest(InterviewStatus.SCHEDULED);

    when(recruitmentService.updateInterviewStatus(
            RECRUITMENT_ID, InterviewStatus.SCHEDULED, ORG_ID))
        .thenThrow(
            new ApplicationClient.ApplicationServiceUnavailableException(
                "Application service unavailable"));

    mvc.perform(
            patch("/api/v1/recruitments/{recruitmentId}/interview-status", RECRUITMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            jsonPath("$.detail")
                .value("Application service is temporarily unavailable. Please try again later."))
        .andExpect(jsonPath("$.correlationId").value("test-correlation-id-123"));
  }

  @Test
  @DisplayName("JobServiceUnavailableException returns 503 on interview status update")
  void jobServiceUnavailableReturns503OnInterviewUpdate() throws Exception {
    givenToken(RECRUITER);

    InterviewStatusUpdateRequest request =
        new InterviewStatusUpdateRequest(InterviewStatus.SCHEDULED);

    when(recruitmentService.updateInterviewStatus(
            RECRUITMENT_ID, InterviewStatus.SCHEDULED, ORG_ID))
        .thenThrow(new JobClient.JobServiceUnavailableException("Job service unavailable"));

    mvc.perform(
            patch("/api/v1/recruitments/{recruitmentId}/interview-status", RECRUITMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER)
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

  @Test
  @DisplayName("ApplicationServiceUnavailableException returns 503 on final status update")
  void applicationServiceUnavailableReturns503OnFinalUpdate() throws Exception {
    givenToken(RECRUITER);

    FinalStatusUpdateRequest request = new FinalStatusUpdateRequest(FinalStatus.HIRED);

    when(recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID))
        .thenThrow(
            new ApplicationClient.ApplicationServiceUnavailableException(
                "Application service unavailable"));

    mvc.perform(
            patch("/api/v1/recruitments/{recruitmentId}/final-status", RECRUITMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            jsonPath("$.detail")
                .value("Application service is temporarily unavailable. Please try again later."))
        .andExpect(jsonPath("$.correlationId").value("test-correlation-id-123"));
  }

  @Test
  @DisplayName("JobServiceUnavailableException returns 503 on final status update")
  void jobServiceUnavailableReturns503OnFinalUpdate() throws Exception {
    givenToken(RECRUITER);

    FinalStatusUpdateRequest request = new FinalStatusUpdateRequest(FinalStatus.HIRED);

    when(recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID))
        .thenThrow(new JobClient.JobServiceUnavailableException("Job service unavailable"));

    mvc.perform(
            patch("/api/v1/recruitments/{recruitmentId}/final-status", RECRUITMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER)
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
