import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { applicationsApi, documentsApi, jobsApi, notificationsApi, recruitmentsApi, RecruitmentResponse } from '../api/client';
import { CandidateApplicationsPage } from '../pages/candidate/CandidateApplicationsPage';
import { ApplicationDetailPage } from '../pages/candidate/ApplicationDetailPage';
import { DocumentsPage } from '../pages/candidate/DocumentsPage';
import { NotificationsPage } from '../pages/candidate/NotificationsPage';
import { ProgressPage } from '../pages/candidate/ProgressPage';
import { ProgressDetailPage } from '../pages/candidate/ProgressDetailPage';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    applicationsApi: { get: vi.fn(), withdraw: vi.fn(), list: vi.fn() },
    documentsApi: { list: vi.fn(), listByType: vi.fn(), getContent: vi.fn(), upload: vi.fn(), delete: vi.fn() },
    jobsApi: { get: vi.fn(), list: vi.fn() },
    notificationsApi: { list: vi.fn(), get: vi.fn() },
    recruitmentsApi: { list: vi.fn(), get: vi.fn() },
  };
});

vi.mock('../context/ToastContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../context/ToastContext')>();
  return {
    ...actual,
    useToast: () => ({ showToast: vi.fn() }),
  };
});
import { AuthProvider } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';

const application = { applicationId: 'application-1', jobId: 'job-1', candidateId: 'candidate-1', resumeLink: 'https://example.test/resume.pdf', status: 'APPLIED' } as const;
const job = { jobId: 'job-1', companyName: 'Acme Corp', role: 'Backend Engineer', location: 'London', status: 'OPEN', ownerOrgId: 'org-1' } as const;
const recruitment: RecruitmentResponse = { recruitmentId: 'recruitment-1', applicationId: 'application-1', jobId: 'job-1', candidateId: 'candidate-1', interviewStatus: 'SCHEDULED', interviewDate: '2026-10-15', interviewStartTime: '10:30', interviewer: 'Alex Recruiter', interviewLocation: 'Room 4B', finalStatus: 'PENDING', createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T10:00:00Z' };
const recruitmentNotScheduled: RecruitmentResponse = { recruitmentId: 'recruitment-2', applicationId: 'application-2', jobId: 'job-1', candidateId: 'candidate-1', interviewStatus: 'NOT_SCHEDULED', interviewDate: null, interviewStartTime: null, interviewer: null, interviewLocation: null, finalStatus: 'PENDING', createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T10:00:00Z' };
const recruitmentPartial: RecruitmentResponse = { recruitmentId: 'recruitment-3', applicationId: 'application-3', jobId: 'job-1', candidateId: 'candidate-1', interviewStatus: 'SCHEDULED', interviewDate: '2026-10-20', interviewStartTime: null, interviewer: 'Sam', interviewLocation: null, finalStatus: 'PENDING', createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T10:00:00Z' };

function renderRoute(element: React.ReactNode, path: string, initialEntry = path) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path={path} element={element} />
          <Route path="/candidate/applications/application-1" element={<div>Application destination</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

function renderProgressDetail(recruitmentData: typeof recruitment) {
  vi.mocked(recruitmentsApi.get).mockResolvedValue(recruitmentData);
  vi.mocked(jobsApi.get).mockResolvedValue(job);
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/candidate/progress/recruitment-1']}>
        <Routes>
          <Route path="/candidate/progress/:recruitmentId" element={<ProgressDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('candidate workflows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(recruitmentsApi.list).mockResolvedValue({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
    vi.mocked(jobsApi.list).mockResolvedValue({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
  });

it('lists the candidate applications from the server', async () => {
    vi.mocked(applicationsApi.list).mockResolvedValue({ content: [application], page: { size: 10, number: 0, totalElements: 1, totalPages: 1 } });
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    renderRoute(<CandidateApplicationsPage />, '/candidate/applications');
    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getByText('APPLIED')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      '/candidate/applications/application-1'
    );
  });

  it('renders application detail and withdraws an active application', async () => {
    vi.mocked(applicationsApi.get).mockResolvedValue(application);
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    vi.mocked(applicationsApi.withdraw).mockResolvedValue({ ...application, status: 'WITHDRAWN' });
    render(
      <AuthProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/candidate/applications/application-1']}>
            <Routes>
              <Route path="/candidate/applications/:applicationId" element={<ApplicationDetailPage />} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </AuthProvider>
    );
    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Withdraw application' }));
    const withdrawButtons = screen.getAllByRole('button', { name: 'Withdraw application' });
    fireEvent.click(withdrawButtons[withdrawButtons.length - 1]);
    expect((await screen.findAllByText('WITHDRAWN')).length).toBeGreaterThan(0);
    expect(applicationsApi.withdraw).toHaveBeenCalledWith('application-1');
  });

  it('renders candidate recruitment progress from the server page', async () => {
    vi.mocked(recruitmentsApi.list).mockResolvedValue({ content: [recruitment], page: { size: 100, number: 0, totalElements: 1, totalPages: 1 } });
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    renderRoute(<ProgressPage />, '/candidate/progress');
    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getByText('Acme Corp · London')).toBeInTheDocument();
    expect(screen.getByText('SCHEDULED')).toBeInTheDocument();
    expect(screen.getByText('PENDING')).toBeInTheDocument();
  });

it('displays interview details when scheduled', async () => {
    renderProgressDetail(recruitment);
    expect(await screen.findByText('Interview details')).toBeInTheDocument();
    expect(screen.getAllByText(/15 Oct 2026/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('10:30').length).toBeGreaterThan(0);
    expect(screen.getByText('Alex Recruiter')).toBeInTheDocument();
    expect(screen.getByText('Room 4B')).toBeInTheDocument();
  });

  it('hides interview details when not scheduled', async () => {
    renderProgressDetail(recruitmentNotScheduled);
    // Wait for loading to complete.
    await screen.findByRole('heading', { name: 'Progress' });
    expect(screen.queryByText('Interview details')).not.toBeInTheDocument();
    // The status badge shows NOT_SCHEDULED in the header.
    expect(screen.getByText('NOT_SCHEDULED')).toBeInTheDocument();
  });

it('handles missing schedule fields gracefully', async () => {
    renderProgressDetail(recruitmentPartial);
    expect(await screen.findByText('Interview details')).toBeInTheDocument();
    expect(screen.getAllByText(/20 Oct 2026/).length).toBeGreaterThan(0);
    expect(screen.getByText('Sam')).toBeInTheDocument();
    // A missing start time falls back to an em dash; a missing location is
    // explicitly reported as not recorded rather than silently blank.
    expect(screen.getByText('Start time').parentElement).toHaveTextContent('—');
    expect(screen.getByText('Location').parentElement).toHaveTextContent('Not recorded');
  });

  it('lists documents and removes one after confirmation', async () => {
    vi.mocked(documentsApi.list).mockResolvedValue({ content: [{ documentId: 'document-1', candidateId: 'candidate-1', documentType: 'RESUME', fileName: 'resume.pdf', contentType: 'application/pdf', fileSize: 1024, createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T10:00:00Z' }], pageable: { pageNumber: 0, pageSize: 20, sort: { sorted: false, unsorted: true, empty: true } }, totalElements: 1, totalPages: 1, last: true, size: 20, number: 0, sort: { sorted: false, unsorted: true, empty: true }, numberOfElements: 1, first: true, empty: false });
    vi.mocked(documentsApi.delete).mockResolvedValue(undefined);
    renderRoute(<DocumentsPage />, '/candidate/documents');
    expect(await screen.findByText('resume.pdf')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    const confirmButtons = screen.getAllByRole('button', { name: 'Delete' });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);
    expect(await screen.findByText('No documents yet')).toBeInTheDocument();
    expect(documentsApi.delete).toHaveBeenCalledWith('document-1');
  });

  it('uploads a document with the selected type', async () => {
    vi.mocked(documentsApi.list).mockResolvedValue({ content: [], pageable: { pageNumber: 0, pageSize: 10, sort: { sorted: false, unsorted: true, empty: true } }, totalElements: 0, totalPages: 0, last: true, size: 10, number: 0, sort: { sorted: false, unsorted: true, empty: true }, numberOfElements: 0, first: true, empty: true });
    vi.mocked(documentsApi.upload).mockResolvedValue({ documentId: 'document-2', candidateId: 'candidate-1', documentType: 'COVER_LETTER', fileName: 'cover.pdf', contentType: 'application/pdf', fileSize: 12, createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T10:00:00Z' });
    renderRoute(<DocumentsPage />, '/candidate/documents');
    expect(await screen.findByText('No documents yet')).toBeInTheDocument();

    const file = new File(['cover letter'], 'cover.pdf', { type: 'application/pdf' });
    fireEvent.change(document.getElementById('documentFile')!, { target: { files: [file] } });
    fireEvent.change(document.getElementById('documentType')!, { target: { value: 'COVER_LETTER' } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));

    await waitFor(() =>
      expect(documentsApi.upload).toHaveBeenCalledWith({ documentType: 'COVER_LETTER', file })
    );
  });

  it('reads notifications from the backend HATEOAS embedded key', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue({ _embedded: { notificationResponseList: [{ notificationId: 'notification-1', candidateId: 'candidate-1', applicationId: 'application-1', eventStatus: 'HIRED', recipientEmail: 'candidate@example.com', subject: 'Application update', message: 'Your application has changed.', deliveryStatus: 'SENT', createdAt: '2026-10-14T10:00:00Z', sentAt: '2026-10-14T10:00:01Z', errorMessage: null }] }, _links: { first: { href: '' }, last: { href: '' }, self: { href: '' } }, page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } });
    renderRoute(<NotificationsPage />, '/candidate/notifications');
    expect(await screen.findByText('Application update')).toBeInTheDocument();
    expect(screen.getByText('Your application has changed.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Application update/i })).toHaveAttribute('href', '/candidate/notifications/notification-1');
  });
});
