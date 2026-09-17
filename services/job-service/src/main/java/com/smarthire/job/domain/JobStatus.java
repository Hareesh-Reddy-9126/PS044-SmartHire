package com.smarthire.job.domain;

/**
 * Job posting lifecycle (D5). Inc 0 persists the value only; the {@code DRAFT → OPEN ↔ CLOSED}
 * transition rules are enforced by the domain at Inc 2.
 */
public enum JobStatus {
  DRAFT,
  OPEN,
  CLOSED
}
