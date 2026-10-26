import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { ReactNode } from 'react';
import {
  accessTokenExpiry,
  authApi,
  clearAccessToken,
  endSession,
  getAccessToken,
  restoreSession,
  setUnauthorizedHandler,
  type MeResponse,
  type RegisterPayload,
} from '../api/client';

export interface AuthContextValue {
  user: MeResponse | null;
  /** True until the initial session check settles. */
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<MeResponse>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  hasRole: (role: string) => boolean;
  /** Epoch millis when the access token expires, or 0 when signed out. */
  tokenExpiresAt: number;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** Refresh this long before expiry so no request races the clock. */
const REFRESH_LEAD_MS = 60_000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [tokenExpiresAt, setTokenExpiresAt] = useState(0);
  const refreshTimer = useRef<number | null>(null);

  /** Loads identity from the server using whatever token is currently held. */
  const loadIdentity = useCallback(async (): Promise<MeResponse> => {
    const me = await authApi.me();
    setUser(me);
    setTokenExpiresAt(accessTokenExpiry());
    return me;
  }, []);

  /** Drops every piece of local session state. */
  const clearIdentity = useCallback(() => {
    clearAccessToken();
    setUser(null);
    setTokenExpiresAt(0);
  }, []);

  const refreshUser = useCallback(async () => {
    if (!getAccessToken()) {
      // No usable access token: try to rebuild one from the refresh cookie
      // before deciding the caller is signed out.
      const restored = await restoreSession();
      if (!restored) {
        clearIdentity();
        setIsLoading(false);
        throw new Error('Your session has expired. Please sign in again.');
      }
    }
    try {
      await loadIdentity();
    } catch (error) {
      clearIdentity();
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, [loadIdentity, clearIdentity]);

  // Initial session resolution. `restoreSession` is tried once so a page
  // reload with a valid refresh cookie comes back signed in.
  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!getAccessToken()) {
        await restoreSession();
      }
      if (cancelled) return;
      try {
        await loadIdentity();
      } catch {
        // No usable session — stay signed out; this is not an error to surface.
        clearIdentity();
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [loadIdentity, clearIdentity]);

  // Proactive refresh: schedule a silent renewal before the access token
  // expires, and reschedule after every successful issuance.
  useEffect(() => {
    const clearTimer = () => {
      if (refreshTimer.current !== null) {
        window.clearTimeout(refreshTimer.current);
        refreshTimer.current = null;
      }
    };

    if (!user || !tokenExpiresAt) {
      clearTimer();
      return;
    }

    const delay = Math.max(tokenExpiresAt - Date.now() - REFRESH_LEAD_MS, 5_000);
    refreshTimer.current = window.setTimeout(async () => {
      const restored = await restoreSession();
      if (restored) {
        setTokenExpiresAt(accessTokenExpiry());
        await loadIdentity();
      } else {
        clearAccessToken();
        setUser(null);
        setTokenExpiresAt(0);
      }
    }, delay);

    return clearTimer;
  }, [user, tokenExpiresAt, loadIdentity]);

  // A request that cannot be recovered after a refresh loses the session; drop
  // local identity so the router sends the user to the session-expired screen.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearAccessToken();
      setUser(null);
      setTokenExpiresAt(0);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      await authApi.login(email, password);
      try {
        // Identity is part of a successful sign-in: without it the router would
        // treat the user as signed out while a token is still held. Returning
        // it lets the caller route to a screen the role can actually open.
        return await loadIdentity();
      } catch (error) {
        clearIdentity();
        throw error;
      } finally {
        setIsLoading(false);
      }
    },
    [loadIdentity, clearIdentity]
  );

  const register = useCallback(async (payload: RegisterPayload) => {
    // Registration returns only a user id; the caller must sign in afterwards.
    await authApi.register(payload);
  }, []);

  const logout = useCallback(async () => {
    await endSession();
    setUser(null);
    setTokenExpiresAt(0);
  }, []);

  const hasRole = useCallback(
    (role: string) => user?.roles?.includes(role) ?? false,
    [user]
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: Boolean(user),
      login,
      register,
      logout,
      refreshUser,
      hasRole,
      tokenExpiresAt,
    }),
    [user, isLoading, login, register, logout, refreshUser, hasRole, tokenExpiresAt]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
