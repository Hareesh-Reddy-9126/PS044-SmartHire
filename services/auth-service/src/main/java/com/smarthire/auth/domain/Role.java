package com.smarthire.auth.domain;

/**
 * RBAC roles (ADR-0010). {@code CANDIDATE} and {@code RECRUITER} are the Project-44 source actors;
 * {@code ADMIN} is the approved platform-operations role — not a recruitment decision-maker.
 * Candidates and recruiters may self-register; administrator self-registration additionally
 * requires the deployment's invitation code.
 */
public enum Role {
  CANDIDATE,
  RECRUITER,
  ADMIN
}
