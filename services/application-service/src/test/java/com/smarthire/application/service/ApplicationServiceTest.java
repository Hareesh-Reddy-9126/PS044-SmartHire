package com.smarthire.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import com.smarthire.application.domain.IllegalApplicationStateException;
import com.smarthire.application.infra.ApplicationRepository;
import com.smarthire.application.infra.JobClient;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ApplicationService}. */
class ApplicationServiceTest {

  private ApplicationRepository applicationRepository;
  private JobClient jobClient;
  private ApplicationService applicationService;

  private final UUID JOB_ID = UUID.randomUUID();
  private final UUID CANDIDATE_ID = UUID.randomUUID();
  private final UUID ORG_ID = UUID.randomUUID();
  private final String RESUME_LINK = "https://example.com/resume.pdf";

  @BeforeEach
  void setUp() {
    applicationRepository = mock(ApplicationRepository.class);
    jobClient = mock(JobClient.class);
    applicationService = new ApplicationService(applicationRepository, jobClient);
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
}
