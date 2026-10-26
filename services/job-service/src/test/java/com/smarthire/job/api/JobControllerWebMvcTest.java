package com.smarthire.job.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smarthire.common.web.GlobalExceptionHandler;
import com.smarthire.job.domain.IllegalJobStateException;
import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.security.SecurityConfig;
import com.smarthire.job.service.JobForbiddenException;
import com.smarthire.job.service.JobNotFoundException;
import com.smarthire.job.service.JobQuery;
import com.smarthire.job.service.JobService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP contract and the full authorization matrix for the Inc 2 job API.
 *
 * <p>The real {@link SecurityConfig} filter chain runs; only the {@link JwtDecoder} is a mock, so
 * tests can mint a token without a live JWKS while every other check — the anonymous-read rule, the
 * {@code @PreAuthorize} role gate, the converter that turns the {@code roles} claim into {@code
 * ROLE_*} authorities — is the production one.
 *
 * <p>{@link GlobalExceptionHandler} is imported on purpose: it handles {@code Exception}, so
 * importing it here reproduces the advice set of the running application and proves {@link
 * JobExceptionHandler} really does win. Without it the 403 and 409 assertions would pass in the
 * slice and silently become 500s in production.
 */
@WebMvcTest(JobController.class)
@Import({SecurityConfig.class, JobExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(
    properties = {
      "smarthire.job.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.job.jwt.issuer=https://smarthire.local/auth",
      "smarthire.job.jwt.audience=smarthire"
    })
class JobControllerWebMvcTest {

  private static final UUID OWNER_ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER_ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static final String RECRUITER = "recruiter-token";
  private static final String OTHER_RECRUITER = "other-recruiter-token";
  private static final String CANDIDATE = "candidate-token";
  private static final String ADMIN = "admin-token";
  private static final String RECRUITER_NO_ORG = "recruiter-no-org-token";

  private static final String BODY =
      """
      {"companyName":"Acme Corp","role":"Backend Engineer","location":"Remote"}
      """;

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private JobService jobService;

  private static Jwt jwt(String token, UUID orgId, List<String> roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject("33333333-3333-3333-3333-333333333333")
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

  /** Stubs the decoder lazily so each test only pays for the tokens it actually presents. */
  private void givenToken(String token) {
    Jwt decoded =
        switch (token) {
          case RECRUITER -> jwt(token, OWNER_ORG_ID, List.of("RECRUITER"));
          case OTHER_RECRUITER -> jwt(token, OTHER_ORG_ID, List.of("RECRUITER"));
          case CANDIDATE -> jwt(token, null, List.of("CANDIDATE"));
          case ADMIN -> jwt(token, null, List.of("ADMIN"));
          case RECRUITER_NO_ORG -> jwt(token, null, List.of("RECRUITER"));
          default -> throw new IllegalArgumentException(token);
        };
    when(jwtDecoder.decode(token)).thenReturn(decoded);
  }

  private static Job job(JobStatus status) {
    return new Job(JOB_ID, "Acme Corp", "Backend Engineer", "Remote", status, OWNER_ORG_ID);
  }

  private static Page<Job> onePage(Job... jobs) {
    return new PageImpl<>(List.of(jobs), PageRequest.of(0, 20), jobs.length);
  }

  // ---------------------------------------------------------------- public reads

  @Nested
  class PublicReads {

    @Test
    void anonymousListingIsAllowedAndSeesOnlyPublicJobs() throws Exception {
      when(jobService.list(any(), any(), anyBoolean(), any()))
          .thenReturn(onePage(job(JobStatus.OPEN)));

      mvc.perform(get("/api/v1/jobs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].jobId").value(JOB_ID.toString()))
          .andExpect(jsonPath("$.content[0].status").value("OPEN"))
          .andExpect(jsonPath("$.page.totalElements").value(1));

      // No org, not a recruiter: the service can only widen visibility for an authenticated owner.
      verify(jobService).list(any(JobQuery.class), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void anonymousDetailOfAnOpenJobIsAllowed() throws Exception {
      when(jobService.get(JOB_ID, null, false)).thenReturn(job(JobStatus.OPEN));

      mvc.perform(get("/api/v1/jobs/{jobId}", JOB_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void aJobTheCallerMayNotSeeIsReportedAsNotFound() throws Exception {
      when(jobService.get(JOB_ID, null, false))
          .thenThrow(new JobNotFoundException("Job not found: " + JOB_ID));

      mvc.perform(get("/api/v1/jobs/{jobId}", JOB_ID))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Not Found"))
          .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aRecruitersListingCarriesTheirOrganisation() throws Exception {
      givenToken(RECRUITER);
      when(jobService.list(any(), any(), anyBoolean(), any())).thenReturn(onePage());

      mvc.perform(get("/api/v1/jobs").header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk());

      verify(jobService).list(any(JobQuery.class), eq(OWNER_ORG_ID), eq(true), any(Pageable.class));
    }

    @Test
    void aCandidatesListingIsTreatedAsPublic() throws Exception {
      givenToken(CANDIDATE);
      when(jobService.list(any(), any(), anyBoolean(), any())).thenReturn(onePage());

      mvc.perform(get("/api/v1/jobs").header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk());

      verify(jobService).list(any(JobQuery.class), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void aRecruiterCanNarrowTheListingToTheirOwnOrganisation() throws Exception {
      givenToken(RECRUITER);
      when(jobService.list(any(), any(), anyBoolean(), any())).thenReturn(onePage());

      mvc.perform(
              get("/api/v1/jobs")
                  .param("mine", "true")
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk());

      ArgumentCaptor<JobQuery> query = ArgumentCaptor.forClass(JobQuery.class);
      verify(jobService).list(query.capture(), eq(OWNER_ORG_ID), eq(true), any(Pageable.class));
      assertThat(query.getValue().mine()).isTrue();
    }

    @Test
    void aCandidateCannotAskForTheOwnershipView() throws Exception {
      givenToken(CANDIDATE);

      mvc.perform(
              get("/api/v1/jobs")
                  .param("mine", "true")
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.title").value("Forbidden"));

      verify(jobService, never()).list(any(), any(), anyBoolean(), any());
    }
  }

  // ---------------------------------------------------------------- filtering / paging

  @Nested
  class FilteringAndPaging {

    @Test
    void filtersAndPageRequestReachTheService() throws Exception {
      givenToken(RECRUITER);
      when(jobService.list(any(), any(), anyBoolean(), any())).thenReturn(onePage());

      mvc.perform(
              get("/api/v1/jobs")
                  .param("status", "DRAFT")
                  .param("location", "Remote")
                  .param("q", "engineer")
                  .param("page", "2")
                  .param("size", "5")
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk());

      ArgumentCaptor<JobQuery> query = ArgumentCaptor.forClass(JobQuery.class);
      ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
      verify(jobService).list(query.capture(), eq(OWNER_ORG_ID), eq(true), pageable.capture());

      assertThat(query.getValue().status()).isEqualTo(JobStatus.DRAFT);
      assertThat(query.getValue().location()).isEqualTo("Remote");
      assertThat(query.getValue().q()).isEqualTo("engineer");
      assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
      assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void anOversizedPageIsClamped() throws Exception {
      when(jobService.list(any(), any(), anyBoolean(), any())).thenReturn(onePage());

      mvc.perform(get("/api/v1/jobs").param("size", "100000")).andExpect(status().isOk());

      ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
      verify(jobService).list(any(), isNull(), eq(false), pageable.capture());
      assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void aNegativePageFallsBackToTheFirstPage() throws Exception {
      when(jobService.list(any(), any(), anyBoolean(), any())).thenReturn(onePage());

      mvc.perform(get("/api/v1/jobs").param("page", "-5")).andExpect(status().isOk());

      ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
      verify(jobService).list(any(), isNull(), eq(false), pageable.capture());
      assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void anUnknownStatusIsABadRequestNotAServerError() throws Exception {
      mvc.perform(get("/api/v1/jobs").param("status", "ARCHIVED"))
          .andExpect(status().isBadRequest());

      verify(jobService, never()).list(any(), any(), anyBoolean(), any());
    }
  }

  // ---------------------------------------------------------------- unauthenticated writes

  @Nested
  class UnauthenticatedWrites {

    @Test
    void creatingWithoutATokenIs401() throws Exception {
      mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Unauthorized"));

      verify(jobService, never()).create(anyString(), anyString(), anyString(), any());
    }

    @Test
    void lifecycleWithoutATokenIs401() throws Exception {
      mvc.perform(post("/api/v1/jobs/{jobId}/open", JOB_ID)).andExpect(status().isUnauthorized());
      mvc.perform(post("/api/v1/jobs/{jobId}/close", JOB_ID)).andExpect(status().isUnauthorized());
      mvc.perform(post("/api/v1/jobs/{jobId}/reopen", JOB_ID)).andExpect(status().isUnauthorized());

      verify(jobService, never()).open(any(), any());
      verify(jobService, never()).close(any(), any());
      verify(jobService, never()).reopen(any(), any());
    }

    @Test
    void updatingWithoutATokenIs401() throws Exception {
      mvc.perform(
              put("/api/v1/jobs/{jobId}", JOB_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isUnauthorized());

      verify(jobService, never()).update(any(), anyString(), anyString(), anyString(), any());
    }
  }

  // ---------------------------------------------------------------- role matrix

  @Nested
  class RoleMatrix {

    @Test
    void aCandidateMayNotCreate() throws Exception {
      givenToken(CANDIDATE);

      mvc.perform(
              post("/api/v1/jobs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Forbidden"));

      verify(jobService, never()).create(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aCandidateMayNotRunTheLifecycle() throws Exception {
      givenToken(CANDIDATE);

      mvc.perform(
              post("/api/v1/jobs/{jobId}/close", JOB_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isForbidden());

      verify(jobService, never()).close(any(), any());
    }

    @Test
    void anAdminIsNotARecruitmentActor() throws Exception {
      givenToken(ADMIN);

      mvc.perform(
              post("/api/v1/jobs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + ADMIN))
          .andExpect(status().isForbidden());
      mvc.perform(
              put("/api/v1/jobs/{jobId}", JOB_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + ADMIN))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/api/v1/jobs/{jobId}/open", JOB_ID).header("Authorization", "Bearer " + ADMIN))
          .andExpect(status().isForbidden());

      verify(jobService, never()).create(anyString(), anyString(), anyString(), any());
      verify(jobService, never()).update(any(), anyString(), anyString(), anyString(), any());
      verify(jobService, never()).open(any(), any());
    }
  }

  // ---------------------------------------------------------------- recruiter happy path

  @Nested
  class RecruiterWrites {

    @Test
    void createReturns201WithLocationAndTheOwningOrganisationFromTheToken() throws Exception {
      givenToken(RECRUITER);
      when(jobService.create("Acme Corp", "Backend Engineer", "Remote", OWNER_ORG_ID))
          .thenReturn(job(JobStatus.DRAFT));

      mvc.perform(
              post("/api/v1/jobs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", "/api/v1/jobs/" + JOB_ID))
          .andExpect(jsonPath("$.status").value("DRAFT"))
          .andExpect(jsonPath("$.ownerOrgId").value(OWNER_ORG_ID.toString()));
    }

    @Test
    void anOwnerOrgIdInTheRequestBodyIsIgnored() throws Exception {
      givenToken(RECRUITER);
      when(jobService.create(anyString(), anyString(), anyString(), any()))
          .thenReturn(job(JobStatus.DRAFT));

      mvc.perform(
              post("/api/v1/jobs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"companyName":"Acme Corp","role":"Backend Engineer","location":"Remote",
                       "ownerOrgId":"11111111-1111-1111-1111-111111111111"}
                      """)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isCreated());

      // The organisation handed to the service is the token's, never the body's.
      verify(jobService).create("Acme Corp", "Backend Engineer", "Remote", OWNER_ORG_ID);
    }

    @Test
    void updateReturns200() throws Exception {
      givenToken(RECRUITER);
      when(jobService.update(JOB_ID, "Acme Corp", "Backend Engineer", "Remote", OWNER_ORG_ID))
          .thenReturn(job(JobStatus.DRAFT));

      mvc.perform(
              put("/api/v1/jobs/{jobId}", JOB_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.companyName").value("Acme Corp"));
    }

    @Test
    void openReturns200AndTheNewStatus() throws Exception {
      givenToken(RECRUITER);
      when(jobService.open(JOB_ID, OWNER_ORG_ID)).thenReturn(job(JobStatus.OPEN));

      mvc.perform(
              post("/api/v1/jobs/{jobId}/open", JOB_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void closeReturns200AndTheNewStatus() throws Exception {
      givenToken(RECRUITER);
      when(jobService.close(JOB_ID, OWNER_ORG_ID)).thenReturn(job(JobStatus.CLOSED));

      mvc.perform(
              post("/api/v1/jobs/{jobId}/close", JOB_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void reopenReturns200AndTheNewStatus() throws Exception {
      givenToken(RECRUITER);
      when(jobService.reopen(JOB_ID, OWNER_ORG_ID)).thenReturn(job(JobStatus.OPEN));

      mvc.perform(
              post("/api/v1/jobs/{jobId}/reopen", JOB_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void anInvalidPayloadIsRejectedBeforeTheServiceIsCalled() throws Exception {
      givenToken(RECRUITER);

      mvc.perform(
              post("/api/v1/jobs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"companyName\":\"  \",\"role\":\"\",\"location\":\"Remote\"}")
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isBadRequest());

      verify(jobService, never()).create(anyString(), anyString(), anyString(), any());
    }
  }

  // ---------------------------------------------------------------- ownership & state failures

  @Nested
  class DeniedAndConflicting {

    @Test
    void aRecruiterFromAnotherOrganisationIsForbidden() throws Exception {
      givenToken(OTHER_RECRUITER);
      when(jobService.update(JOB_ID, "Acme Corp", "Backend Engineer", "Remote", OTHER_ORG_ID))
          .thenThrow(new JobForbiddenException("This job belongs to another organization."));

      mvc.perform(
              put("/api/v1/jobs/{jobId}", JOB_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + OTHER_RECRUITER))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aRecruiterTokenWithoutAnOrganisationIsDeniedNotDefaulted() throws Exception {
      givenToken(RECRUITER_NO_ORG);
      when(jobService.create(anyString(), anyString(), anyString(), isNull()))
          .thenThrow(new JobForbiddenException("The access token carries no organization."));

      mvc.perform(
              post("/api/v1/jobs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .header("Authorization", "Bearer " + RECRUITER_NO_ORG))
          .andExpect(status().isForbidden());

      // Passed through as null — the controller never substitutes a fallback organisation.
      verify(jobService).create("Acme Corp", "Backend Engineer", "Remote", null);
    }

    @Test
    void anIllegalTransitionIs409WithTheRefusedMove() throws Exception {
      givenToken(RECRUITER);
      when(jobService.open(JOB_ID, OWNER_ORG_ID))
          .thenThrow(
              new IllegalJobStateException(
                  "Only a DRAFT job can be opened; this job is OPEN.", JobStatus.OPEN, "open"));

      mvc.perform(
              post("/api/v1/jobs/{jobId}/open", JOB_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.currentStatus").value("OPEN"))
          .andExpect(jsonPath("$.attemptedAction").value("open"));
    }

    @Test
    void aMissingJobIs404OnMutation() throws Exception {
      givenToken(RECRUITER);
      when(jobService.close(JOB_ID, OWNER_ORG_ID))
          .thenThrow(new JobNotFoundException("Job not found: " + JOB_ID));

      mvc.perform(
              post("/api/v1/jobs/{jobId}/close", JOB_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isNotFound());
    }

    @Test
    void anUnverifiableTokenIs401WithoutRevealingWhy() throws Exception {
      when(jwtDecoder.decode("forged"))
          .thenThrow(
              new org.springframework.security.oauth2.jwt.BadJwtException("signature mismatch"));

      mvc.perform(
              post("/api/v1/jobs/{jobId}/open", JOB_ID).header("Authorization", "Bearer forged"))
          .andExpect(status().isUnauthorized())
          .andExpect(
              jsonPath("$.detail").value("Authentication is required to access this resource."))
          .andExpect(
              jsonPath("$.detail")
                  .value(
                      org.hamcrest.Matchers.not(
                          org.hamcrest.Matchers.containsString("signature"))));
    }
  }
}
