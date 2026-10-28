package com.smarthire.application.service;

/**
 * Thrown when a candidate already has an active application for the same job.
 *
 * <p>Withdrawn and rejected applications do not block a new application; every other state does.
 */
public class DuplicateApplicationException extends RuntimeException {

  public DuplicateApplicationException(String message) {
    super(message);
  }
}
