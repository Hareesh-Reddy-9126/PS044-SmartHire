package com.smarthire.job.domain;

import java.io.Serial;

/**
 * Raised by {@link Job} when a lifecycle transition that the state machine does not allow is
 * attempted. Rendered as an RFC 9457 {@code 409 Conflict} by the API layer (ADR-0016): the request
 * was well-formed and the caller was authorized, but the resource's current state forbids it.
 */
public class IllegalJobStateException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final JobStatus currentStatus;
  private final String attemptedAction;

  public IllegalJobStateException(String message, JobStatus currentStatus, String attemptedAction) {
    super(message);
    this.currentStatus = currentStatus;
    this.attemptedAction = attemptedAction;
  }

  /** The status the job was actually in when the transition was attempted. */
  public JobStatus getCurrentStatus() {
    return currentStatus;
  }

  /** The transition that was refused — {@code open}, {@code close} or {@code reopen}. */
  public String getAttemptedAction() {
    return attemptedAction;
  }
}
