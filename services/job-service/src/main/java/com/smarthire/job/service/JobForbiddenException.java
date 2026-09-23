package com.smarthire.job.service;

import java.io.Serial;

/**
 * Raised by the service-layer ownership check when an authenticated recruiter tries to act on a job
 * owned by another organization, or presents a token without a usable {@code orgId} claim. Rendered
 * as an RFC 9457 {@code 403 Forbidden} (ADR-0016) with no detail about the owning organization.
 */
public class JobForbiddenException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public JobForbiddenException(String message) {
    super(message);
  }
}
