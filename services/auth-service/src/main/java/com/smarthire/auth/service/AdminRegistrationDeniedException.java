package com.smarthire.auth.service;

/**
 * Administrator self-registration was attempted without a valid invitation code, or the deployment
 * has not enabled administrator self-registration at all. Rendered as HTTP 403 with a deliberately
 * opaque message so the endpoint does not disclose whether a code is configured.
 */
public class AdminRegistrationDeniedException extends RuntimeException {

  public AdminRegistrationDeniedException() {
    super("Administrator registration requires a valid invitation code.");
  }
}
