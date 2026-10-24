import { useCallback, useState } from 'react';
import { isApiError } from '../api/client';
import { useToast } from '../context/ToastContext';
import { errorMessage, isRateLimited, transitionConflictMessage } from '../utils/errors';

export interface ExecuteOptions<T> {
  successMessage?: string;
  errorMessage?: string;
  /** Return false to suppress the success toast after a successful call. */
  onSuccess?: (data: T) => void | boolean;
  /** Return true to suppress the default error toast and handle it yourself. */
  onError?: (error: unknown) => void | boolean;
}

export interface UseApiResult {
  isLoading: boolean;
  execute: <T>(call: () => Promise<T>, options?: ExecuteOptions<T>) => Promise<T | null>;
}

/**
 * Runs an API call with consistent busy state and error reporting.
 *
 * A 409 is reported with the state-transition wording, because the useful part
 * of that response is which transition was refused rather than a generic
 * failure. Authentication loss is left to the provider, which owns the session.
 */
export function useApi(): UseApiResult {
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = useState(false);

  const execute = useCallback(
    async <T,>(call: () => Promise<T>, options: ExecuteOptions<T> = {}): Promise<T | null> => {
      setIsLoading(true);
      try {
        const data = await call();

        const suppressed = options.onSuccess?.(data);
        if (suppressed !== false && options.successMessage) {
          showToast(options.successMessage, 'success');
        }
        return data;
      } catch (error) {
        const handled = options.onError?.(error);
        if (handled === true) {
          return null;
        }

        if (isApiError(error) && error.error.status === 409) {
          showToast(transitionConflictMessage(error), 'error');
        } else if (isRateLimited(error)) {
          showToast('Too many requests. Wait a moment and try again.', 'error');
        } else {
          showToast(
            errorMessage(error, options.errorMessage ?? 'Something went wrong. Please try again.'),
            'error'
          );
        }
        return null;
      } finally {
        setIsLoading(false);
      }
    },
    [showToast]
  );

  return { isLoading, execute };
}
