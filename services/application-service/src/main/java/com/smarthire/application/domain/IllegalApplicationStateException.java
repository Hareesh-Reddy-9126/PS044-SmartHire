package com.smarthire.application.domain;

/**
 * Thrown when an illegal state transition is attempted on an {@link Application}. The exception
 * carries the current state and the attempted action for precise diagnostics.
 */
public class IllegalApplicationStateException extends IllegalStateException {

  private final ApplicationStatus currentStatus;
  private final String attemptedAction;

  public IllegalApplicationStateException(
      String message, ApplicationStatus currentStatus, String attemptedAction) {
    super(message);
    this.currentStatus = currentStatus;
    this.attemptedAction = attemptedAction;
  }

  public ApplicationStatus getCurrentStatus() {
    return currentStatus;
  }

  public String getAttemptedAction() {
    return attemptedAction;
  }
}
