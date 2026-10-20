package com.smarthire.document.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Document type classification.
 *
 * <p>Values are stored and rendered in upper case, but {@link #parse(String)} and the JSON binding
 * accept any case. A path segment or a request body is client input, and a client that sends {@code
 * resume} should get its document listed rather than a type-conversion error.
 */
public enum DocumentType {
  RESUME,
  COVER_LETTER,
  PORTFOLIO,
  CERTIFICATE,
  OTHER;

  /**
   * Resolves a document type from untrusted input, ignoring case and surrounding whitespace.
   *
   * @param value the supplied type name
   * @return the matching type
   * @throws IllegalArgumentException if the value matches no type; mapped to RFC 9457 400
   */
  public static DocumentType parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("A document type is required.");
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown document type: " + value);
    }
  }

  /** JSON binding entry point, so {@code "resume"} deserializes to {@link #RESUME}. */
  @JsonCreator
  public static DocumentType fromJson(String value) {
    return parse(value);
  }

  @JsonValue
  public String toJsonValue() {
    return name();
  }
}
