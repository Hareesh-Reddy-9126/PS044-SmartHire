import { ApiErrorResponse, isApiError } from '../api/client';

export { isApiError };

/** HTTP status of a caught error, or undefined when it is not an API failure. */
export function apiStatus(error: unknown): number | undefined {
  return isApiError(error) ? error.error.status : undefined;
}

/** Correlation id the backend attaches to every problem response. */
export function correlationId(error: unknown): string | undefined {
  return isApiError(error) ? error.error.correlationId || undefined : undefined;
}

/**
 * Message to show the user.
 *
 * Prefers the server's RFC 9457 `detail`, which is written for a human, and
 * falls back to the caller's copy when there is no usable body (a network
 * failure, or a mock in a test).
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (isApiError(error)) {
    return error.error.detail?.trim() || fallback;
  }
  return fallback;
}

export function isNotFound(error: unknown): boolean {
  return apiStatus(error) === 404;
}

export function isForbidden(error: unknown): boolean {
  return apiStatus(error) === 403;
}

export function isUnauthorized(error: unknown): boolean {
  return apiStatus(error) === 401;
}

export function isConflict(error: unknown): boolean {
  return apiStatus(error) === 409;
}

export function isRateLimited(error: unknown): boolean {
  return apiStatus(error) === 429;
}

/** A downstream service is unreachable or its circuit is open. */
export function isServiceUnavailable(error: unknown): boolean {
  return apiStatus(error) === 503;
}

/**
 * Copy for an illegal state transition (409).
 *
 * The services annotate these responses with the current status and the action
 * that was attempted, so the message can name both instead of echoing a raw
 * string.
 */
export function transitionConflictMessage(error: unknown): string {
  if (!isApiError(error)) {
    return 'This change is no longer possible. The record may have been updated since this page loaded.';
  }

  const { currentStatus, currentFinalStatus, currentInterviewStatus, attemptedAction } = error.error;

  if (currentStatus) {
    return `This application is ${currentStatus}, and "${attemptedAction ?? 'this change'}" needs a different status. It may have been updated since this page loaded.`;
  }
  if (currentInterviewStatus || currentFinalStatus) {
    return `This recruitment is currently at interview "${currentInterviewStatus ?? '—'}" and decision "${currentFinalStatus ?? '—'}". It may have been updated since this page loaded.`;
  }

  return error.error.detail?.trim() ||
    'This change is no longer possible. The record may have been updated since this page loaded.';
}

/** True when the error is a rate-limit rejection from the gateway. */
export function rateLimitMessage(): string {
  return 'Too many requests. Wait a moment and try again.';
}

export { ApiErrorResponse };
