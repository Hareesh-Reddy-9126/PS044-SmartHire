package com.smarthire.document.service;

/** Thrown when a document is not found or not visible to the caller. */
public class DocumentNotFoundException extends RuntimeException {
  public DocumentNotFoundException(String message) {
    super(message);
  }
}
