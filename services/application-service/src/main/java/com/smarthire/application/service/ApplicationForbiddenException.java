package com.smarthire.application.service;

/** Thrown when a caller attempts to access an application they do not own. */
public class ApplicationForbiddenException extends RuntimeException {

  public ApplicationForbiddenException(String message) {
    super(message);
  }
}
