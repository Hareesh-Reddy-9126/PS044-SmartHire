package com.smarthire.auth.service;

/** Registration attempted with an email that already exists. Maps to HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {

  public EmailAlreadyExistsException(String message) {
    super(message);
  }
}
