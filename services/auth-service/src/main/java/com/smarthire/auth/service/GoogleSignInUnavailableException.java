package com.smarthire.auth.service;

/**
 * Google sign-in was requested but the deployment has not configured an OAuth client id. Kept
 * distinct from a credential failure so operators see a configuration problem rather than a
 * misleading "invalid token".
 */
public class GoogleSignInUnavailableException extends RuntimeException {

  public GoogleSignInUnavailableException() {
    super("Google sign-in is not configured for this deployment.");
  }
}
