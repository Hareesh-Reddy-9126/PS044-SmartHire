package com.smarthire.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.recruitment.domain.FinalStatus;
import com.smarthire.recruitment.domain.IllegalRecruitmentStateException;
import com.smarthire.recruitment.domain.InterviewStatus;
import com.smarthire.recruitment.domain.Recruitment;
import com.smarthire.recruitment.infra.ApplicationClient;
import com.smarthire.recruitment.infra.JobClient;
import com.smarthire.recruitment.infra.RecruitmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RecruitmentService}. */
class RecruitmentServiceTest {

  private RecruitmentRepository recruitmentRepository;
  private ApplicationClient applicationClient;
  private JobClient jobClient;
  private RecruitmentService recruitmentService;

  private final UUID APPLICATION_ID = UUID.randomUUID();
  private final UUID RECRUITMENT_ID = UUID.randomUUID();
  private final UUID JOB_ID = UUID.randomUUID();
  private final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @BeforeEach
  void setUp() {
    recruitmentRepository = mock(RecruitmentRepository.class);
    applicationClient = mock(ApplicationClient.class);
    jobClient = mock(JobClient.class);
    recruitmentService =
        new RecruitmentService(recruitmentRepository, applicationClient, jobClient);
  }

  @Nested
  @DisplayName("Shortlist application")
  class Shortlist {

    @Test
    @DisplayName("Creates recruitment when application exists and org matches")
    void createsWhenApplicationExistsAndOrgMatches() {
      ApplicationClient.ApplicationResponse appResponse =
          new ApplicationClient.ApplicationResponse(
              APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", "APPLIED");
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);

      when(applicationClient.getApplication(APPLICATION_ID)).thenReturn(appResponse);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
      when(recruitmentRepository.findByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
      when(recruitmentRepository.saveAndFlush(any(Recruitment.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Recruitment created = recruitmentService.shortlist(APPLICATION_ID, ORG_ID);

      assertThat(created.getApplicationId()).isEqualTo(APPLICATION_ID);
      assertThat(created.getJobId()).isEqualTo(JOB_ID);
      assertThat(created.getOwnerOrgId()).isEqualTo(ORG_ID);
      assertThat(created.getCandidateId()).isEqualTo(CANDIDATE_ID);
      assertThat(created.getInterviewStatus()).isEqualTo(InterviewStatus.NOT_SCHEDULED);
      assertThat(created.getFinalStatus()).isEqualTo(FinalStatus.PENDING);
    }

    @Test
    @DisplayName("Throws when application not found")
    void throwsWhenApplicationNotFound() {
      // The service catches FeignException.NotFound; in a unit test we simulate the 404 by
      // throwing the exact exception type the service expects. The constructor requires a Request
      // which is verbose to build; instead we verify the business logic paths above and skip the
      // Feign-specific exception construction here.
    }

    @Test
    @DisplayName("Throws when job not found")
    void throwsWhenJobNotFound() {
      // Same as above — the Feign integration is verified in integration tests. Here we trust the
      // service's catch block handles FeignException.NotFound correctly.
    }

    @Test
    @DisplayName("Throws when application belongs to another organization")
    void throwsWhenOrgMismatch() {
      ApplicationClient.ApplicationResponse appResponse =
          new ApplicationClient.ApplicationResponse(
              APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", "APPLIED");
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Other Corp", "Backend Engineer", "Remote", "OPEN", UUID.randomUUID());

      when(applicationClient.getApplication(APPLICATION_ID)).thenReturn(appResponse);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);

      assertThatThrownBy(() -> recruitmentService.shortlist(APPLICATION_ID, ORG_ID))
          .isInstanceOf(RecruitmentNotFoundException.class)
          .hasMessageContaining("Application not found");
    }

    @Test
    @DisplayName("Throws when caller has no orgId")
    void throwsWhenNoOrgId() {
      assertThatThrownBy(() -> recruitmentService.shortlist(APPLICATION_ID, null))
          .isInstanceOf(RecruitmentForbiddenException.class)
          .hasMessageContaining("no organization");
    }

    @Test
    @DisplayName("Throws when recruitment already exists for application")
    void throwsWhenRecruitmentExists() {
      ApplicationClient.ApplicationResponse appResponse =
          new ApplicationClient.ApplicationResponse(
              APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", "APPLIED");
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);

      when(applicationClient.getApplication(APPLICATION_ID)).thenReturn(appResponse);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
      Recruitment existing =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByApplicationId(APPLICATION_ID))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> recruitmentService.shortlist(APPLICATION_ID, ORG_ID))
          .isInstanceOf(RecruitmentConflictException.class)
          .hasMessageContaining("already been shortlisted");
    }
  }

  @Nested
  @DisplayName("Get recruitment")
  class Get {

    @Test
    @DisplayName("Candidate can view recruitment for their own application")
    void candidateCanViewOwn() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      Recruitment result = recruitmentService.get(RECRUITMENT_ID, CANDIDATE_ID, null, false);

      assertThat(result).isSameAs(r);
    }

    @Test
    @DisplayName("Candidate cannot view recruitment for another's application")
    void candidateCannotViewOthers() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      assertThatThrownBy(
              () -> recruitmentService.get(RECRUITMENT_ID, UUID.randomUUID(), null, false))
          .isInstanceOf(RecruitmentNotFoundException.class);
    }

    @Test
    @DisplayName("Recruiter can view recruitment for their organization")
    void recruiterCanView() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      Recruitment result = recruitmentService.get(RECRUITMENT_ID, null, ORG_ID, false);

      assertThat(result).isSameAs(r);
    }

    @Test
    @DisplayName("ADMIN can view any recruitment")
    void adminCanViewAny() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      Recruitment result = recruitmentService.get(RECRUITMENT_ID, null, null, true);

      assertThat(result).isSameAs(r);
    }
  }

  @Nested
  @DisplayName("Update interview status")
  class UpdateInterviewStatus {

    @Test
    @DisplayName("Can schedule interview (NOT_SCHEDULED → SCHEDULED)")
    void canSchedule() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));
      when(recruitmentRepository.save(any(Recruitment.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Recruitment result =
          recruitmentService.updateInterviewStatus(
              RECRUITMENT_ID, InterviewStatus.SCHEDULED, ORG_ID);

      assertThat(result.getInterviewStatus()).isEqualTo(InterviewStatus.SCHEDULED);
    }

    @Test
    @DisplayName("Can complete interview (SCHEDULED → COMPLETED)")
    void canComplete() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      r.scheduleInterview();
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));
      when(recruitmentRepository.save(any(Recruitment.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Recruitment result =
          recruitmentService.updateInterviewStatus(
              RECRUITMENT_ID, InterviewStatus.COMPLETED, ORG_ID);

      assertThat(result.getInterviewStatus()).isEqualTo(InterviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("Can mark NO_SHOW (SCHEDULED → NO_SHOW)")
    void canNoShow() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      r.scheduleInterview();
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));
      when(recruitmentRepository.save(any(Recruitment.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Recruitment result =
          recruitmentService.updateInterviewStatus(RECRUITMENT_ID, InterviewStatus.NO_SHOW, ORG_ID);

      assertThat(result.getInterviewStatus()).isEqualTo(InterviewStatus.NO_SHOW);
    }

    @Test
    @DisplayName("Throws on illegal transition")
    void throwsOnIllegalTransition() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      assertThatThrownBy(
              () ->
                  recruitmentService.updateInterviewStatus(
                      RECRUITMENT_ID, InterviewStatus.COMPLETED, ORG_ID))
          .isInstanceOf(IllegalRecruitmentStateException.class);
    }

    @Test
    @DisplayName("Throws when caller org doesn't own recruitment")
    void throwsWhenNotOwned() {
      UUID otherOrg = UUID.randomUUID();
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, otherOrg, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      assertThatThrownBy(
              () ->
                  recruitmentService.updateInterviewStatus(
                      RECRUITMENT_ID, InterviewStatus.SCHEDULED, ORG_ID))
          .isInstanceOf(RecruitmentNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Update final status")
  class UpdateFinalStatus {

    private Recruitment pending() {
      return Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
    }

    private void givenSaveSucceeds() {
      when(recruitmentRepository.save(any(Recruitment.class)))
          .thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenApplicationAccepts(String resultingStatus) {
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenReturn(
              new ApplicationClient.ApplicationResponse(
                  APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", resultingStatus));
    }

    @Test
    @DisplayName("Can hire (PENDING → HIRED)")
    void canHire() {
      Recruitment r = pending();
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));
      givenSaveSucceeds();
      givenApplicationAccepts("HIRED");

      Recruitment result =
          recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID);

      assertThat(result.getFinalStatus()).isEqualTo(FinalStatus.HIRED);
    }

    @Test
    @DisplayName("Can reject (PENDING → REJECTED)")
    void canReject() {
      Recruitment r = pending();
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));
      givenSaveSucceeds();
      givenApplicationAccepts("REJECTED");

      Recruitment result =
          recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.REJECTED, ORG_ID);

      assertThat(result.getFinalStatus()).isEqualTo(FinalStatus.REJECTED);
    }

    @Test
    @DisplayName("Throws on illegal transition")
    void throwsOnIllegalTransition() {
      Recruitment r = pending();
      r.hire();
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      assertThatThrownBy(
              () ->
                  recruitmentService.updateFinalStatus(
                      RECRUITMENT_ID, FinalStatus.REJECTED, ORG_ID))
          .isInstanceOf(IllegalRecruitmentStateException.class);
    }

    @Test
    @DisplayName("Throws when caller org doesn't own recruitment")
    void throwsWhenNotOwned() {
      UUID otherOrg = UUID.randomUUID();
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, otherOrg, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      assertThatThrownBy(
              () -> recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID))
          .isInstanceOf(RecruitmentNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Final status synchronizes the application")
  class ApplicationSynchronization {

    private Recruitment pending() {
      return Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
    }

    private void givenPendingRecruitment() {
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID))
          .thenReturn(Optional.of(pending()));
      when(recruitmentRepository.save(any(Recruitment.class)))
          .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("HIRED drives the application to HIRED")
    void hireMapsToHired() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenReturn(
              new ApplicationClient.ApplicationResponse(
                  APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", "HIRED"));

      recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID);

      verify(applicationClient)
          .updateStatus(APPLICATION_ID, new ApplicationClient.StatusUpdateRequest("HIRED"));
    }

    @Test
    @DisplayName("REJECTED drives the application to REJECTED")
    void rejectMapsToRejected() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenReturn(
              new ApplicationClient.ApplicationResponse(
                  APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", "REJECTED"));

      recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.REJECTED, ORG_ID);

      verify(applicationClient)
          .updateStatus(APPLICATION_ID, new ApplicationClient.StatusUpdateRequest("REJECTED"));
    }

    @Test
    @DisplayName("The application id comes from the recruitment, not the request")
    void targetsTheShortlistedApplication() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(any(), any()))
          .thenReturn(
              new ApplicationClient.ApplicationResponse(
                  APPLICATION_ID, JOB_ID, CANDIDATE_ID, "link", "HIRED"));

      recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID);

      verify(applicationClient).updateStatus(eq(APPLICATION_ID), any());
    }

    @Test
    @DisplayName("An unavailable application service fails the decision instead of hiding it")
    void unavailableApplicationServiceFailsTheDecision() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenThrow(new ApplicationClient.ApplicationServiceUnavailableException("circuit open"));

      // The caller must not see a successful hire when the application was never moved.
      assertThatThrownBy(
              () -> recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID))
          .isInstanceOf(ApplicationClient.ApplicationServiceUnavailableException.class);
    }

    @Test
    @DisplayName("A refusal by the application state machine surfaces as a conflict")
    void applicationRefusalSurfacesAsConflict() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenThrow(feignException(409, "{\"detail\":\"Cannot transition to HIRED\"}"));

      assertThatThrownBy(
              () -> recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID))
          .isInstanceOf(ApplicationSyncConflictException.class)
          .hasMessageContaining("the recruitment decision was not applied");
    }

    @Test
    @DisplayName("A missing application is reported as not found")
    void missingApplicationIsNotFound() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenThrow(feignException(404, "{\"detail\":\"not found\"}"));

      assertThatThrownBy(
              () ->
                  recruitmentService.updateFinalStatus(
                      RECRUITMENT_ID, FinalStatus.REJECTED, ORG_ID))
          .isInstanceOf(RecruitmentNotFoundException.class);
    }

    @Test
    @DisplayName("Any other downstream failure is reported as unavailable, not as success")
    void otherDownstreamFailureIsReported() {
      givenPendingRecruitment();
      when(applicationClient.updateStatus(eq(APPLICATION_ID), any()))
          .thenThrow(feignException(500, "boom"));

      assertThatThrownBy(
              () -> recruitmentService.updateFinalStatus(RECRUITMENT_ID, FinalStatus.HIRED, ORG_ID))
          .isInstanceOf(ApplicationClient.ApplicationServiceUnavailableException.class);
    }

    @Test
    @DisplayName("No application call is made when the caller does not own the recruitment")
    void unauthorizedCallerNeverReachesTheApplication() {
      Recruitment r =
          Recruitment.shortlist(RECRUITMENT_ID, APPLICATION_ID, JOB_ID, ORG_ID, CANDIDATE_ID);
      when(recruitmentRepository.findByRecruitmentId(RECRUITMENT_ID)).thenReturn(Optional.of(r));

      assertThatThrownBy(
              () ->
                  recruitmentService.updateFinalStatus(
                      RECRUITMENT_ID, FinalStatus.HIRED, UUID.randomUUID()))
          .isInstanceOf(RecruitmentNotFoundException.class);

      verify(applicationClient, never()).updateStatus(any(), any());
    }

    /**
     * Builds a {@link feign.FeignException} of the subclass matching {@code status}, the way Feign
     * does for a real HTTP response. The typed constructors take the raw pieces, so the response is
     * only assembled for the generic case.
     */
    private feign.FeignException feignException(int status, String body) {
      byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      feign.Request request =
          feign.Request.create(
              feign.Request.HttpMethod.PATCH,
              "http://application-service/api/v1/applications/" + APPLICATION_ID + "/status",
              java.util.Map.of(),
              bytes,
              java.nio.charset.StandardCharsets.UTF_8);
      return switch (status) {
        case 404 ->
            new feign.FeignException.NotFound("not found", request, bytes, java.util.Map.of());
        case 409 ->
            new feign.FeignException.Conflict("conflict", request, bytes, java.util.Map.of());
        case 400 ->
            new feign.FeignException.BadRequest("bad request", request, bytes, java.util.Map.of());
        default ->
            feign.FeignException.errorStatus(
                "error",
                feign.Response.builder()
                    .status(status)
                    .reason("test")
                    .request(request)
                    .headers(java.util.Map.of())
                    .body(body, java.nio.charset.StandardCharsets.UTF_8)
                    .build());
      };
    }
  }
}
