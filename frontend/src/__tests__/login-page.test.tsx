import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

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
});
