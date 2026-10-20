import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// Mock the api/client module BEFORE importing anything that uses it
vi.mock('../api/client', () => ({
  authApi: {
    me: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
  },
  getAccessToken: vi.fn(() => null),
  clearAccessToken: vi.fn(),
  isTokenExpired: vi.fn(() => true),
  setAccessToken: vi.fn(),
}));

// Import after mocks are set up
import { AuthProvider, useAuth } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/AppRoutes';
import { authApi, getAccessToken, clearAccessToken, isTokenExpired } from '../api/client';

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAccessToken).mockReturnValue(null);
    vi.mocked(isTokenExpired).mockReturnValue(true);
    vi.mocked(authApi.me).mockReset();
  });

  it('returns unauthenticated state when no access token exists', async () => {
    vi.mocked(getAccessToken).mockReturnValue(null);
    vi.mocked(isTokenExpired).mockReturnValue(true);

    function TestAuthDisplay() {
      const { isAuthenticated, isLoading } = useAuth();
      if (isLoading) return <div data-testid="auth-loading">Loading...</div>;
      return <div data-testid="auth-status">authenticated: {String(isAuthenticated)}</div>;
    }

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <TestAuthDisplay />
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('authenticated: false')).toBeInTheDocument();
    }, { timeout: 10000 });
  });

  it('returns authenticated state when valid token exists and /auth/me succeeds', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'test@example.com',
      roles: ['CANDIDATE'],
      orgId: null,
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    function TestAuthDisplay() {
      const { user, isAuthenticated, isLoading } = useAuth();
      if (isLoading) return <div data-testid="auth-loading">Loading...</div>;
      return (
        <div>
          <div data-testid="auth-status">authenticated: {String(isAuthenticated)}</div>
          {user && <div data-testid="auth-email">email: {user.email}</div>}
        </div>
      );
    }

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <TestAuthDisplay />
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('authenticated: true')).toBeInTheDocument();
      expect(screen.getByText('email: test@example.com')).toBeInTheDocument();
    }, { timeout: 10000 });
  });

  it('clears user and returns unauthenticated when /auth/me fails', async () => {
    vi.mocked(getAccessToken).mockReturnValue('expired-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockRejectedValue(new Error('401 Unauthorized'));

    function TestAuthDisplay() {
      const { isAuthenticated, isLoading } = useAuth();
      if (isLoading) return <div data-testid="auth-loading">Loading...</div>;
      return <div data-testid="auth-status">authenticated: {String(isAuthenticated)}</div>;
    }

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <TestAuthDisplay />
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('authenticated: false')).toBeInTheDocument();
      expect(clearAccessToken).toHaveBeenCalled();
    }, { timeout: 10000 });
  });
});

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAccessToken).mockReturnValue(null);
    vi.mocked(isTokenExpired).mockReturnValue(true);
    vi.mocked(authApi.me).mockReset();
  });

  const renderProtected = (initialEntries = ['/candidate/applications']) => {
    return render(
      <MemoryRouter initialEntries={initialEntries}>
        <AuthProvider>
          <Routes>
            <Route
              path="/candidate/applications"
              element={
                <ProtectedRoute allowedRoles={['CANDIDATE']}>
                  <div data-testid="protected-content">Candidate Applications</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
            <Route path="/403" element={<div data-testid="forbidden-page">403 Forbidden</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );
  };

  it('redirects unauthenticated user to /login', async () => {
    vi.mocked(getAccessToken).mockReturnValue(null);
    vi.mocked(isTokenExpired).mockReturnValue(true);

    renderProtected();

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    }, { timeout: 10000 });

    expect(screen.queryByText('Candidate Applications')).not.toBeInTheDocument();
  });

  it('allows access when user has required role', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'candidate@example.com',
      roles: ['CANDIDATE'],
      orgId: null,
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    renderProtected();

    await waitFor(() => {
      expect(screen.getByText('Candidate Applications')).toBeInTheDocument();
    }, { timeout: 10000 });

    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  it('redirects to /403 when user lacks required role', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'recruiter@example.com',
      roles: ['RECRUITER'],
      orgId: 'org-123',
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/candidate/applications']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/candidate/applications"
              element={
                <ProtectedRoute allowedRoles={['CANDIDATE']}>
                  <div data-testid="protected-content">Candidate Applications</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
            <Route path="/403" element={<div data-testid="forbidden-page">403 Forbidden</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('403 Forbidden')).toBeInTheDocument();
    }, { timeout: 10000 });

    expect(screen.queryByText('Candidate Applications')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  it('shows loading state while auth is initializing', async () => {
    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    
    let resolveMe: (value: import('../api/client').MeResponse) => void;
    const mePromise = new Promise<import('../api/client').MeResponse>(resolve => { resolveMe = resolve; });
    vi.mocked(authApi.me).mockReturnValue(mePromise);

    render(
      <MemoryRouter initialEntries={['/candidate/applications']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/candidate/applications"
              element={
                <ProtectedRoute allowedRoles={['CANDIDATE']}>
                  <div data-testid="protected-content">Candidate Applications</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
            <Route path="/403" element={<div data-testid="forbidden-page">403 Forbidden</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    // Should not show content or login yet (loading)
    expect(screen.queryByText('Candidate Applications')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();

    // Resolve auth
    act(() => {
      resolveMe!({
        sub: 'user-123',
        email: 'candidate@example.com',
        roles: ['CANDIDATE'],
        orgId: null,
      });
    });

    await waitFor(() => {
      expect(screen.getByText('Candidate Applications')).toBeInTheDocument();
    }, { timeout: 10000 });
  });
});