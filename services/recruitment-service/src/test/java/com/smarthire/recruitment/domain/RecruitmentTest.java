package com.smarthire.recruitment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Recruitment} aggregate: both state machines, and ownership. */
class RecruitmentTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID OWNER_ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  private static Recruitment newRecruitment() {
    return Recruitment.shortlist(
        UUID.randomUUID(), APPLICATION_ID, JOB_ID, OWNER_ORG_ID, CANDIDATE_ID);
  }

  @Nested
  @DisplayName("Interview status legal transitions")
  class InterviewLegalTransitions {

    @Test
    @DisplayName("NOT_SCHEDULED → SCHEDULED")
    void notScheduledToScheduled() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      assertThat(r.getInterviewStatus()).isEqualTo(InterviewStatus.SCHEDULED);
    }

    @Test
    @DisplayName("SCHEDULED → COMPLETED")
    void scheduledToCompleted() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.completeInterview();
      assertThat(r.getInterviewStatus()).isEqualTo(InterviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("SCHEDULED → NO_SHOW")
    void scheduledToNoShow() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.markNoShow();
      assertThat(r.getInterviewStatus()).isEqualTo(InterviewStatus.NO_SHOW);
    }
  }

  @Nested
  @DisplayName("Interview status illegal transitions")
  class InterviewIllegalTransitions {

    @Test
    @DisplayName("Cannot schedule from non-NOT_SCHEDULED")
    void cannotScheduleFromWrongState() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      assertThatThrownBy(r::scheduleInterview)
          .isInstanceOf(IllegalRecruitmentStateException.class)
          .hasMessageContaining("Only a NOT_SCHEDULED recruitment can be scheduled");
    }

    @Test
    @DisplayName("Cannot complete from non-SCHEDULED")
    void cannotCompleteFromWrongState() {
      Recruitment r = newRecruitment();
      assertThatThrownBy(r::completeInterview)
          .isInstanceOf(IllegalRecruitmentStateException.class)
          .hasMessageContaining("Only a SCHEDULED interview can be marked completed");
    }

    @Test
    @DisplayName("Cannot mark NO_SHOW from non-SCHEDULED")
    void cannotNoShowFromWrongState() {
      Recruitment r = newRecruitment();
      assertThatThrownBy(r::markNoShow)
          .isInstanceOf(IllegalRecruitmentStateException.class)
          .hasMessageContaining("Only a SCHEDULED interview can be marked NO_SHOW");
    }

    @Test
    @DisplayName("Cannot complete from COMPLETED")
    void cannotCompleteFromCompleted() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.completeInterview();
      assertThatThrownBy(r::completeInterview).isInstanceOf(IllegalRecruitmentStateException.class);
    }

    @Test
    @DisplayName("Cannot mark NO_SHOW from COMPLETED")
    void cannotNoShowFromCompleted() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.completeInterview();
      assertThatThrownBy(r::markNoShow).isInstanceOf(IllegalRecruitmentStateException.class);
    }

    @Test
    @DisplayName("Cannot complete from NO_SHOW")
    void cannotCompleteFromNoShow() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.markNoShow();
      assertThatThrownBy(r::completeInterview).isInstanceOf(IllegalRecruitmentStateException.class);
    }

    @Test
    @DisplayName("The exception reports both lifecycles and the attempted action")
    void exceptionCarriesDiagnostics() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.hire();

      assertThatThrownBy(r::scheduleInterview)
          .isInstanceOfSatisfying(
              IllegalRecruitmentStateException.class,
              ex -> {
                assertThat(ex.getCurrentInterviewStatus()).isEqualTo("SCHEDULED");
                assertThat(ex.getCurrentFinalStatus()).isEqualTo("HIRED");
                assertThat(ex.getAttemptedAction()).isEqualTo("scheduleInterview");
              });
    }
  }

  @Nested
  @DisplayName("Final status legal transitions")
  class FinalLegalTransitions {

    @Test
    @DisplayName("PENDING → HIRED")
    void pendingToHired() {
      Recruitment r = newRecruitment();
      r.hire();
      assertThat(r.getFinalStatus()).isEqualTo(FinalStatus.HIRED);
    }

    @Test
    @DisplayName("PENDING → REJECTED")
    void pendingToRejected() {
      Recruitment r = newRecruitment();
      r.reject();
      assertThat(r.getFinalStatus()).isEqualTo(FinalStatus.REJECTED);
    }
  }

  @Nested
  @DisplayName("Final status illegal transitions")
  class FinalIllegalTransitions {

    @Test
    @DisplayName("Cannot hire from non-PENDING")
    void cannotHireFromWrongState() {
      Recruitment r = newRecruitment();
      r.hire();
      assertThatThrownBy(r::hire)
          .isInstanceOf(IllegalRecruitmentStateException.class)
          .hasMessageContaining("Only a PENDING recruitment can be hired");
    }

    @Test
    @DisplayName("Cannot reject from non-PENDING")
    void cannotRejectFromWrongState() {
      Recruitment r = newRecruitment();
      r.reject();
      assertThatThrownBy(r::reject)
          .isInstanceOf(IllegalRecruitmentStateException.class)
          .hasMessageContaining("Only a PENDING recruitment can be rejected");
    }

    @Test
    @DisplayName("Cannot hire from REJECTED")
    void cannotHireFromRejected() {
      Recruitment r = newRecruitment();
      r.reject();
      assertThatThrownBy(r::hire).isInstanceOf(IllegalRecruitmentStateException.class);
    }

    @Test
    @DisplayName("Cannot reject from HIRED")
    void cannotRejectFromHired() {
      Recruitment r = newRecruitment();
      r.hire();
      assertThatThrownBy(r::reject).isInstanceOf(IllegalRecruitmentStateException.class);
    }
  }

  @Nested
  @DisplayName("Shortlisting sets the initial state")
  class Shortlisting {

    @Test
    @DisplayName("Initial interview status is NOT_SCHEDULED")
    void initialInterviewStatus() {
      assertThat(newRecruitment().getInterviewStatus()).isEqualTo(InterviewStatus.NOT_SCHEDULED);
    }

    @Test
    @DisplayName("Initial final status is PENDING")
    void initialFinalStatus() {
      assertThat(newRecruitment().getFinalStatus()).isEqualTo(FinalStatus.PENDING);
    }

    @Test
    @DisplayName("The resolved ownership facts are stored on the aggregate")
    void storesOwnership() {
      Recruitment r = newRecruitment();

      assertThat(r.getApplicationId()).isEqualTo(APPLICATION_ID);
      assertThat(r.getJobId()).isEqualTo(JOB_ID);
      assertThat(r.getOwnerOrgId()).isEqualTo(OWNER_ORG_ID);
      assertThat(r.getCandidateId()).isEqualTo(CANDIDATE_ID);
    }
  }

  @Nested
  @DisplayName("Ownership")
  class Ownership {

    @Test
    @DisplayName("Belongs to the organization that owns the job posting")
    void ownedByOwningOrg() {
      assertThat(newRecruitment().isOwnedBy(OWNER_ORG_ID)).isTrue();
    }

    @Test
    @DisplayName("Does not belong to another organization")
    void notOwnedByAnotherOrg() {
      assertThat(newRecruitment().isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("A null organization owns nothing — a token with no orgId cannot pass the check")
    void nullOrgOwnsNothing() {
      assertThat(newRecruitment().isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("Tracks the candidate whose application it is")
    void belongsToItsCandidate() {
      assertThat(newRecruitment().belongsToCandidate(CANDIDATE_ID)).isTrue();
    }

    @Test
    @DisplayName("Does not track another candidate")
    void doesNotBelongToAnotherCandidate() {
      assertThat(newRecruitment().belongsToCandidate(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("A null candidate matches nothing")
    void nullCandidateMatchesNothing() {
      assertThat(newRecruitment().belongsToCandidate(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("Independent state machines")
  class IndependentMachines {

    @Test
    @DisplayName("Interview and final status can advance independently")
    void independentAdvancement() {
      Recruitment r = newRecruitment();
      r.scheduleInterview();
      r.hire(); // final status can advance while interview is SCHEDULED
      assertThat(r.getInterviewStatus()).isEqualTo(InterviewStatus.SCHEDULED);
      assertThat(r.getFinalStatus()).isEqualTo(FinalStatus.HIRED);

      r.completeInterview(); // interview can still advance
      assertThat(r.getInterviewStatus()).isEqualTo(InterviewStatus.COMPLETED);
      assertThat(r.getFinalStatus()).isEqualTo(FinalStatus.HIRED);
    }
  }
}
