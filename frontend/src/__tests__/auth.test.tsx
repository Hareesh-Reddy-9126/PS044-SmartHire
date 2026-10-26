import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
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
  setUnauthorizedHandler: vi.fn(),
  restoreSession: vi.fn(() => Promise.resolve(false)),
  tryRefreshSession: vi.fn(() => Promise.resolve(false)),
  accessTokenExpiry: vi.fn(() => Date.now() + 900000),
  endSession: vi.fn(() => Promise.resolve()),
}));

// Import after mocks are set up
import { AuthProvider, useAuth } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/AppRoutes';
import { authApi, getAccessToken, clearAccessToken, isTokenExpired, endSession } from '../api/client';

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
    });

    expect(screen.queryByText('Candidate Applications')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  it('allows recruiter access to recruiter routes', async () => {
    const mockMeResponse = {
      sub: 'user-456',
      email: 'recruiter@example.com',
      roles: ['RECRUITER'],
      orgId: 'org-123',
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/recruiter/jobs']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/recruiter/jobs"
              element={
                <ProtectedRoute allowedRoles={['RECRUITER']}>
                  <div data-testid="recruiter-content">Recruiter Jobs</div>
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
      expect(screen.getByText('Recruiter Jobs')).toBeInTheDocument();
    });

    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
    expect(screen.queryByText('403 Forbidden')).not.toBeInTheDocument();
  });

  it('allows admin access to admin routes', async () => {
    const mockMeResponse = {
      sub: 'user-789',
      email: 'admin@example.com',
      roles: ['ADMIN'],
      orgId: null,
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/admin/overview']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/admin/overview"
              element={
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <div data-testid="admin-content">Admin Overview</div>
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
      expect(screen.getByText('Admin Overview')).toBeInTheDocument();
    });

    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
    expect(screen.queryByText('403 Forbidden')).not.toBeInTheDocument();
  });

  it('updates navbar immediately after login sets auth state', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'newuser@example.com',
      roles: ['CANDIDATE'],
      orgId: null,
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    function NavbarDisplay() {
      const { user, isAuthenticated } = useAuth();
      if (!isAuthenticated || !user) return <div data-testid="navbar-state">Signed out</div>;
      return (
        <div data-testid="navbar-state">
          Signed in as {user.email} ({user.roles.join(', ')})
        </div>
      );
    }

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <NavbarDisplay />
        </AuthProvider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Signed in as newuser@example.com (CANDIDATE)')).toBeInTheDocument();
    });
  });

  it('reverts navbar to signed-out state after logout', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'candidate@example.com',
      roles: ['CANDIDATE'],
      orgId: null,
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);
    vi.mocked(endSession).mockImplementation(async () => {
      clearAccessToken();
    });

    function NavbarDisplay() {
      const { user, isAuthenticated, logout } = useAuth();
      if (!isAuthenticated || !user) {
        return (
          <div>
            <button onClick={logout}>Sign out</button>
            <span data-testid="navbar-state">Signed out</span>
          </div>
        );
      }
      return (
        <div>
          <button onClick={logout}>Sign out</button>
          <span data-testid="navbar-state">
            Signed in as {user.email} ({user.roles.join(', ')})
          </span>
        </div>
      );
    }

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <NavbarDisplay />
        </AuthProvider>
      </MemoryRouter>
    );

    // After login, navbar shows signed-in state
    await waitFor(() => {
      expect(screen.getByText('Signed in as candidate@example.com (CANDIDATE)')).toBeInTheDocument();
    });

    // Click logout
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    // Navbar reverts to signed-out state
    await waitFor(() => {
      expect(screen.getByText('Signed out')).toBeInTheDocument();
    });
    expect(clearAccessToken).toHaveBeenCalled();
  });

  it('blocks recruiter from accessing admin routes', async () => {
    const mockMeResponse = {
      sub: 'user-456',
      email: 'recruiter@example.com',
      roles: ['RECRUITER'],
      orgId: 'org-123',
    };

    vi.mocked(getAccessToken).mockReturnValue('valid-token');
    vi.mocked(isTokenExpired).mockReturnValue(false);
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/admin/overview']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/admin/overview"
              element={
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <div data-testid="admin-content">Admin Overview</div>
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
    });

    expect(screen.queryByText('Admin Overview')).not.toBeInTheDocument();
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