import { useState, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export function useApi() {
  const { logout } = useAuth();
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = useState(false);

  const handleError = useCallback((error: unknown, defaultMessage: string) => {
    if (error instanceof Response) {
      // This shouldn't happen with our client
    } else if (error && typeof error === 'object' && 'error' in error) {
      const apiError = error as { error: { status: number; detail: string } };
      if (apiError.error.status === 401) {
        showToast('Your session has expired. Please sign in again.', 'error');
        logout();
        return;
      }
      if (apiError.error.status === 403) {
        showToast('You do not have permission to perform this action.', 'error');
        return;
      }
      if (apiError.error.status === 429) {
        showToast('Too many requests. Please wait a moment and try again.', 'error');
        return;
      }
      showToast(apiError.error.detail || defaultMessage, 'error');
    } else {
      showToast(defaultMessage, 'error');
    }
  }, [showToast, logout]);

  const execute = useCallback(async <T,>(
    apiCall: () => Promise<T>,
    options?: {
      loadingMessage?: string;
      successMessage?: string;
      errorMessage?: string;
      onSuccess?: (data: T) => void;
    }
  ): Promise<T | null> => {
    setIsLoading(true);
    try {
      const data = await apiCall();
      if (options?.successMessage) {
        showToast(options.successMessage, 'success');
      }
      options?.onSuccess?.(data);
      return data;
    } catch (error) {
      handleError(error, options?.errorMessage || 'An unexpected error occurred');
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [handleError, showToast]);

  return { isLoading, execute };
}

export function useLocalStorage<T>(key: string, initialValue: T) {
  const [storedValue, setStoredValue] = useState<T>(() => {
    if (typeof window === 'undefined') return initialValue;
    try {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } catch {
      return initialValue;
    }
  });

  const setValue = (value: T | ((val: T) => T)) => {
    try {
      const valueToStore = value instanceof Function ? value(storedValue) : value;
      setStoredValue(valueToStore);
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(key, JSON.stringify(valueToStore));
      }
    } catch (error) {
      console.error(`Error saving to localStorage:`, error);
    }
  };

  return [storedValue, setValue] as const;
}