package com.smarthire.recruitment.service;

/**
 * Thrown when a request conflicts with the current state of the resource — today, shortlisting an
 * application that already has a recruitment. Rendered as 409 by {@code
 * RecruitmentExceptionHandler}.
 *
 * <p>A dedicated type rather than a raw {@code IllegalArgumentException}: the handler can map it
 * deterministically without also catching every unrelated argument bug in the service layer as a
 * 409.
 */
public class RecruitmentConflictException extends RuntimeException {

  public RecruitmentConflictException(String message) {
    super(message);
  }
}
