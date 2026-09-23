package com.smarthire.recruitment.service;

/** Thrown when a recruitment record is not found. */
public class RecruitmentNotFoundException extends RuntimeException {

  public RecruitmentNotFoundException(String message) {
    super(message);
  }
}
