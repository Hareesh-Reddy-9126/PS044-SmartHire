package com.smarthire.recruitment.service;

/**
 * Application Service refused the status change that a recruitment decision requires.
 *
 * <p>Application Service owns the application state machine, so it — not this service — decides
 * whether an application may move to {@code HIRED} or {@code REJECTED} (for example, an application
 * the candidate has since withdrawn, or one that was never shortlisted). A refusal is a conflict
 * about the resource, not an outage, so it is reported as 409 rather than 503.
 *
 * <p>Surfacing it is the point: the caller asked for one decision that spans two aggregates, and
 * answering "done" while the application was left unchanged would be a lie.
 */
public class ApplicationSyncConflictException extends RuntimeException {

  public ApplicationSyncConflictException(String message) {
    super(message);
  }
}
