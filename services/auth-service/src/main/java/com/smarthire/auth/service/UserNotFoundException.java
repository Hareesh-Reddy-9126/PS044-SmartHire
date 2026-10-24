package com.smarthire.auth.service;

/** Raised when an internal caller asks about a user id that does not exist. */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(String message) {
    super(message);
  }
}
