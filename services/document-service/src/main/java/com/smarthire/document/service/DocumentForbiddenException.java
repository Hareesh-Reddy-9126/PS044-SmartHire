package com.smarthire.document.service;

/** Thrown when a caller is not authorized to access a document. */
public class DocumentForbiddenException extends RuntimeException {
  public DocumentForbiddenException(String message) {
    super(message);
  }
}
