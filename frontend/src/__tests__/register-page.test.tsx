import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

// The registration page goes through AuthProvider, so the API boundary is
// mocked exactly where the provider calls it.
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
import { RegisterPage } from '../pages/auth/RegisterPage';
import { authApi } from '../api/client';

function renderRegister(path = '/register?role=CANDIDATE') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

function fillAccountFields(password = 'correct-horse-battery') {
  fireEvent.change(screen.getByLabelText(/^Email/), {
    target: { value: 'new@example.com' },
  });
  fireEvent.change(screen.getByLabelText(/^Password/), {
    target: { value: password },
  });
  fireEvent.change(screen.getByLabelText(/^Confirm password/), {
    target: { value: password },
  });
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('defaults to candidate registration and preserves the role in the sign-in link', async () => {
    renderRegister();

    expect(
      await screen.findByRole('heading', { name: /create an account/i })
    ).toBeInTheDocument();
    expect(screen.getByText('Candidate registration')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /candidate/i })).toBeChecked();
    expect(screen.queryByLabelText(/organization name/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/administrator invitation code/i)
    ).not.toBeInTheDocument();

    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login?role=CANDIDATE'
    );
  });

  it('reveals the organization field and submits a recruiter registration payload', async () => {
    renderRegister();

    fireEvent.click(await screen.findByRole('radio', { name: /recruiter/i }));
    expect(screen.getByText('Recruiter registration')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /recruiter/i })).toBeChecked();

    fillAccountFields();
    fireEvent.change(screen.getByLabelText(/organization name/i), {
      target: { value: '  Acme Corp  ' },
    });

    vi.mocked(authApi.register).mockResolvedValue({ userId: 'user-recruiter' });
    fireEvent.click(screen.getByRole('button', { name: /create recruiter account/i }));

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith({
        email: 'new@example.com',
        password: 'correct-horse-battery',
        role: 'RECRUITER',
        organizationName: 'Acme Corp',
      });
    });

    // Successful registration lands on sign-in with the account prefilled and a
    // success notice, rather than silently signing the user in.
    expect(await screen.findByText('Account created')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Email/)).toHaveValue('new@example.com');
    expect(screen.getByRole('radio', { name: /recruiter/i })).toBeChecked();
  });

  it('sends administrator registration bookmarks to the administrator sign-in', async () => {
    renderRegister('/register?role=ADMIN');

    expect(await screen.findByRole('heading', { name: /^sign in$/i })).toBeInTheDocument();
    expect(screen.queryByText('Administrator registration')).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/administrator invitation code/i)
    ).not.toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords before calling the API', async () => {
    renderRegister();

    fireEvent.change(await screen.findByLabelText(/^Email/), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/^Password/), {
      target: { value: 'correct-horse-battery' },
    });
    fireEvent.change(screen.getByLabelText(/^Confirm password/), {
      target: { value: 'different-password' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create candidate account/i }));

    expect(await screen.findByText('The two passwords do not match.')).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });
});
