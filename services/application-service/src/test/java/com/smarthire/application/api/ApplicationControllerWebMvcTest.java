package com.smarthire.application.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.application.api.dto.ApplicationCreateRequest;
import com.smarthire.application.api.dto.ApplicationStatusUpdateRequest;
import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import com.smarthire.application.infra.JobClient;
import com.smarthire.application.security.SecurityConfig;
import com.smarthire.application.service.ApplicationForbiddenException;
import com.smarthire.application.service.ApplicationNotFoundException;
import com.smarthire.application.service.ApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP contract and the full authorization matrix for the Application API.
 *
 * <p>The real {@link SecurityConfig} filter chain runs; only the {@link JwtDecoder} is a mock, so
 * tests can mint a token without a live JWKS while every other check — the authenticated-only rule,
 * the {@code @PreAuthorize} role gate, the converter that turns the {@code roles} claim into {@code
 * ROLE_*} authorities — is the production one.
 *
 * <p>{@link ApplicationExceptionHandler} is ordered first
 * ({@code @Order(Ordered.HIGHEST_PRECEDENCE)}), so it wins over {@link GlobalExceptionHandler} for
 * the exceptions it declares.
 */
@WebMvcTest(ApplicationController.class)
@Import({SecurityConfig.class, ApplicationExceptionHandler.class})
@TestPropertySource(
    properties = {
      "smarthire.application.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.application.jwt.issuer=https://smarthire.local/auth",
      "smarthire.application.jwt.audience=smarthire"
    })
class ApplicationControllerWebMvcTest {

  private static final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID APPLICATION_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final String RESUME_LINK = "https://example.com/resume.pdf";

  private static final String CANDIDATE = "candidate-token";
  private static final String RECRUITER = "recruiter-token";
  private static final String ADMIN = "admin-token";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private ApplicationService applicationService;
  @MockitoBean private JobClient jobClient;

  private void givenToken(String token) {
    Jwt decoded =
        switch (token) {
          case CANDIDATE -> jwt(token, CANDIDATE_ID, null, List.of("CANDIDATE"));
          case RECRUITER -> jwt(token, null, ORG_ID, List.of("RECRUITER"));
          case ADMIN -> jwt(token, null, null, List.of("ADMIN"));
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
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900));
    if (orgId != null) {
      builder.claim("orgId", orgId.toString());
    }
    return builder.build();
  }

  private static Application application(ApplicationStatus status) {
    return new Application(APPLICATION_ID, JOB_ID, CANDIDATE_ID, RESUME_LINK, status);
  }

  // ---------------------------------------------------------------- POST /applications

  @Nested
  class CreateApplication {

    @Test
    void candidateCanCreateWhenJobIsOpen() throws Exception {
      givenToken(CANDIDATE);
      ApplicationCreateRequest request = new ApplicationCreateRequest(JOB_ID, RESUME_LINK);
      Application created = application(ApplicationStatus.APPLIED);
      when(applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID)).thenReturn(created);

      mvc.perform(
              post("/api/v1/applications")
                  .header("Authorization", "Bearer " + CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
          .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
          .andExpect(jsonPath("$.candidateId").value(CANDIDATE_ID.toString()))
          .andExpect(jsonPath("$.status").value("APPLIED"));

      verify(applicationService).create(eq(JOB_ID), eq(RESUME_LINK), eq(CANDIDATE_ID));
    }

    @Test
    void recruiterCannotCreate() throws Exception {
      givenToken(RECRUITER);
      ApplicationCreateRequest request = new ApplicationCreateRequest(JOB_ID, RESUME_LINK);

      mvc.perform(
              post("/api/v1/applications")
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreate() throws Exception {
      givenToken(ADMIN);
      ApplicationCreateRequest request = new ApplicationCreateRequest(JOB_ID, RESUME_LINK);

      mvc.perform(
              post("/api/v1/applications")
                  .header("Authorization", "Bearer " + ADMIN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
      ApplicationCreateRequest request = new ApplicationCreateRequest(JOB_ID, RESUME_LINK);

      mvc.perform(
              post("/api/v1/applications")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestBodyReturns400() throws Exception {
      givenToken(CANDIDATE);
      String invalidJson = "{}";

      mvc.perform(
              post("/api/v1/applications")
                  .header("Authorization", "Bearer " + CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidJson))
          .andExpect(status().isBadRequest());
    }

    @Test
    void nonHttpResumeLinkReturns400() throws Exception {
      givenToken(CANDIDATE);
      ApplicationCreateRequest request = new ApplicationCreateRequest(JOB_ID, "not-a-url");

      mvc.perform(
              post("/api/v1/applications")
                  .header("Authorization", "Bearer " + CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(applicationService);
    }
  }

  // ---------------------------------------------------------------- GET /applications/{id}

  @Nested
  class ListApplications {

    @Test
    void candidateCanListOnlyTheirApplications() throws Exception {
      givenToken(CANDIDATE);
      when(applicationService.listForCandidate(eq(CANDIDATE_ID), any()))
          .thenReturn(
              new PageImpl<>(
                  List.of(application(ApplicationStatus.APPLIED)), PageRequest.of(0, 20), 1));

      mvc.perform(get("/api/v1/applications").header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].applicationId").value(APPLICATION_ID.toString()))
          .andExpect(jsonPath("$.page.totalElements").value(1));

      verify(applicationService).listForCandidate(eq(CANDIDATE_ID), any());
    }

    @Test
    void recruiterCannotListCandidateApplications() throws Exception {
      givenToken(RECRUITER);
      mvc.perform(get("/api/v1/applications").header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetApplication {

    @Test
    void candidateCanViewOwnApplication() throws Exception {
      givenToken(CANDIDATE);
      Application app = application(ApplicationStatus.APPLIED);
      when(applicationService.get(APPLICATION_ID, CANDIDATE_ID, null, false)).thenReturn(app);

      mvc.perform(
              get("/api/v1/applications/{applicationId}", APPLICATION_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
          .andExpect(jsonPath("$.candidateId").value(CANDIDATE_ID.toString()))
          .andExpect(jsonPath("$.status").value("APPLIED"));

      verify(applicationService).get(eq(APPLICATION_ID), eq(CANDIDATE_ID), isNull(), eq(false));
    }

    @Test
    void candidateCannotViewOthersApplication() throws Exception {
      givenToken(CANDIDATE);
      when(applicationService.get(eq(APPLICATION_ID), any(), isNull(), eq(false)))
          .thenThrow(new ApplicationNotFoundException("Not found"));

      mvc.perform(
              get("/api/v1/applications/{applicationId}", APPLICATION_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Not Found"))
          .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void recruiterCanViewApplicationForTheirOrgsJob() throws Exception {
      givenToken(RECRUITER);
      Application app = application(ApplicationStatus.APPLIED);
      when(applicationService.get(APPLICATION_ID, null, ORG_ID, false)).thenReturn(app);

      mvc.perform(
              get("/api/v1/applications/{applicationId}", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()));

      verify(applicationService).get(eq(APPLICATION_ID), isNull(), eq(ORG_ID), eq(false));
    }

    @Test
    void adminCanViewAnyApplication() throws Exception {
      givenToken(ADMIN);
      Application app = application(ApplicationStatus.APPLIED);
      when(applicationService.get(APPLICATION_ID, null, null, true)).thenReturn(app);

      mvc.perform(
              get("/api/v1/applications/{applicationId}", APPLICATION_ID)
                  .header("Authorization", "Bearer " + ADMIN))
          .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
      mvc.perform(get("/api/v1/applications/{applicationId}", APPLICATION_ID))
          .andExpect(status().isUnauthorized());
    }
  }

  // ---------------------------------------------------------------- POST
  // /applications/{id}/withdraw

  @Nested
  class WithdrawApplication {

    @Test
    void ownerCanWithdrawAppliedApplication() throws Exception {
      givenToken(CANDIDATE);
      Application app = application(ApplicationStatus.WITHDRAWN);
      when(applicationService.withdraw(APPLICATION_ID, CANDIDATE_ID)).thenReturn(app);

      mvc.perform(
              post("/api/v1/applications/{applicationId}/withdraw", APPLICATION_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void nonOwnerCannotWithdraw() throws Exception {
      givenToken(CANDIDATE);
      when(applicationService.withdraw(eq(APPLICATION_ID), any()))
          .thenThrow(new ApplicationForbiddenException("Forbidden"));

      mvc.perform(
              post("/api/v1/applications/{applicationId}/withdraw", APPLICATION_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Forbidden"))
          .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void cannotWithdrawHiredApplication() throws Exception {
      givenToken(CANDIDATE);
      when(applicationService.withdraw(APPLICATION_ID, CANDIDATE_ID))
          .thenThrow(
              new com.smarthire.application.domain.IllegalApplicationStateException(
                  "Cannot withdraw", ApplicationStatus.HIRED, "withdraw"));

      mvc.perform(
              post("/api/v1/applications/{applicationId}/withdraw", APPLICATION_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"))
          .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
      mvc.perform(post("/api/v1/applications/{applicationId}/withdraw", APPLICATION_ID))
          .andExpect(status().isUnauthorized());
    }
  }

  // ---------------------------------------------------------------- PATCH
  // /applications/{id}/status

  @Nested
  class UpdateApplicationStatus {

    @Test
    void recruiterCanMoveAppliedToUnderReview() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW);
      Application app = application(ApplicationStatus.UNDER_REVIEW);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.UNDER_REVIEW, ORG_ID, false))
          .thenReturn(app);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void recruiterCanMoveUnderReviewToShortlisted() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED);
      Application app = application(ApplicationStatus.SHORTLISTED);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.SHORTLISTED, ORG_ID, false))
          .thenReturn(app);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SHORTLISTED"));
    }

    @Test
    void recruiterCanMoveShortlistedToHired() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.HIRED);
      Application app = application(ApplicationStatus.HIRED);
      when(applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.HIRED, ORG_ID, false))
          .thenReturn(app);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("HIRED"));
    }

    @Test
    void recruiterCanMoveShortlistedToRejected() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.REJECTED);
      Application app = application(ApplicationStatus.REJECTED);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.REJECTED, ORG_ID, false))
          .thenReturn(app);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void adminCanUpdateAnyApplication() throws Exception {
      givenToken(ADMIN);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW);
      Application app = application(ApplicationStatus.UNDER_REVIEW);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.UNDER_REVIEW, null, true))
          .thenReturn(app);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + ADMIN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void recruiterFromWrongOrgCannotUpdate() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.UNDER_REVIEW, ORG_ID, false))
          .thenThrow(
              new ApplicationForbiddenException(
                  "Recruiter does not own the job for this application."));

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Forbidden"))
          .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void candidateCannotUpdateStatus() throws Exception {
      givenToken(CANDIDATE);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + CANDIDATE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }

    @Test
    void cannotSkipToShortlisted() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.SHORTLISTED, ORG_ID, false))
          .thenThrow(
              new com.smarthire.application.domain.IllegalApplicationStateException(
                  "Only an UNDER_REVIEW application can be shortlisted; this application is APPLIED.",
                  ApplicationStatus.APPLIED,
                  "shortlist"));

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"))
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.currentStatus").value("APPLIED"))
          .andExpect(jsonPath("$.attemptedAction").value("shortlist"));
    }

    @Test
    void cannotSkipToHired() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.HIRED);
      when(applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.HIRED, ORG_ID, false))
          .thenThrow(
              new com.smarthire.application.domain.IllegalApplicationStateException(
                  "Only a SHORTLISTED application can be hired; this application is APPLIED.",
                  ApplicationStatus.APPLIED,
                  "hire"));

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"))
          .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cannotTransitionToApplied() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.APPLIED);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.APPLIED, ORG_ID, false))
          .thenThrow(
              new com.smarthire.application.domain.IllegalApplicationStateException(
                  "Cannot transition to APPLIED via recruiter-driven update",
                  ApplicationStatus.APPLIED,
                  "updateStatus"));

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"))
          .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cannotTransitionToWithdrawn() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.WITHDRAWN);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.WITHDRAWN, ORG_ID, false))
          .thenThrow(
              new com.smarthire.application.domain.IllegalApplicationStateException(
                  "Cannot transition to WITHDRAWN via recruiter-driven update",
                  ApplicationStatus.APPLIED,
                  "updateStatus"));

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"))
          .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cannotTransitionHiredToRejected() throws Exception {
      givenToken(RECRUITER);
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.REJECTED);
      when(applicationService.updateStatus(
              APPLICATION_ID, ApplicationStatus.REJECTED, ORG_ID, false))
          .thenThrow(
              new com.smarthire.application.domain.IllegalApplicationStateException(
                  "Only a SHORTLISTED application can be rejected; this application is HIRED.",
                  ApplicationStatus.HIRED,
                  "reject"));

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Illegal State Transition"))
          .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidRequestBodyReturns400() throws Exception {
      givenToken(RECRUITER);
      String invalidJson = "{}";

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .header("Authorization", "Bearer " + RECRUITER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidJson))
          .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
      ApplicationStatusUpdateRequest request =
          new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW);

      mvc.perform(
              patch("/api/v1/applications/{applicationId}/status", APPLICATION_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized());
    }
  }
}
