package com.smarthire.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import com.smarthire.application.domain.IllegalApplicationStateException;
import com.smarthire.application.infra.ApplicationRepository;
import com.smarthire.application.infra.JobClient;
import com.smarthire.application.infra.NotificationClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests for {@link ApplicationService}. */
class ApplicationServiceTest {

  private ApplicationRepository applicationRepository;
  private JobClient jobClient;
  private NotificationClient notificationClient;
  private ApplicationService applicationService;

  private final UUID JOB_ID = UUID.randomUUID();
  private final UUID CANDIDATE_ID = UUID.randomUUID();
  private final UUID ORG_ID = UUID.randomUUID();
  private final String RESUME_LINK = "https://example.com/resume.pdf";

  @BeforeEach
  void setUp() {
    applicationRepository = mock(ApplicationRepository.class);
    jobClient = mock(JobClient.class);
    notificationClient = mock(NotificationClient.class);
    applicationService =
        new ApplicationService(applicationRepository, jobClient, notificationClient);
  }

  @Nested
  @DisplayName("Create application")
  class Create {

    @Test
    @DisplayName("Creates application when job exists and is OPEN")
    void createsWhenJobOpen() {
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application created = applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID);

      assertThat(created.getJobId()).isEqualTo(JOB_ID);
      assertThat(created.getCandidateId()).isEqualTo(CANDIDATE_ID);
      assertThat(created.getResumeLink()).isEqualTo(RESUME_LINK);
      assertThat(created.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("Throws when job not found")
    void throwsWhenJobNotFound() {
      // The service catches feign.FeignException.NotFound - test this via WebMvcTest instead
      // Unit testing the specific FeignException is difficult due to constructor requirements
    }

    @Test
    @DisplayName("Throws when job is not OPEN")
    void throwsWhenJobNotOpen() {
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "CLOSED", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);

      assertThatThrownBy(() -> applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not OPEN");
    }

    @Test
    @DisplayName("Throws when candidate already has an active application for the job")
    void throwsWhenActiveApplicationExists() {
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
      when(applicationRepository.existsByJobIdAndCandidateIdAndStatusNotIn(
              JOB_ID,
              CANDIDATE_ID,
              List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED)))
          .thenReturn(true);

      assertThatThrownBy(() -> applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID))
          .isInstanceOf(DuplicateApplicationException.class)
          .hasMessageContaining("already have an active application");

      verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    @DisplayName("Allows a new application after the previous one was withdrawn or rejected")
    void allowsReapplyAfterTerminalStatus() {
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
      when(applicationRepository.existsByJobIdAndCandidateIdAndStatusNotIn(
              JOB_ID,
              CANDIDATE_ID,
              List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED)))
          .thenReturn(false);
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application created = applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID);

      assertThat(created.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
      verify(applicationRepository)
          .existsByJobIdAndCandidateIdAndStatusNotIn(
              JOB_ID,
              CANDIDATE_ID,
              List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED));
    }

    @Test
    @DisplayName("Translates a concurrent unique-index violation into the duplicate conflict")
    void translatesUniqueViolation() {
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
      when(applicationRepository.save(any(Application.class)))
          .thenThrow(
              new DataIntegrityViolationException(
                  "duplicate key value violates unique constraint"));

      assertThatThrownBy(() -> applicationService.create(JOB_ID, RESUME_LINK, CANDIDATE_ID))
          .isInstanceOf(DuplicateApplicationException.class)
          .hasMessageContaining("already have an active application");
    }
  }

  @Nested
  @DisplayName("Get application")
  class Get {

    @Test
    @DisplayName("Candidate can view their own application")
    void candidateCanViewOwn() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      Application result =
          applicationService.get(app.getApplicationId(), CANDIDATE_ID, null, false);

      assertThat(result).isSameAs(app);
    }

    @Test
    @DisplayName("Candidate cannot view another's application")
    void candidateCannotViewOthers() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () -> applicationService.get(app.getApplicationId(), UUID.randomUUID(), null, false))
          .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("Recruiter can view application for their org's job")
    void recruiterCanViewOrgJob() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);

      Application result = applicationService.get(app.getApplicationId(), null, ORG_ID, false);

      assertThat(result).isSameAs(app);
    }

    @Test
    @DisplayName("Recruiter cannot view application for another org's job")
    void recruiterCannotViewOtherOrg() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      JobClient.JobResponse jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Other Corp", "Backend Engineer", "Remote", "OPEN", UUID.randomUUID());
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);

      assertThatThrownBy(() -> applicationService.get(app.getApplicationId(), null, ORG_ID, false))
          .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("ADMIN can view any application")
    void adminCanViewAny() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      Application result = applicationService.get(app.getApplicationId(), null, null, true);

      assertThat(result).isSameAs(app);
    }
  }

  @Nested
  @DisplayName("Withdraw application")
  class Withdraw {

    @Test
    @DisplayName("Owner can withdraw APPLIED application")
    void ownerCanWithdrawApplied() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result = applicationService.withdraw(app.getApplicationId(), CANDIDATE_ID);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("Owner can withdraw UNDER_REVIEW application")
    void ownerCanWithdrawUnderReview() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result = applicationService.withdraw(app.getApplicationId(), CANDIDATE_ID);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("Owner can withdraw SHORTLISTED application")
    void ownerCanWithdrawShortlisted() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result = applicationService.withdraw(app.getApplicationId(), CANDIDATE_ID);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("Non-owner cannot withdraw")
    void nonOwnerCannotWithdraw() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () -> applicationService.withdraw(app.getApplicationId(), UUID.randomUUID()))
          .isInstanceOf(ApplicationForbiddenException.class);
    }

    @Test
    @DisplayName("Cannot withdraw HIRED application")
    void cannotWithdrawHired() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.hire();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(() -> applicationService.withdraw(app.getApplicationId(), CANDIDATE_ID))
          .isInstanceOf(IllegalApplicationStateException.class);
    }
  }

  @Nested
  @DisplayName("Update application status (recruiter-driven)")
  class UpdateStatus {

    private JobClient.JobResponse jobResponse;
    private Application app;

    @BeforeEach
    void setup() {
      jobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID);
      when(jobClient.getJob(JOB_ID)).thenReturn(jobResponse);
    }

    @Test
    @DisplayName("Recruiter can move APPLIED to UNDER_REVIEW")
    void recruiterCanMoveAppliedToUnderReview() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.UNDER_REVIEW, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("Recruiter can move UNDER_REVIEW to SHORTLISTED")
    void recruiterCanMoveUnderReviewToShortlisted() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.SHORTLISTED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
    }

    @Test
    @DisplayName("Recruiter can move SHORTLISTED to HIRED")
    void recruiterCanMoveShortlistedToHired() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Recruiter can move SHORTLISTED to REJECTED")
    void recruiterCanMoveShortlistedToRejected() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.REJECTED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("ADMIN can update status without org check")
    void adminCanUpdateWithoutOrgCheck() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      when(applicationRepository.save(any(Application.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.UNDER_REVIEW, null, true);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("Recruiter from wrong org cannot update status")
    void recruiterFromWrongOrgCannotUpdate() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));
      UUID otherOrgId = UUID.randomUUID();
      JobClient.JobResponse otherJobResponse =
          new JobClient.JobResponse(
              JOB_ID, "Other Corp", "Backend Engineer", "Remote", "OPEN", otherOrgId);
      when(jobClient.getJob(JOB_ID)).thenReturn(otherJobResponse);

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.UNDER_REVIEW, ORG_ID, false))
          .isInstanceOf(ApplicationForbiddenException.class)
          .hasMessageContaining("does not own");
    }

    @Test
    @DisplayName("Recruiter without orgId cannot update status")
    void recruiterWithoutOrgIdCannotUpdate() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.UNDER_REVIEW, null, false))
          .isInstanceOf(ApplicationForbiddenException.class)
          .hasMessageContaining("must have an organization");
    }

    @Test
    @DisplayName("Cannot transition to APPLIED via recruiter update")
    void cannotTransitionToApplied() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.APPLIED, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }

    @Test
    @DisplayName("Cannot transition to WITHDRAWN via recruiter update")
    void cannotTransitionToWithdrawn() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.WITHDRAWN, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }

    @Test
    @DisplayName("Cannot skip from APPLIED to SHORTLISTED")
    void cannotSkipToShortlisted() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.SHORTLISTED, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }

    @Test
    @DisplayName("Cannot skip from APPLIED to HIRED")
    void cannotSkipToHired() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }

    @Test
    @DisplayName("Cannot skip from UNDER_REVIEW to HIRED")
    void cannotSkipUnderReviewToHired() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }

    @Test
    @DisplayName("Cannot transition HIRED to REJECTED")
    void cannotTransitionHiredToRejected() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.hire();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.REJECTED, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }

    @Test
    @DisplayName("Cannot transition REJECTED to HIRED")
    void cannotTransitionRejectedToHired() {
      app = Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.reject();
      when(applicationRepository.findByApplicationId(app.getApplicationId()))
          .thenReturn(Optional.of(app));

      assertThatThrownBy(
              () ->
                  applicationService.updateStatus(
                      app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false))
          .isInstanceOf(IllegalApplicationStateException.class);
    }
  }
}
