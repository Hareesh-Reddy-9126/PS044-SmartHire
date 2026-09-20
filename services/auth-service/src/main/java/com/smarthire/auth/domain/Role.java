package com.smarthire.auth.domain;

/**
 * RBAC roles (ADR-0010). {@code CANDIDATE} and {@code RECRUITER} are the Project-44 source actors;
 * {@code ADMIN} is the approved platform-operations role — not a recruitment decision-maker. Public
 * self-registration creates {@code CANDIDATE} accounts only (decision 7).
 */
public enum Role {
  CANDIDATE,
  RECRUITER,
  ADMIN
}
