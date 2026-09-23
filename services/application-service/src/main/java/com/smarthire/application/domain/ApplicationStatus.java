package com.smarthire.application.domain;

/**
 * Application status lifecycle (D5):
 *
 * <pre>
 * APPLIED
 *    |
 *    v
 * UNDER_REVIEW
 *    |
 *    v
 * SHORTLISTED
 *    |   \
 *    v    \
 *  HIRED  REJECTED
 *
 * Candidate withdrawal (from any active state):
 *
 * APPLIED / UNDER_REVIEW / SHORTLISTED
 *    |
 *    v
 * WITHDRAWN
 * </pre>
 *
 * Illegal transitions are rejected by the aggregate.
 */
public enum ApplicationStatus {
  APPLIED,
  UNDER_REVIEW,
  SHORTLISTED,
  HIRED,
  REJECTED,
  WITHDRAWN;
}
