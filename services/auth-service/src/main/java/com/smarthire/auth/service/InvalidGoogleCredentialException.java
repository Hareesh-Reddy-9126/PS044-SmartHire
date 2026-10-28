package com.smarthire.auth.service;

/**
 * The Google ID token presented by the browser failed verification (signature, issuer, audience,
 * expiry or verified-email check). The response deliberately does not say which check failed.
 */
public class InvalidGoogleCredentialException extends RuntimeException {

  public InvalidGoogleCredentialException(String message) {
    super(message);
  }

  public InvalidGoogleCredentialException(String message, Throwable cause) {
    super(message, cause);
  }
}
