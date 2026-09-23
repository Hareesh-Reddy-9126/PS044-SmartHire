package com.smarthire.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.job.domain.IllegalJobStateException;
import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.infra.JobRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Object-level authorization and visibility rules of {@link JobService}. The repository is mocked:
 * what is under test here is which caller is allowed to reach which job, not the SQL. The
 * specification-based listing is covered end-to-end against a real database in {@code JobApiIT}.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  private static final UUID OWNER_ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER_ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID MISSING_JOB_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Mock private JobRepository jobRepository;

  @InjectMocks private JobService jobService;

  private static Job ownedJob(JobStatus status) {
    return new Job(JOB_ID, "Acme Corp", "Backend Engineer", "Remote", status, OWNER_ORG_ID);
  }

  private void givenJob(Job job) {
    when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(job));
    when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  class Create {

    @Test
    void createsADraftOwnedByTheCallersOrganization() {
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

      Job created = jobService.create("Acme Corp", "Backend Engineer", "Remote", OWNER_ORG_ID);

      ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
      verify(jobRepository).save(saved.capture());
      assertThat(saved.getValue().getStatus()).isEqualTo(JobStatus.DRAFT);
      assertThat(saved.getValue().getOwnerOrgId()).isEqualTo(OWNER_ORG_ID);
      assertThat(saved.getValue().getJobId()).isNotNull();
      assertThat(created.getCompanyName()).isEqualTo("Acme Corp");
    }

    @Test
    void refusesWhenTheTokenCarriesNoOrganization() {
      assertThatExceptionOfType(JobForbiddenException.class)
          .isThrownBy(() -> jobService.create("Acme Corp", "Backend Engineer", "Remote", null));

      verify(jobRepository, never()).save(any());
    }
  }

  @Nested
  class OwnershipOnMutations {

    @Test
    void theOwningOrganizationMayUpdate() {
      givenJob(ownedJob(JobStatus.DRAFT));

      Job updated =
          jobService.update(JOB_ID, "Globex Inc", "Data Analyst", "Bengaluru, IN", OWNER_ORG_ID);

      assertThat(updated.getCompanyName()).isEqualTo("Globex Inc");
      assertThat(updated.getRole()).isEqualTo("Data Analyst");
      assertThat(updated.getLocation()).isEqualTo("Bengaluru, IN");
      assertThat(updated.getOwnerOrgId()).isEqualTo(OWNER_ORG_ID);
    }

    @Test
    void anotherOrganizationMayNotUpdate() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.DRAFT)));

      assertThatExceptionOfType(JobForbiddenException.class)
          .isThrownBy(
              () -> jobService.update(JOB_ID, "Evil Corp", "Pwned", "Nowhere", OTHER_ORG_ID));

      verify(jobRepository, never()).save(any());
    }

    @Test
    void anotherOrganizationMayNotOpenCloseOrReopen() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.DRAFT)));

      assertThatExceptionOfType(JobForbiddenException.class)
          .isThrownBy(() -> jobService.open(JOB_ID, OTHER_ORG_ID));
      assertThatExceptionOfType(JobForbiddenException.class)
          .isThrownBy(() -> jobService.close(JOB_ID, OTHER_ORG_ID));
      assertThatExceptionOfType(JobForbiddenException.class)
          .isThrownBy(() -> jobService.reopen(JOB_ID, OTHER_ORG_ID));

      verify(jobRepository, never()).save(any());
    }

    @Test
    void aTokenWithoutAnOrganizationMayNotMutate() {
      assertThatExceptionOfType(JobForbiddenException.class)
          .isThrownBy(() -> jobService.open(JOB_ID, null));

      // Denied before the job is even loaded: a missing orgId is never treated as a wildcard.
      verify(jobRepository, never()).findByJobId(any());
      verify(jobRepository, never()).save(any());
    }

    @Test
    void aMissingJobIsReportedAsNotFound() {
      when(jobRepository.findByJobId(MISSING_JOB_ID)).thenReturn(Optional.empty());

      assertThatExceptionOfType(JobNotFoundException.class)
          .isThrownBy(() -> jobService.open(MISSING_JOB_ID, OWNER_ORG_ID));
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void openMovesDraftToOpen() {
      givenJob(ownedJob(JobStatus.DRAFT));

      assertThat(jobService.open(JOB_ID, OWNER_ORG_ID).getStatus()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    void closeMovesOpenToClosed() {
      givenJob(ownedJob(JobStatus.OPEN));

      assertThat(jobService.close(JOB_ID, OWNER_ORG_ID).getStatus()).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    void reopenMovesClosedToOpen() {
      givenJob(ownedJob(JobStatus.CLOSED));

      assertThat(jobService.reopen(JOB_ID, OWNER_ORG_ID).getStatus()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    void anIllegalTransitionPropagatesFromTheAggregate() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.CLOSED)));

      assertThatExceptionOfType(IllegalJobStateException.class)
          .isThrownBy(() -> jobService.open(JOB_ID, OWNER_ORG_ID));

      verify(jobRepository, never()).save(any());
    }
  }

  @Nested
  class Visibility {

    @Test
    void anOpenJobIsVisibleToAnonymousCallers() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.OPEN)));

      assertThat(jobService.get(JOB_ID, null, false).getJobId()).isEqualTo(JOB_ID);
    }

    @Test
    void aDraftJobIsHiddenFromAnonymousCallers() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.DRAFT)));

      assertThatExceptionOfType(JobNotFoundException.class)
          .isThrownBy(() -> jobService.get(JOB_ID, null, false));
    }

    @Test
    void aClosedJobIsHiddenFromAnotherOrganizationsRecruiter() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.CLOSED)));

      assertThatExceptionOfType(JobNotFoundException.class)
          .isThrownBy(() -> jobService.get(JOB_ID, OTHER_ORG_ID, true));
    }

    @Test
    void aDraftJobIsVisibleToItsOwningRecruiter() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.DRAFT)));

      assertThat(jobService.get(JOB_ID, OWNER_ORG_ID, true).getStatus()).isEqualTo(JobStatus.DRAFT);
    }

    @Test
    void aDraftJobIsHiddenFromACandidateOfTheOwningOrganization() {
      when(jobRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(ownedJob(JobStatus.DRAFT)));

      // Same orgId, but not a recruiter: the org claim alone does not grant back-office visibility.
      assertThatExceptionOfType(JobNotFoundException.class)
          .isThrownBy(() -> jobService.get(JOB_ID, OWNER_ORG_ID, false));
    }
  }

  @Nested
  class Listing {

    @Test
    void appliesTheCallersPageAndReturnsMappedResults() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<Job> page = new PageImpl<>(List.of(ownedJob(JobStatus.OPEN)), pageable, 1);
      when(jobRepository.findAll(ArgumentMatchers.<Specification<Job>>any(), any(Pageable.class)))
          .thenReturn(page);

      Page<Job> result = jobService.list(JobQuery.unfiltered(), null, false, pageable);

      assertThat(result.getContent()).hasSize(1);
      verify(jobRepository)
          .findAll(ArgumentMatchers.<Specification<Job>>any(), any(Pageable.class));
    }

    @Test
    void filtersAreCombinedIntoASingleQuery() {
      Pageable pageable = PageRequest.of(1, 5);
      when(jobRepository.findAll(ArgumentMatchers.<Specification<Job>>any(), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(), pageable, 0));

      jobService.list(
          new JobQuery(JobStatus.DRAFT, "Remote", "engineer"), OWNER_ORG_ID, true, pageable);

      ArgumentCaptor<Pageable> captured = ArgumentCaptor.forClass(Pageable.class);
      verify(jobRepository).findAll(ArgumentMatchers.<Specification<Job>>any(), captured.capture());
      assertThat(captured.getValue().getPageNumber()).isEqualTo(1);
      assertThat(captured.getValue().getPageSize()).isEqualTo(5);
    }
  }
}
