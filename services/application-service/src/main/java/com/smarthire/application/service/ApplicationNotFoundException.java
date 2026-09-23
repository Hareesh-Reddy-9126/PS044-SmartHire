package com.smarthire.application.service;

/** Thrown when an application is not found. */
public class ApplicationNotFoundException extends RuntimeException {

  public ApplicationNotFoundException(String message) {
    super(message);
  }
}
