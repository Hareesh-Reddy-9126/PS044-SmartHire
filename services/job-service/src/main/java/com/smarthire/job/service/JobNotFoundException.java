package com.smarthire.job.service;

import java.io.Serial;

/**
 * Raised when the requested job does not exist, or exists but must not be disclosed to this caller.
 * Rendered as an RFC 9457 {@code 404 Not Found} (ADR-0016).
 *
 * <p>The second case is deliberate: answering 403 for a DRAFT job the caller may not see would
 * confirm that the identifier exists, so a non-owner probing identifiers gets the same answer for
 * "no such job" and "not yours to see". Mutations still answer 403, because there the caller has
 * already proved recruiter identity and the distinction is useful rather than leaky.
 */
public class JobNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public JobNotFoundException(String message) {
    super(message);
  }
}
