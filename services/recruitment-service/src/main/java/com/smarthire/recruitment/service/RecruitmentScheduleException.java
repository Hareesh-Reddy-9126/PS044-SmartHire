package com.smarthire.recruitment.service;

/** Thrown when interview schedule values are not semantically valid. */
public class RecruitmentScheduleException extends RuntimeException {

  public RecruitmentScheduleException(String message) {
    super(message);
  }
}
