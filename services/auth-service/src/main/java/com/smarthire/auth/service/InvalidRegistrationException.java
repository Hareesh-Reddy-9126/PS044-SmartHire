package com.smarthire.auth.service;

/**
 * A registration request was well-formed JSON but cannot be accepted for the requested role, for
 * example a recruiter without an organization name. Rendered as HTTP 400.
 */
public class InvalidRegistrationException extends RuntimeException {

  public InvalidRegistrationException(String message) {
    super(message);
  }
}
