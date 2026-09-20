package com.smarthire.auth.service;

/**
 * A presented refresh token is unknown, expired, or already rotated/revoked (including detected
 * reuse). Deliberately non-specific to avoid leaking token state. Maps to HTTP 401.
 */
public class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String message) {
    super(message);
  }
}
