import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

// LoginPage renders through AuthProvider, so the API boundary is mocked at the
// same level the provider uses it. No credentials are involved: the point is to
// prove that no development-only recruiter/admin credentials are shipped in the
// production sign-in screen.
vi.mock('../api/client', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(() => Promise.reject(new Error('no session in test'))),
    register: vi.fn(),
  },
  getAccessToken: vi.fn(() => null),
  clearAccessToken: vi.fn(),
  isTokenExpired: vi.fn(() => true),
  setAccessToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
  restoreSession: vi.fn(() => Promise.resolve(false)),
  tryRefreshSession: vi.fn(() => Promise.resolve(false)),
  accessTokenExpiry: vi.fn(() => 0),
  endSession: vi.fn(() => Promise.resolve()),
}));

import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from '../pages/auth/LoginPage';
import { authApi } from '../api/client';

function WorkspaceProbe({ title }: { title: string }) {
  const location = useLocation();
  const notice = (
    location.state as { notice?: { title: string; message: string } } | null
  )?.notice;

  return (
    <div>
      <h1>{title}</h1>
      {notice && (
        <div>
          <p>{notice.title}</p>
          <p>{notice.message}</p>
        </div>
      )}
    </div>
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not ship development recruiter or admin credentials', async () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>
    );

    await screen.findByRole('heading', { name: /sign in/i });

    await waitFor(() => expect(screen.queryByText(/development credentials/i)).not.toBeInTheDocument());
    for (const forbidden of [
      'recruiter@smarthire.local',
      'Recruiter123!',
      'admin@smarthire.local',
      'Admin123!',
    ]) {
      expect(document.body.innerHTML).not.toContain(forbidden);
    }
  });

  it('redirects to a valid next path after login for the signed-in role', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'candidate@example.com',
      roles: ['CANDIDATE'],
      orgId: null,
    };

    vi.mocked(authApi.login).mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access-token-123',
      accessTokenExpiresAt: '2026-10-25T14:00:00Z',
    });
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/login?next=/candidate/applications']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/candidate/applications" element={<div data-testid="candidate-applications">Candidate Applications</div>} />
            <Route path="/recruiter/jobs" element={<div data-testid="recruiter-jobs">Recruiter Jobs</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Email/), {
      target: { value: 'candidate@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/Password/), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Candidate Applications')).toBeInTheDocument();
    });
    expect(screen.queryByText('Sign in')).not.toBeInTheDocument();
  });

  it('drops a next path aimed at another role and redirects to role home', async () => {
    const mockMeResponse = {
      sub: 'user-456',
      email: 'recruiter@example.com',
      roles: ['RECRUITER'],
      orgId: 'org-123',
    };

    vi.mocked(authApi.login).mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access-token-123',
      accessTokenExpiresAt: '2026-10-25T14:00:00Z',
    });
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/login?next=/candidate/applications']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/candidate/applications" element={<div data-testid="candidate-applications">Candidate Applications</div>} />
            <Route path="/recruiter/jobs" element={<div data-testid="recruiter-jobs">Recruiter Jobs</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Email/), {
      target: { value: 'recruiter@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/Password/), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Recruiter Jobs')).toBeInTheDocument();
    });
    expect(screen.queryByText('Candidate Applications')).not.toBeInTheDocument();
    expect(screen.queryByText('Sign in')).not.toBeInTheDocument();
  });

  it('rejects an external next path and redirects to role home', async () => {
    const mockMeResponse = {
      sub: 'user-123',
      email: 'candidate@example.com',
      roles: ['CANDIDATE'],
      orgId: null,
    };

    vi.mocked(authApi.login).mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access-token-123',
      accessTokenExpiresAt: '2026-10-25T14:00:00Z',
    });
    vi.mocked(authApi.me).mockResolvedValue(mockMeResponse);

    render(
      <MemoryRouter initialEntries={['/login?next=https://evil.com/phish']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/candidate/applications" element={<div data-testid="candidate-applications">Candidate Applications</div>} />
            <Route path="/recruiter/jobs" element={<div data-testid="recruiter-jobs">Recruiter Jobs</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Email/), {
      target: { value: 'candidate@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/Password/), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Candidate Applications')).toBeInTheDocument();
    });
    expect(screen.queryByText('Sign in')).not.toBeInTheDocument();
  });

  it('lets the visitor choose a portal and carries that role into registration', async () => {
    render(
      <MemoryRouter initialEntries={['/login?role=RECRUITER']}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>
    );

    await screen.findByRole('heading', { name: /sign in/i });
    expect(
      screen.getByText(/Publish roles, review applicants and record interview/i)
    ).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /recruiter/i })).toBeChecked();
    expect(screen.getByRole('link', { name: /create a recruiter account/i })).toHaveAttribute(
      'href',
      '/register?role=RECRUITER'
    );

    fireEvent.click(screen.getByRole('radio', { name: /candidate/i }));

    expect(
      screen.getByText(/Track your applications, documents and interview progress/i)
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /create a candidate account/i })).toHaveAttribute(
      'href',
      '/register?role=CANDIDATE'
    );
  });

  it('explains a portal mismatch and opens the account workspace instead of failing', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access-token-123',
      accessTokenExpiresAt: '2026-10-25T14:00:00Z',
    });
    vi.mocked(authApi.me).mockResolvedValue({
      sub: 'user-456',
      email: 'recruiter@example.com',
      roles: ['RECRUITER'],
      orgId: 'org-123',
    });

    render(
      <MemoryRouter initialEntries={['/login?role=CANDIDATE']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/recruiter/jobs" element={<WorkspaceProbe title="Recruiter Jobs" />} />
            <Route
              path="/candidate/applications"
              element={<WorkspaceProbe title="Candidate Applications" />}
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    fireEvent.change(await screen.findByLabelText(/Email/), {
      target: { value: 'recruiter@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/Password/), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Recruiter Jobs')).toBeInTheDocument();
    expect(
      screen.getByText('Signed in to the Recruiter workspace')
    ).toBeInTheDocument();
    expect(
      screen.getByText(/instead of the Candidate portal/i)
    ).toBeInTheDocument();
    expect(screen.queryByText('Candidate Applications')).not.toBeInTheDocument();
  });

  it('does not show a mismatch notice when a generic sign-in link was used', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access-token-123',
      accessTokenExpiresAt: '2026-10-25T14:00:00Z',
    });
    vi.mocked(authApi.me).mockResolvedValue({
      sub: 'user-456',
      email: 'recruiter@example.com',
      roles: ['RECRUITER'],
      orgId: 'org-123',
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/recruiter/jobs" element={<WorkspaceProbe title="Recruiter Jobs" />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );

    fireEvent.change(await screen.findByLabelText(/Email/), {
      target: { value: 'recruiter@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/Password/), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Recruiter Jobs')).toBeInTheDocument();
    expect(
      screen.queryByText('Signed in to the Recruiter workspace')
    ).not.toBeInTheDocument();
  });
});
