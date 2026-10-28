import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApplicationSubmitPage } from '../pages/candidate/ApplicationSubmitPage';
import { ApiErrorResponse, applicationsApi, jobsApi } from '../api/client';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    jobsApi: { get: vi.fn() },
    applicationsApi: { create: vi.fn(), list: vi.fn() },
  };
});

const job = { jobId: 'job-123', companyName: 'Acme Corp', role: 'Backend Engineer', location: 'London', status: 'OPEN' as const, ownerOrgId: 'org-1' };

function renderPage() {
  return render(<MemoryRouter initialEntries={['/jobs/job-123/apply']}><Routes><Route path="/jobs/:jobId/apply" element={<ApplicationSubmitPage />} /><Route path="/candidate/applications/:applicationId" element={<div>Application destination</div>} /></Routes></MemoryRouter>);
}

function apiError(status: number, detail = 'Server rejected the application') {
  return new ApiErrorResponse({ type: 'about:blank', title: 'Request failed', status, detail, instance: '/api/v1/applications', correlationId: 'corr-1' });
}

describe('ApplicationSubmitPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    vi.mocked(applicationsApi.list).mockResolvedValue({
      content: [],
      page: { size: 100, number: 0, totalElements: 0, totalPages: 0 },
    });
  });

  it('loads the job and submits a résumé link', async () => {
    vi.mocked(applicationsApi.create).mockResolvedValue({ ...job, applicationId: 'application-1', candidateId: 'candidate-1', resumeLink: 'https://example.com/resume.pdf', status: 'APPLIED' });
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Apply for a job' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Resume link/), { target: { value: 'https://example.com/resume.pdf' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
    await waitFor(() => expect(applicationsApi.create).toHaveBeenCalledWith({ jobId: 'job-123', resumeLink: 'https://example.com/resume.pdf' }));
    expect(await screen.findByText('Application destination')).toBeInTheDocument();
  });

  it('validates a missing or malformed résumé link', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Apply for a job' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
    expect(screen.getByText('Enter a link to your resume before applying.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Resume link/), { target: { value: 'not-a-url' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
    expect(screen.getByText('Enter a valid http or https resume link.')).toBeInTheDocument();
    expect(applicationsApi.create).not.toHaveBeenCalled();
  });

  it('shows the server error without claiming success', async () => {
    vi.mocked(applicationsApi.create).mockRejectedValue(apiError(500));
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Apply for a job' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Resume link/), { target: { value: 'https://example.com/resume.pdf' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Server rejected the application');
    expect(screen.queryByText('Application submitted')).not.toBeInTheDocument();
  });

  it('redirects a duplicate application to the existing application record', async () => {
    vi.mocked(applicationsApi.create).mockRejectedValue(apiError(409, 'You already have an active application for this job.'));
    vi.mocked(applicationsApi.list).mockResolvedValue({
      content: [
        {
          applicationId: 'application-7',
          jobId: 'job-123',
          candidateId: 'candidate-1',
          resumeLink: 'https://example.com/resume.pdf',
          status: 'APPLIED',
        },
      ],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    });

    renderPage();
    expect(await screen.findByRole('heading', { name: 'Apply for a job' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Resume link/), { target: { value: 'https://example.com/resume.pdf' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));

    expect(await screen.findByText('Application destination')).toBeInTheDocument();
  });

  it('explains a duplicate application when no active record can be found', async () => {
    vi.mocked(applicationsApi.create).mockRejectedValue(apiError(409, 'You already have an active application for this job.'));

    renderPage();
    expect(await screen.findByRole('heading', { name: 'Apply for a job' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Resume link/), { target: { value: 'https://example.com/resume.pdf' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('You already have an active application for this job');
  });
});