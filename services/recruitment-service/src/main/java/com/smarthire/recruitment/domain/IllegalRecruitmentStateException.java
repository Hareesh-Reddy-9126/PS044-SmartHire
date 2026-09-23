package com.smarthire.recruitment.domain;

/**
 * Thrown when an illegal state transition is attempted on a {@link Recruitment}. The exception
 * carries the current state and the attempted action for precise diagnostics.
 */
public class IllegalRecruitmentStateException extends IllegalStateException {

  private final String currentInterviewStatus;
  private final String currentFinalStatus;
  private final String attemptedAction;

  public IllegalRecruitmentStateException(
      String message,
      String currentInterviewStatus,
      String currentFinalStatus,
      String attemptedAction) {
    super(message);
    this.currentInterviewStatus = currentInterviewStatus;
    this.currentFinalStatus = currentFinalStatus;
    this.attemptedAction = attemptedAction;
  }

  public String getCurrentInterviewStatus() {
    return currentInterviewStatus;
  }

  public String getCurrentFinalStatus() {
    return currentFinalStatus;
  }

  public String getAttemptedAction() {
    return attemptedAction;
  }
}
