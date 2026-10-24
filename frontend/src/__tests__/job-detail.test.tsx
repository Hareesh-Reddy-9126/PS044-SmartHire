import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobDetailPage } from '../pages/public/JobDetailPage';
import { ApiErrorResponse, jobsApi } from '../api/client';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return { ...actual, jobsApi: { get: vi.fn() } };
});

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));

import { useAuth } from '../context/AuthContext';

const job = {
  jobId: 'job-123',
  companyName: 'Acme Corp',
  role: 'Backend Engineer',
  location: 'London, UK',
  status: 'OPEN' as const,
  ownerOrgId: 'org-456',
};

function renderPage(auth: {
  user: { sub: string; email: string; roles: string[]; orgId: string | null } | null;
  isLoading: boolean;
} = { user: null, isLoading: false }) {
  vi.mocked(useAuth).mockReturnValue({
    ...auth,
    isAuthenticated: Boolean(auth.user),
    tokenExpiresAt: 0,
    login: vi.fn(), register: vi.fn(), logout: vi.fn(), refreshUser: vi.fn(), hasRole: vi.fn(),
  });
  return render(
    <MemoryRouter initialEntries={['/jobs/job-123']}>
      <Routes>
        <Route path="/jobs/:jobId" element={<JobDetailPage />} />
        <Route path="/jobs/:jobId/apply" element={<div>Application form</div>} />
        <Route path="/login" element={<div>Login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function apiError(status: number) {
  return new ApiErrorResponse({ type: 'about:blank', title: 'Request failed', status, detail: 'Request failed', instance: '/api/v1/jobs/job-123', correlationId: 'corr-123' });
}

describe('JobDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the fields returned by the job API', async () => {
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    renderPage();
    expect(screen.getByLabelText('Loading job details')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0);
    expect(screen.getAllByText('London, UK').length).toBeGreaterThan(0);
    expect(screen.getAllByText('job-123').length).toBeGreaterThan(0);
    expect(screen.getByText(/A job description, salary, skills, and employment details are not available/i)).toBeInTheDocument();
  });

  it('shows a retry action for API errors', async () => {
    vi.mocked(jobsApi.get).mockRejectedValue(apiError(503));
    renderPage();
    expect(await screen.findByRole('heading', { name: "We couldn't load this posting" })).toBeInTheDocument();
    expect(jobsApi.get).toHaveBeenCalledTimes(1);
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument());
    expect(jobsApi.get).toHaveBeenCalledTimes(2);
  });

  it('shows the unavailable state for a not-found response', async () => {
    vi.mocked(jobsApi.get).mockRejectedValue(apiError(404));
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Job posting not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to jobs' })).toHaveAttribute('href', '/jobs');
  });

  it('navigates a signed-in candidate to the apply route', async () => {
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    renderPage({ user: { sub: 'candidate-1', email: 'candidate@example.com', roles: ['CANDIDATE'], orgId: null }, isLoading: false });
    fireEvent.click((await screen.findAllByRole('button', { name: 'Apply now' }))[0]);
    expect(await screen.findByText('Application form')).toBeInTheDocument();
  });
});