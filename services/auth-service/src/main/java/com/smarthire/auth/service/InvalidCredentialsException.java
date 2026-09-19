package com.smarthire.auth.service;

/** Login failed (unknown email or wrong password). Deliberately non-specific. Maps to HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
