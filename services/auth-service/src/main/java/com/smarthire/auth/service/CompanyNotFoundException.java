package com.smarthire.auth.service;

/** Raised when a company record cannot be found. Mapped to HTTP 404 by the API layer. */
public class CompanyNotFoundException extends RuntimeException {

  public CompanyNotFoundException(String message) {
    super(message);
  }
}
