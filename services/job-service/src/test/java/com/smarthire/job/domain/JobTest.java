package com.smarthire.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Lifecycle rules of the Job aggregate (D5). The legal moves are {@code DRAFT → OPEN}, {@code OPEN
 * → CLOSED} and {@code CLOSED → OPEN}; the parameterised cases assert that <em>every</em> other
 * combination of (status, transition) is refused, so a new status added later cannot quietly become
 * a legal source state.
 */
class JobTest {

  private static final UUID OWNER_ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER_ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private static Job jobWith(JobStatus status) {
    return new Job(
        UUID.randomUUID(), "Acme Corp", "Backend Engineer", "Remote", status, OWNER_ORG_ID);
  }

  @Test
  void createDraftStartsInDraftAndIsOwnedByTheCreatingOrganization() {
    Job job =
        Job.createDraft(UUID.randomUUID(), "Acme Corp", "Backend Engineer", "Remote", OWNER_ORG_ID);

    assertThat(job.getStatus()).isEqualTo(JobStatus.DRAFT);
    assertThat(job.getOwnerOrgId()).isEqualTo(OWNER_ORG_ID);
    assertThat(job.isPubliclyVisible()).isFalse();
  }

  @Nested
  class LegalTransitions {

    @Test
    void draftOpens() {
      Job job = jobWith(JobStatus.DRAFT);

      job.open();

      assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
      assertThat(job.isPubliclyVisible()).isTrue();
    }

    @Test
    void openCloses() {
      Job job = jobWith(JobStatus.OPEN);

      job.close();

      assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
      assertThat(job.isPubliclyVisible()).isFalse();
    }

    @Test
    void closedReopens() {
      Job job = jobWith(JobStatus.CLOSED);

      job.reopen();

      assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    void theWholeCycleRuns() {
      Job job = jobWith(JobStatus.DRAFT);

      assertThatNoException()
          .isThrownBy(
              () -> {
                job.open();
                job.close();
                job.reopen();
                job.close();
              });
      assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
    }
  }

  @Nested
  class IllegalTransitions {

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void openFailsFromAnythingButDraft(JobStatus status) {
      Job job = jobWith(status);

      assertThatExceptionOfType(IllegalJobStateException.class)
          .isThrownBy(job::open)
          .satisfies(
              ex -> {
                assertThat(ex.getCurrentStatus()).isEqualTo(status);
                assertThat(ex.getAttemptedAction()).isEqualTo("open");
              });
      assertThat(job.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void closeFailsFromAnythingButOpen(JobStatus status) {
      Job job = jobWith(status);

      assertThatExceptionOfType(IllegalJobStateException.class)
          .isThrownBy(job::close)
          .satisfies(
              ex -> {
                assertThat(ex.getCurrentStatus()).isEqualTo(status);
                assertThat(ex.getAttemptedAction()).isEqualTo("close");
              });
      assertThat(job.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
    void reopenFailsFromAnythingButClosed(JobStatus status) {
      Job job = jobWith(status);

      assertThatExceptionOfType(IllegalJobStateException.class)
          .isThrownBy(job::reopen)
          .satisfies(
              ex -> {
                assertThat(ex.getCurrentStatus()).isEqualTo(status);
                assertThat(ex.getAttemptedAction()).isEqualTo("reopen");
              });
      assertThat(job.getStatus()).isEqualTo(status);
    }

    @Test
    void transitionsAreStrictNotIdempotent() {
      Job job = jobWith(JobStatus.OPEN);

      // A second open() is a conflict, not a silent no-op: the caller is told the job already
      // moved.
      assertThatExceptionOfType(IllegalJobStateException.class).isThrownBy(job::open);
    }
  }

  @Nested
  class Ownership {

    @Test
    void isOwnedByTheCreatingOrganizationOnly() {
      Job job = jobWith(JobStatus.DRAFT);

      assertThat(job.isOwnedBy(OWNER_ORG_ID)).isTrue();
      assertThat(job.isOwnedBy(OTHER_ORG_ID)).isFalse();
    }

    @Test
    void anAbsentOrganizationNeverOwns() {
      Job job = jobWith(JobStatus.DRAFT);

      assertThat(job.isOwnedBy(null)).isFalse();
    }

    @Test
    void editingDetailsChangesNeitherStatusNorOwnership() {
      Job job = jobWith(JobStatus.OPEN);

      job.updateDetails("Globex Inc", "Data Analyst", "Bengaluru, IN");

      assertThat(job.getCompanyName()).isEqualTo("Globex Inc");
      assertThat(job.getRole()).isEqualTo("Data Analyst");
      assertThat(job.getLocation()).isEqualTo("Bengaluru, IN");
      assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
      assertThat(job.getOwnerOrgId()).isEqualTo(OWNER_ORG_ID);
    }
  }
}
