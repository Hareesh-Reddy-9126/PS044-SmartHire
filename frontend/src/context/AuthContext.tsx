import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi, MeResponse, clearAccessToken, getAccessToken, isTokenExpired } from '../api/client';

interface AuthContextType {
  user: MeResponse | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    try {
      const token = getAccessToken();
      if (!token) {
        setUser(null);
        return;
      }
      const me = await authApi.me();
      setUser(me);
    } catch {
      setUser(null);
      clearAccessToken();
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Initial auth check
  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  // Token expiry warning scheduler
  useEffect(() => {
    if (!getAccessToken()) return;
    
    const checkInterval = setInterval(() => {
      if (isTokenExpired()) {
        // Token expired - user will be redirected on next API call
        // Could trigger a warning toast here
      }
    }, 60000); // Check every minute
    
    return () => clearInterval(checkInterval);
  }, []);

  const login = async (email: string, password: string) => {
    await authApi.login(email, password);
    await refreshUser();
  };

  const register = async (email: string, password: string) => {
    await authApi.register(email, password);
    // Registration doesn't return a token - user must login
  };

  const logout = async () => {
    await authApi.logout();
    setUser(null);
  };

  const hasRole = (role: string): boolean => {
    return user?.roles.includes(role) ?? false;
  };

  return (
    <AuthContext.Provider value={{
      user,
      isLoading,
      isAuthenticated: !!user,
      login,
      register,
      logout,
      refreshUser,
      hasRole,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}