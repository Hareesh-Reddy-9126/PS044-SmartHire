import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi, jobsApi, recruitmentsApi } from '../api/client';
import { RecruiterApplicationLookupPage } from '../pages/recruiter/RecruiterApplicationLookupPage';
import { RecruiterRecruitmentDetailPage } from '../pages/recruiter/RecruiterRecruitmentDetailPage';
import { ToastProvider } from '../context/ToastContext';

vi.mock('../context/ToastContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../context/ToastContext')>();
  return {
    ...actual,
    useToast: () => ({ showToast: vi.fn() }),
  };
});

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    applicationsApi: { get: vi.fn() },
    jobsApi: { get: vi.fn() },
    recruitmentsApi: {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      schedule: vi.fn(),
      updateFinalStatus: vi.fn(),
    },
  };
});

const APPLICATION_ID = '5aab7ae9-d1ad-4052-a1b4-74312769bb41';
const RECRUITMENT_ID = '0f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0';

const job = {
  jobId: '6f86939e-584c-4f05-8e98-44248ab29e4e',
  companyName: 'Acme Corp',
  role: 'Backend Engineer',
  location: 'London',
  status: 'OPEN',
  ownerOrgId: '3d2c1b0a-9f8e-4d7c-8b6a-5f4e3d2c1b0a',
} as const;

const awaitingShortlist = {
  applicationId: APPLICATION_ID,
  jobId: job.jobId,
  candidateId: '7edc6a6c-afd0-49d1-b5b9-195ebdc38254',
  resumeLink: 'https://example.test/resume.pdf',
  status: 'APPLIED',
} as const;

const shortlisted = { ...awaitingShortlist, status: 'SHORTLISTED' } as const;

const scheduled = {
  recruitmentId: RECRUITMENT_ID,
  applicationId: APPLICATION_ID,
  jobId: job.jobId,
  candidateId: awaitingShortlist.candidateId,
  interviewStatus: 'SCHEDULED' as const,
  interviewDate: '2099-04-15',
  interviewStartTime: '10:30',
  interviewer: 'Alex Recruiter',
  interviewLocation: 'Room 4B',
  finalStatus: 'PENDING' as const,
};

const notScheduled = {
  ...scheduled,
  interviewStatus: 'NOT_SCHEDULED' as const,
  interviewDate: null,
  interviewStartTime: null,
  interviewer: null,
  interviewLocation: null,
};

const completed = { ...scheduled, interviewStatus: 'COMPLETED' as const };

const hired = { ...completed, finalStatus: 'HIRED' as const };

function renderWithRoutes(
  initialEntry: string,
  path: string,
  element: React.ReactNode,
  extraRoutes: React.ReactNode = null
) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path={path} element={element} />
          {extraRoutes}
        </Routes>
      </MemoryRouter>
    </ToastProvider>
  );
}

describe('recruiter interview workflow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(recruitmentsApi.list).mockResolvedValue({
      content: [],
      page: { size: 100, number: 0, totalElements: 0, totalPages: 0 },
    });
  });

  it('shortlists an application and opens the new recruitment', async () => {
    vi.mocked(applicationsApi.get).mockResolvedValue(awaitingShortlist);
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    vi.mocked(recruitmentsApi.create).mockResolvedValue(notScheduled);

    renderWithRoutes(
      '/recruiter/applications',
      '/recruiter/applications',
      <RecruiterApplicationLookupPage />,
      <Route
        path="/recruiter/recruitments/:recruitmentId"
        element={<div>Recruitment destination</div>}
      />
    );

    fireEvent.change(screen.getByLabelText(/Application ID/), {
      target: { value: APPLICATION_ID },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Look up application' }));

    expect(await screen.findByRole('heading', { name: 'Application' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Shortlist application' }));

    await waitFor(() =>
      expect(recruitmentsApi.create).toHaveBeenCalledWith(APPLICATION_ID)
    );
    expect(await screen.findByText('Recruitment destination')).toBeInTheDocument();
  });

  it('shows a loading state instead of claiming the job is gone', async () => {
    vi.mocked(applicationsApi.get).mockResolvedValue(awaitingShortlist);
    vi.mocked(jobsApi.get).mockReturnValue(new Promise(() => undefined));
    vi.mocked(recruitmentsApi.list).mockResolvedValue({
      content: [],
      page: { size: 100, number: 0, totalElements: 0, totalPages: 0 },
    });

    renderWithRoutes(
      '/recruiter/applications',
      '/recruiter/applications',
      <RecruiterApplicationLookupPage />
    );

    fireEvent.change(screen.getByLabelText(/Application ID/), {
      target: { value: APPLICATION_ID },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Look up application' }));

    expect(await screen.findByText('Loading job details…')).toBeInTheDocument();
    expect(screen.getByText('Loading recruitment…')).toBeInTheDocument();
    expect(
      screen.queryByText('The job for this application is no longer available.')
    ).not.toBeInTheDocument();
  });

  it('schedules an interview with the required details', async () => {
    vi.mocked(recruitmentsApi.get).mockResolvedValue(notScheduled);
    vi.mocked(applicationsApi.get).mockResolvedValue(shortlisted);
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    vi.mocked(recruitmentsApi.schedule).mockResolvedValue(scheduled);

    renderWithRoutes(
      `/recruiter/recruitments/${RECRUITMENT_ID}`,
      '/recruiter/recruitments/:recruitmentId',
      <RecruiterRecruitmentDetailPage />
    );

    expect(
      await screen.findByRole('heading', { name: 'Schedule the interview' })
    ).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2099-04-15' } });
    fireEvent.change(screen.getByLabelText('Start time'), { target: { value: '10:30' } });
    fireEvent.change(screen.getByLabelText('Interviewer'), {
      target: { value: 'Alex Recruiter' },
    });
    fireEvent.change(screen.getByLabelText('Location'), { target: { value: 'Room 4B' } });
    fireEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));

    await waitFor(() =>
      expect(recruitmentsApi.schedule).toHaveBeenCalledWith(RECRUITMENT_ID, {
        interviewDate: '2099-04-15',
        interviewStartTime: '10:30',
        interviewer: 'Alex Recruiter',
        interviewLocation: 'Room 4B',
      })
    );

    // The form is replaced by the stored interview details.
    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Schedule the interview' })
      ).not.toBeInTheDocument()
    );
    expect(screen.getAllByText('SCHEDULED').length).toBeGreaterThan(0);
    expect(screen.getByText('Alex Recruiter')).toBeInTheDocument();
    expect(screen.getByText('Room 4B')).toBeInTheDocument();
  });

  it('updates the application status when a candidate is hired', async () => {
    vi.mocked(recruitmentsApi.get).mockResolvedValue(completed);
    vi.mocked(applicationsApi.get).mockResolvedValue(shortlisted);
    vi.mocked(jobsApi.get).mockResolvedValue(job);
    vi.mocked(recruitmentsApi.updateFinalStatus).mockResolvedValue(hired);

    renderWithRoutes(
      `/recruiter/recruitments/${RECRUITMENT_ID}`,
      '/recruiter/recruitments/:recruitmentId',
      <RecruiterRecruitmentDetailPage />
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Hire candidate' }));

    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Hire candidate' }));

    await waitFor(() =>
      expect(recruitmentsApi.updateFinalStatus).toHaveBeenCalledWith(RECRUITMENT_ID, {
        status: 'HIRED',
      })
    );

    // The Candidate card must reflect the application's new status, not the
    // SHORTLISTED snapshot loaded before the decision.
    const applicationStatus = (await screen.findByText('Application status')).parentElement;
    expect(applicationStatus).not.toBeNull();
    expect(within(applicationStatus as HTMLElement).getByText('HIRED')).toBeInTheDocument();
  });
});
