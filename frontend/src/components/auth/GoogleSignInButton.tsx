import { useCallback, useEffect, useRef, useState } from 'react';

/** Minimal shape of the Google Identity Services API used by this screen. */
interface GoogleCredentialResponse {
  credential?: string;
}

interface GoogleIdentityServices {
  initialize(options: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
  }): void;
  renderButton(
    parent: HTMLElement,
    options: {
      type?: 'standard' | 'icon';
      theme?: 'outline' | 'filled_blue' | 'filled_black';
      size?: 'large' | 'medium' | 'small';
      text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
      shape?: 'rectangular' | 'pill' | 'circle' | 'square';
      logo_alignment?: 'left' | 'center';
      width?: number;
    }
  ): void;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: GoogleIdentityServices;
      };
    };
  }
}

const CLIENT_ID = (
  import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined
)?.trim();

const SCRIPT_ID = 'google-identity-services';
const SCRIPT_SRC = 'https://accounts.google.com/gsi/client';

/** Loads Google's client once; later callers reuse the same script tag. */
function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();

  const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
  if (existing) {
    return new Promise((resolve, reject) => {
      existing.addEventListener('load', () => resolve(), { once: true });
      existing.addEventListener(
        'error',
        () => reject(new Error('Google Identity Services failed to load')),
        { once: true }
      );
    });
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () =>
      reject(new Error('Google Identity Services failed to load'));
    document.head.appendChild(script);
  });
}

export interface GoogleSignInButtonProps {
  /** Called with the ID token after the user chooses a Google account. */
  onCredential: (credential: string) => void;
  disabled?: boolean;
}

/**
 * "Continue with Google" control.
 *
 * Renders nothing when `VITE_GOOGLE_CLIENT_ID` is not configured, so a
 * deployment without OAuth keeps the email/password form unchanged. The button
 * itself is drawn by Google inside an iframe; we only provide the container.
 */
export function GoogleSignInButton({
  onCredential,
  disabled = false,
}: GoogleSignInButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  const [status, setStatus] = useState<'idle' | 'ready' | 'error'>('idle');

  useEffect(() => {
    callbackRef.current = onCredential;
  }, [onCredential]);

  const handleCredential = useCallback((response: GoogleCredentialResponse) => {
    if (response.credential) callbackRef.current(response.credential);
  }, []);

  useEffect(() => {
    if (!CLIENT_ID) return;
    let cancelled = false;

    loadGoogleScript()
      .then(() => {
        if (cancelled) return;
        const google = window.google;
        const container = containerRef.current;
        if (!google || !container) {
          setStatus('error');
          return;
        }

        google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: handleCredential,
          auto_select: false,
          cancel_on_tap_outside: true,
        });

        container.replaceChildren();
        const width = Math.min(
          Math.max(container.clientWidth || 320, 200),
          360
        );
        google.accounts.id.renderButton(container, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          logo_alignment: 'left',
          width,
        });
        setStatus('ready');
      })
      .catch(() => {
        if (!cancelled) setStatus('error');
      });

    return () => {
      cancelled = true;
    };
  }, [handleCredential]);

  if (!CLIENT_ID) return null;

  return (
    <div className="mt-6">
      <div className="flex items-center gap-3" aria-hidden="true">
        <span className="h-px flex-1 bg-c-border" />
        <span className="font-caption text-caption uppercase tracking-wide text-c-text-subtle">
          or
        </span>
        <span className="h-px flex-1 bg-c-border" />
      </div>

      <div className="mt-4 flex flex-col items-center gap-2">
        <div
          ref={containerRef}
          className={disabled ? 'pointer-events-none opacity-60' : undefined}
          aria-busy={status === 'idle'}
        />
        {status === 'error' && (
          <p className="text-center font-caption text-caption text-c-text-subtle">
            Google sign-in could not be loaded. Use your email and password
            instead.
          </p>
        )}
      </div>
    </div>
  );
}
