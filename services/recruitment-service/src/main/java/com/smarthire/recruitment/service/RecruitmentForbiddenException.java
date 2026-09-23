package com.smarthire.recruitment.service;

/**
 * Thrown when an authenticated caller holds the right role but cannot act — specifically, a
 * RECRUITER whose verified token carries no {@code orgId} claim, so there is no organization for
 * the ownership check to compare against. Rendered as 403.
 *
 * <p>Distinct from the object-level denials, which report 404 instead: those hide whether the
 * identifier exists, while this one reveals nothing about any resource, only about the caller's own
 * token.
 */
public class RecruitmentForbiddenException extends RuntimeException {

  public RecruitmentForbiddenException(String message) {
    super(message);
  }
}
