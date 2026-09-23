package com.smarthire.recruitment.domain;

/**
 * Interview status lifecycle:
 *
 * <pre>
 * NOT_SCHEDULED
 *      |
 *      v
 * SCHEDULED
 *    /     \
 *   v       v
 * COMPLETED  NO_SHOW
 * </pre>
 *
 * Illegal transitions are rejected by the aggregate.
 */
public enum InterviewStatus {
  NOT_SCHEDULED,
  SCHEDULED,
  COMPLETED,
  NO_SHOW;
}
