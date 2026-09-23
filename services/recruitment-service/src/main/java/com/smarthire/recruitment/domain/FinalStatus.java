package com.smarthire.recruitment.domain;

/**
 * Final hiring decision lifecycle:
 *
 * <pre>
 * PENDING
 *    |
 *    +----> HIRED
 *    |
 *    +----> REJECTED
 * </pre>
 *
 * Once HIRED or REJECTED, no further transitions are allowed.
 */
public enum FinalStatus {
  PENDING,
  HIRED,
  REJECTED;
}
