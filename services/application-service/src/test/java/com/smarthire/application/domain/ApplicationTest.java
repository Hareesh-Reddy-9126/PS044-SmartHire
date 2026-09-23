package com.smarthire.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Application} aggregate state machine (D5). */
class ApplicationTest {

  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID CANDIDATE_ID = UUID.randomUUID();
  private static final String RESUME_LINK = "https://example.com/resume.pdf";

  @Nested
  @DisplayName("Legal transitions")
  class LegalTransitions {

    @Test
    @DisplayName("APPLIED → UNDER_REVIEW")
    void appliedToUnderReview() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("UNDER_REVIEW → SHORTLISTED")
    void underReviewToShortlisted() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
    }

    @Test
    @DisplayName("SHORTLISTED → HIRED")
    void shortlistedToHired() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.hire();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.HIRED);
      assertThat(app.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("SHORTLISTED → REJECTED")
    void shortlistedToRejected() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.reject();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
      assertThat(app.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("APPLIED → WITHDRAWN (candidate withdrawal)")
    void appliedToWithdrawn() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.withdraw();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
      assertThat(app.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("UNDER_REVIEW → WITHDRAWN (candidate withdrawal)")
    void underReviewToWithdrawn() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.withdraw();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("SHORTLISTED → WITHDRAWN (candidate withdrawal)")
    void shortlistedToWithdrawn() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.withdraw();
      assertThat(app.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }
  }

  @Nested
  @DisplayName("Illegal transitions")
  class IllegalTransitions {

    @Test
    @DisplayName("Cannot move UNDER_REVIEW from non-APPLIED")
    void cannotMoveToUnderReviewFromWrongState() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      assertThatThrownBy(app::moveToUnderReview)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining("Only an APPLIED application can move to UNDER_REVIEW");
    }

    @Test
    @DisplayName("Cannot shortlist from non-UNDER_REVIEW")
    void cannotShortlistFromWrongState() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      assertThatThrownBy(app::shortlist)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining("Only an UNDER_REVIEW application can be shortlisted");
    }

    @Test
    @DisplayName("Cannot hire from non-SHORTLISTED")
    void cannotHireFromWrongState() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      assertThatThrownBy(app::hire)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining("Only a SHORTLISTED application can be hired");
    }

    @Test
    @DisplayName("Cannot reject from non-SHORTLISTED")
    void cannotRejectFromWrongState() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      assertThatThrownBy(app::reject)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining("Only a SHORTLISTED application can be rejected");
    }

    @Test
    @DisplayName("Cannot withdraw from HIRED")
    void cannotWithdrawFromHired() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.hire();
      assertThatThrownBy(app::withdraw)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining(
              "Only an APPLIED, UNDER_REVIEW, or SHORTLISTED application can be withdrawn");
    }

    @Test
    @DisplayName("Cannot withdraw from REJECTED")
    void cannotWithdrawFromRejected() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.reject();
      assertThatThrownBy(app::withdraw)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining(
              "Only an APPLIED, UNDER_REVIEW, or SHORTLISTED application can be withdrawn");
    }

    @Test
    @DisplayName("Cannot withdraw from WITHDRAWN (already terminal)")
    void cannotWithdrawFromWithdrawn() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.withdraw();
      assertThatThrownBy(app::withdraw)
          .isInstanceOf(IllegalApplicationStateException.class)
          .hasMessageContaining(
              "Only an APPLIED, UNDER_REVIEW, or SHORTLISTED application can be withdrawn");
    }
  }

  @Nested
  @DisplayName("Ownership")
  class Ownership {

    @Test
    @DisplayName("Owner can access their application")
    void ownerCanAccess() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      assertThat(app.isOwnedBy(CANDIDATE_ID)).isTrue();
    }

    @Test
    @DisplayName("Different candidate cannot access")
    void differentCandidateCannotAccess() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      assertThat(app.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Null candidate never owns")
    void nullCandidateNeverOwns() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      assertThat(app.isOwnedBy(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("Terminal state detection")
  class TerminalState {

    @Test
    @DisplayName("HIRED is terminal")
    void hiredIsTerminal() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.hire();
      assertThat(app.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("REJECTED is terminal")
    void rejectedIsTerminal() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      app.reject();
      assertThat(app.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("WITHDRAWN is terminal")
    void withdrawnIsTerminal() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.withdraw();
      assertThat(app.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("APPLIED is not terminal")
    void appliedIsNotTerminal() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      assertThat(app.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("UNDER_REVIEW is not terminal")
    void underReviewIsNotTerminal() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      assertThat(app.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SHORTLISTED is not terminal")
    void shortlistedIsNotTerminal() {
      Application app =
          Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
      app.moveToUnderReview();
      app.shortlist();
      assertThat(app.isTerminal()).isFalse();
    }
  }
}
