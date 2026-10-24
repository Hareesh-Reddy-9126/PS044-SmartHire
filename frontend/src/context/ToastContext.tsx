import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type ToastTone = 'success' | 'error' | 'info';

export interface Toast {
  id: string;
  message: string;
  tone: ToastTone;
}

export interface ToastContextValue {
  toasts: Toast[];
  showToast: (message: string, tone?: ToastTone) => void;
  dismissToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

/** Success and info clear themselves; an error waits until it is dismissed. */
const AUTO_DISMISS_MS: Record<ToastTone, number | null> = {
  success: 4000,
  info: 5000,
  error: null,
};

const MAX_VISIBLE = 3;

let counter = 0;

const TONE_ICON: Record<ToastTone, string> = {
  success: 'check_circle',
  error: 'error',
  info: 'info',
};

const TONE_CLASS: Record<ToastTone, string> = {
  success: 'toast-success',
  error: 'toast-error',
  info: 'toast-info',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismissToast = useCallback((id: string) => {
    setToasts(current => current.filter(toast => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (message: string, tone: ToastTone = 'success') => {
      counter += 1;
      const id = `toast-${counter}`;

      // Keep the newest toasts and drop the oldest beyond the visible cap.
      setToasts(current => [...current, { id, message, tone }].slice(-MAX_VISIBLE));

      const ttl = AUTO_DISMISS_MS[tone];
      if (ttl !== null) {
        window.setTimeout(() => dismissToast(id), ttl);
      }
    },
    [dismissToast]
  );

  const value = useMemo(
    () => ({ toasts, showToast, dismissToast }),
    [toasts, showToast, dismissToast]
  );

  return (
    <ToastContext.Provider value={value}>
      {children}

      <div className="toast-region">
        {toasts.map(toast => (
          <div
            key={toast.id}
            // Errors interrupt; other tones wait for a pause in speech.
            role={toast.tone === 'error' ? 'alert' : 'status'}
            aria-live={toast.tone === 'error' ? 'assertive' : 'polite'}
            className={`toast ${TONE_CLASS[toast.tone]}`}
          >
            <span className="material-symbols-outlined text-[20px] shrink-0 mt-px" aria-hidden="true">
              {TONE_ICON[toast.tone]}
            </span>
            <span className="font-body-md text-body-md grow min-w-0">{toast.message}</span>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              aria-label="Dismiss notification"
              className="-mr-1 flex h-6 w-6 shrink-0 items-center justify-center rounded text-white/80 transition-colors duration-fast hover:bg-white/20 hover:text-white focus-ring"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                close
              </span>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
