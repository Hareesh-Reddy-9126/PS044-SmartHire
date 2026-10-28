import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobBrowsePage } from '../pages/public/JobBrowsePage';
import { useAuth } from '../context/AuthContext';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return { ...actual, jobsApi: { ...actual.jobsApi, list: vi.fn() } };
});

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));

import { jobsApi as mockedJobsApi } from '../api/client';

const jobs = [
  {
    jobId: 'job-1',
    companyName: 'Acme Corp',
    role: 'Backend Engineer',
    location: 'Remote',
    status: 'OPEN' as const,
    ownerOrgId: 'org-1',
  },
  {
    jobId: 'job-2',
    companyName: 'Globex Inc',
    role: 'Data Analyst',
    location: 'Bengaluru, IN',
    status: 'OPEN' as const,
    ownerOrgId: 'org-1',
  },
];

function renderPage(auth: {
  user: { sub: string; email: string; roles: string[]; orgId: string | null } | null;
  isLoading: boolean;
} = { user: null, isLoading: false }) {
  vi.mocked(useAuth).mockReturnValue({
    ...auth,
    isAuthenticated: Boolean(auth.user),
    tokenExpiresAt: 0,
    login: vi.fn(), loginWithGoogle: vi.fn(), register: vi.fn(), logout: vi.fn(), refreshUser: vi.fn(), hasRole: vi.fn(),
  });
  return render(
    <MemoryRouter initialEntries={['/jobs']}>
      <Routes>
        <Route path="/jobs" element={<JobBrowsePage />} />
        <Route path="/login" element={<div>Login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('JobBrowsePage - public access', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('unauthenticated users can browse public jobs', async () => {
    vi.mocked(mockedJobsApi.list).mockResolvedValue({
      content: jobs,
      page: { size: 10, number: 0, totalElements: 2, totalPages: 1 },
    });
    renderPage({ user: null, isLoading: false });

    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getByText('Globex Inc')).toBeInTheDocument();
  });

  it('authenticated candidate users can browse public jobs', async () => {
    vi.mocked(mockedJobsApi.list).mockResolvedValue({
      content: jobs,
      page: { size: 10, number: 0, totalElements: 2, totalPages: 1 },
    });
    renderPage({
      user: { sub: 'candidate-1', email: 'c@example.com', roles: ['CANDIDATE'], orgId: null },
      isLoading: false,
    });

    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getByText('Data Analyst')).toBeInTheDocument();
  });

  it('authenticated recruiter users can browse public jobs', async () => {
    vi.mocked(mockedJobsApi.list).mockResolvedValue({
      content: jobs,
      page: { size: 10, number: 0, totalElements: 2, totalPages: 1 },
    });
    renderPage({
      user: { sub: 'recruiter-1', email: 'r@example.com', roles: ['RECRUITER'], orgId: 'org-1' },
      isLoading: false,
    });

    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getByText('Data Analyst')).toBeInTheDocument();
  });

  it('a failed API call on the public jobs page does not redirect to login', async () => {
    vi.mocked(mockedJobsApi.list).mockRejectedValue(
      new Error('Failed to fetch')
    );
    renderPage({ user: null, isLoading: false });

    // Should show an error state, not redirect to login
    await waitFor(() => {
      expect(screen.getByText(/couldn't load/i)).toBeInTheDocument();
    });
    expect(screen.queryByText('Login page')).not.toBeInTheDocument();
  });
});
