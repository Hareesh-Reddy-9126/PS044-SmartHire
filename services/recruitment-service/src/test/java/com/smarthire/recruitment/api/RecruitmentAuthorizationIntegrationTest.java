package com.smarthire.recruitment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.recruitment.api.dto.FinalStatusUpdateRequest;
import com.smarthire.recruitment.api.dto.InterviewScheduleRequest;
import com.smarthire.recruitment.api.dto.InterviewStatusUpdateRequest;
import com.smarthire.recruitment.api.dto.RecruitmentCreateRequest;
import com.smarthire.recruitment.domain.FinalStatus;
import com.smarthire.recruitment.domain.InterviewStatus;
import com.smarthire.recruitment.infra.ApplicationClient;
import com.smarthire.recruitment.infra.JobClient;
import com.smarthire.recruitment.security.SecurityConfig;
import com.smarthire.recruitment.service.RecruitmentNotFoundException;
import com.smarthire.recruitment.service.RecruitmentScheduleException;
import com.smarthire.recruitment.service.RecruitmentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * Integration tests for recruitment authorization boundaries.
 *
 * <p>Verifies HTTP status codes for cross-organization and cross-role access attempts. Uses
 * {@code @WebMvcTest} with the real security configuration so method security
 * ({@code @PreAuthorize}) and the exception handler are exercised, while mocking the service layer
 * to avoid requiring a database or Testcontainers.
 */
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
class RecruitmentAuthorizationIntegrationTest {

  private static final UUID ORG_ID_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID_B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID RECRUITMENT_ID =
      UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final String TOKEN_RECRUITER_A = "recruiter-a-token";
  private static final String TOKEN_RECRUITER_B = "recruiter-b-token";
  private static final String TOKEN_CANDIDATE = "candidate-token";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private RecruitmentService recruitmentService;
  @MockitoBean private ApplicationClient applicationClient;
  @MockitoBean private JobClient jobClient;
  @MockitoBean private CircuitBreakerRegistry circuitBreakerRegistry;
  @MockitoBean private CircuitBreaker applicationCircuitBreaker;
  @MockitoBean private CircuitBreaker jobCircuitBreaker;

  @BeforeEach
  void setUp() {
    when(circuitBreakerRegistry.circuitBreaker("applicationService"))
        .thenReturn(applicationCircuitBreaker);
    when(circuitBreakerRegistry.circuitBreaker("jobService")).thenReturn(jobCircuitBreaker);
    when(applicationCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    when(jobCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
  }

  private void givenToken(String token) {
    Jwt decoded =
        switch (token) {
          case TOKEN_RECRUITER_A -> jwt(token, null, ORG_ID_A, List.of("RECRUITER"));
          case TOKEN_RECRUITER_B -> jwt(token, null, ORG_ID_B, List.of("RECRUITER"));
          case TOKEN_CANDIDATE -> jwt(token, CANDIDATE_ID, null, List.of("CANDIDATE"));
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

  @Nested
  @DisplayName("Candidate role authorization")
  class CandidateAuthorization {

    @Test
    @DisplayName("CANDIDATE calling PATCH /recruitments/{id}/schedule receives 403")
    void candidateCannotScheduleInterview() throws Exception {
      givenToken(TOKEN_CANDIDATE);

      InterviewScheduleRequest request =
          new InterviewScheduleRequest(
              java.time.LocalDate.of(2099, 4, 15),
              java.time.LocalTime.of(10, 30),
              "Alex Recruiter",
              "Room 4B");

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/schedule", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("CANDIDATE calling PATCH /recruitments/{id}/interview-status receives 403")
    void candidateCannotUpdateInterviewStatus() throws Exception {
      givenToken(TOKEN_CANDIDATE);

      InterviewStatusUpdateRequest request =
          new InterviewStatusUpdateRequest(InterviewStatus.SCHEDULED);

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/interview-status", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("CANDIDATE calling PATCH /recruitments/{id}/final-status receives 403")
    void candidateCannotUpdateFinalStatus() throws Exception {
      givenToken(TOKEN_CANDIDATE);

      FinalStatusUpdateRequest request = new FinalStatusUpdateRequest(FinalStatus.HIRED);

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/final-status", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("CANDIDATE calling POST /recruitments receives 403")
    void candidateCannotShortlist() throws Exception {
      givenToken(TOKEN_CANDIDATE);

      RecruitmentCreateRequest request = new RecruitmentCreateRequest(UUID.randomUUID());

      mvc.perform(
              post("/api/v1/recruitments")
                  .header("Authorization", "Bearer " + TOKEN_CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Forbidden"));
    }
  }

  @Nested
  @DisplayName("Cross-organization authorization")
  class CrossOrgAuthorization {

    @BeforeEach
    void setUpServiceMocks() {
      // Configure service to throw RecruitmentNotFoundException for cross-org access
      // simulating the loadOwned() check in RecruitmentService
      when(recruitmentService.scheduleInterview(
              eq(RECRUITMENT_ID), any(), any(), any(), any(), eq(ORG_ID_B)))
          .thenThrow(new RecruitmentNotFoundException("Recruitment not found: " + RECRUITMENT_ID));

      when(recruitmentService.get(eq(RECRUITMENT_ID), eq((UUID) null), eq(ORG_ID_B), eq(false)))
          .thenThrow(new RecruitmentNotFoundException("Recruitment not found: " + RECRUITMENT_ID));

      when(recruitmentService.updateInterviewStatus(eq(RECRUITMENT_ID), any(), eq(ORG_ID_B)))
          .thenThrow(new RecruitmentNotFoundException("Recruitment not found: " + RECRUITMENT_ID));

      when(recruitmentService.updateFinalStatus(eq(RECRUITMENT_ID), any(), eq(ORG_ID_B)))
          .thenThrow(new RecruitmentNotFoundException("Recruitment not found: " + RECRUITMENT_ID));

      // For scheduling with valid date but wrong org
      when(recruitmentService.scheduleInterview(
              eq(RECRUITMENT_ID), any(), any(), any(), any(), eq(ORG_ID_B)))
          .thenThrow(new RecruitmentNotFoundException("Recruitment not found: " + RECRUITMENT_ID));
    }

    @Test
    @DisplayName("Recruiter from org B reading org A's recruitment receives 404")
    void crossOrgRecruiterCannotRead() throws Exception {
      givenToken(TOKEN_RECRUITER_B);

      mvc.perform(
              get("/api/v1/recruitments/{recruitmentId}", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_RECRUITER_B))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("Recruiter from org B scheduling org A's recruitment receives 404")
    void crossOrgRecruiterCannotSchedule() throws Exception {
      givenToken(TOKEN_RECRUITER_B);

      InterviewScheduleRequest request =
          new InterviewScheduleRequest(
              java.time.LocalDate.of(2099, 4, 15),
              java.time.LocalTime.of(10, 30),
              "Alex Recruiter",
              "Room 4B");

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/schedule", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_RECRUITER_B)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName(
        "Recruiter from org B updating interview status on org A's recruitment receives 404")
    void crossOrgRecruiterCannotUpdateInterviewStatus() throws Exception {
      givenToken(TOKEN_RECRUITER_B);

      InterviewStatusUpdateRequest request =
          new InterviewStatusUpdateRequest(InterviewStatus.SCHEDULED);

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/interview-status", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_RECRUITER_B)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("Recruiter from org B updating final status on org A's recruitment receives 404")
    void crossOrgRecruiterCannotUpdateFinalStatus() throws Exception {
      givenToken(TOKEN_RECRUITER_B);

      FinalStatusUpdateRequest request = new FinalStatusUpdateRequest(FinalStatus.HIRED);

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/final-status", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_RECRUITER_B)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Not Found"));
    }
  }

  @Nested
  @DisplayName("Input validation")
  class InputValidation {

    @Test
    @DisplayName("Past interview date returns 400")
    void pastInterviewDateRejected() throws Exception {
      givenToken(TOKEN_RECRUITER_A);

      // Service throws RecruitmentScheduleException for past dates
      when(recruitmentService.scheduleInterview(
              eq(RECRUITMENT_ID),
              eq(java.time.LocalDate.of(2000, 1, 1)),
              any(),
              any(),
              any(),
              eq(ORG_ID_A)))
          .thenThrow(
              new RecruitmentScheduleException("Interview date and time must be in the future."));

      InterviewScheduleRequest request =
          new InterviewScheduleRequest(
              java.time.LocalDate.of(2000, 1, 1),
              java.time.LocalTime.NOON,
              "Alex Recruiter",
              "Room 4B");

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/schedule", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_RECRUITER_A)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Duplicate scheduling on already scheduled recruitment returns 409")
    void duplicateSchedulingRejected() throws Exception {
      givenToken(TOKEN_RECRUITER_A);

      // Service throws IllegalRecruitmentStateException for already scheduled
      when(recruitmentService.scheduleInterview(
              eq(RECRUITMENT_ID), any(), any(), any(), any(), eq(ORG_ID_A)))
          .thenThrow(
              new com.smarthire.recruitment.domain.IllegalRecruitmentStateException(
                  "Interview already scheduled.",
                  InterviewStatus.SCHEDULED.name(),
                  FinalStatus.PENDING.name(),
                  "scheduleInterview"));

      InterviewScheduleRequest request =
          new InterviewScheduleRequest(
              java.time.LocalDate.of(2099, 4, 15),
              java.time.LocalTime.of(10, 30),
              "Alex Recruiter",
              "Room 4B");

      mvc.perform(
              patch("/api/v1/recruitments/{recruitmentId}/schedule", RECRUITMENT_ID)
                  .header("Authorization", "Bearer " + TOKEN_RECRUITER_A)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"));
    }
  }
}
