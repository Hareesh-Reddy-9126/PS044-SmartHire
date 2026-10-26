import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  applicationsApi,
  ApplicationResponse,
  jobsApi,
  JobResponse,
  recruitmentsApi,
  RecruitmentResponse,
} from '../../api/client';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { ConfirmDialog } from '../../components/ui/Modal';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Timeline, TimelineItem } from '../../components/ui/Timeline';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { CopyableId } from '../../components/ui/CopyableId';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { PageHeader } from '../../components/ui/PageHeader';
import { Breadcrumbs } from '../../components/ui/Breadcrumbs';
import { useToast } from '../../context/ToastContext';
import { formatDay, formatTime } from '../../utils/format';
import {
  errorMessage,
  isConflict,
  isForbidden,
  isNotFound,
  transitionConflictMessage,
} from '../../utils/errors';

/** The linear happy path; off-ramps are declared as terminal values. */
const APPLICATION_STEPS = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'HIRED'];

/** Statuses that end the application outside the happy path. */
const TERMINAL_STATUSES = ['REJECTED', 'WITHDRAWN'];

/** Optional interview detail rows, only shown once a schedule exists. */
const SCHEDULED_STATES = ['SCHEDULED', 'COMPLETED', 'NO_SHOW'];

/** The interview card heading follows the recorded interview state. */
const INTERVIEW_SECTION_TITLE: Record<string, string> = {
  SCHEDULED: 'Interview scheduled',
  COMPLETED: 'Interview completed',
  NO_SHOW: 'Interview not attended',
};

const NEXT_STEP_COPY: Record<string, string> = {
  APPLIED: 'Your application has been received and is waiting to be reviewed.',
  UNDER_REVIEW: 'A recruiter is reviewing your application.',
  SHORTLISTED: "You've been shortlisted. The recruiter may schedule an interview next.",
  HIRED: "Congratulations — you've been hired for this role.",
  REJECTED: 'This application was not taken forward.',
  WITHDRAWN: 'You withdrew this application.',
};

export function ApplicationDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>();
  const { showToast } = useToast();

  const [application, setApplication] = useState<ApplicationResponse | null>(null);
  const [job, setJob] = useState<JobResponse | null>(null);
  const [recruitment, setRecruitment] = useState<RecruitmentResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [notFound, setNotFound] = useState(false);
  const [isWithdrawOpen, setIsWithdrawOpen] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);

  const load = useCallback(async () => {
    if (!applicationId) return;

    setIsLoading(true);
    setLoadError(null);
    setNotFound(false);

    try {
      const found = await applicationsApi.get(applicationId);

      // Job context and any linked recruitment are enrichment: neither should
      // prevent the application itself from rendering.
      const [jobResult, recruitmentResult] = await Promise.allSettled([
        jobsApi.get(found.jobId),
        recruitmentsApi.list(0, 100),
      ]);

      setApplication(found);

      if (jobResult.status === 'fulfilled') {
        setJob(jobResult.value);
      } else {
        setJob(null);
      }

      if (recruitmentResult.status === 'fulfilled') {
        const all = recruitmentResult.value.content ?? [];
        setRecruitment(all.find(item => item.applicationId === found.applicationId) ?? null);
      } else {
        setRecruitment(null);
      }
    } catch (error) {
      if (isNotFound(error)) setNotFound(true);
      else setLoadError(error);
    } finally {
      setIsLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    void load();
  }, [load]);

  const withdraw = async () => {
    if (!applicationId) return;

    setIsWithdrawing(true);
    try {
      const updated = await applicationsApi.withdraw(applicationId);
      setApplication(updated);
      setIsWithdrawOpen(false);
      showToast('Application withdrawn.', 'success');
    } catch (error) {
      if (isForbidden(error)) {
        showToast('You can only withdraw your own application.', 'error');
      } else if (isConflict(error)) {
        showToast(transitionConflictMessage(error), 'error');
        void load();
      } else {
        showToast(errorMessage(error, 'We could not withdraw this application.'), 'error');
      }
    } finally {
      setIsWithdrawing(false);
    }
  };

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl py-16 text-center">
        <SkeletonDetail />
        <p className="mt-6 font-body-md text-body-md text-c-text-muted">Loading application…</p>
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="mx-auto max-w-xl py-14 text-center">
        <h1 className="font-headline-lg text-headline-lg text-on-surface">
          We couldn't find that application
        </h1>
        <p className="font-body-md text-body-md text-c-text-muted mt-2">
          It may have been removed, or you may not have access to it.
        </p>
        <Link to="/candidate/applications" className="btn btn-secondary btn-md mt-6">
          My applications
        </Link>
      </div>
    );
  }

  if (loadError || !application) {
    return (
      <div className="mx-auto max-w-xl py-14 text-center">
        <Alert tone="danger" title="Something went wrong">
          {errorMessage(loadError, "We couldn't load this application.")}
        </Alert>
        <div className="mt-6 flex justify-center">
          <Button onClick={() => void load()}>Try again</Button>
        </div>
      </div>
    );
  }

  const canWithdraw = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED'].includes(application.status);
  // An off-ramp replaces the remaining happy-path steps; we do not know how far
  // the application progressed before it ended, so only APPLIED is asserted.
  const timelineSteps = TERMINAL_STATUSES.includes(application.status)
    ? ['APPLIED', application.status]
    : APPLICATION_STEPS;
  const showSchedule =
    Boolean(recruitment) &&
    SCHEDULED_STATES.includes(recruitment!.interviewStatus) &&
    Boolean(recruitment!.interviewDate);

  return (
    <div className="space-y-8">
      <Breadcrumbs
        items={[
          { label: 'My applications', href: '/candidate/applications' },
          {
            label: job?.role
              ? `${job.role} (${application.applicationId.slice(0, 8)})`
              : `Application ${application.applicationId.slice(0, 8)}`,
          },
        ]}
      />

      <PageHeader
        title={job?.role ?? 'Application'}
        description={job ? `${job.companyName} · ${job.location}` : undefined}
        action={
          canWithdraw ? (
            <Button variant="danger" onClick={() => setIsWithdrawOpen(true)}>
              Withdraw application
            </Button>
          ) : (
            <StatusBadge status={application.status} />
          )
        }
      />

      <div className="grid items-start gap-8 lg:grid-cols-4">
        <div className="space-y-6 lg:col-span-2">
          <Card padding="lg">
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-5">Application status</h2>
            {/*
              Timeline items derived from APPLICATION_STEPS and the current
              application status. TimelineProps accepts `items`, not `steps`.
            */}
            <Timeline
              items={timelineSteps.map((step, index, steps) => ({
                title: step,
                status: index < steps.indexOf(application.status)
                  ? 'completed'
                  : index === steps.indexOf(application.status)
                  ? 'active'
                  : 'pending',
              })) as TimelineItem[]}
            />
          </Card>

          <Card padding="lg">
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-5">
              Your submission
            </h2>

            <DefinitionList
              items={[
                {
                  label: 'Application ID',
                  value: <CopyableId value={application.applicationId} />,
                },
                {
                  label: 'Résumé link',
                  value: (
                    <a
                      className="text-c-primary-700 underline break-all"
                      href={application.resumeLink}
                      target="_blank"
                      rel="noreferrer noopener"
                    >
                      Open résumé
                      <span className="material-symbols-outlined text-[14px] align-middle ml-1" aria-hidden="true">
                        open_in_new
                      </span>
                    </a>
                  ),
                },
                { label: 'Job ID', value: <CopyableId value={application.jobId} /> },
              ]}
            />
          </Card>
        </div>

        <div className="space-y-6 lg:col-span-2">
          <Card padding="lg">
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-4">
              What happens next
            </h2>
            <p className="font-body-md text-body-md text-c-text-muted mt-3">
              {NEXT_STEP_COPY[application.status] ?? `Current status: ${application.status}.`}
            </p>
          </Card>

          <Card padding="lg">
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-5">
              Linked recruitment
            </h2>

            {recruitment ? (
              <>
                <div className="mt-3 flex flex-wrap items-center gap-3">
                  <StatusBadge status={recruitment.interviewStatus} size="sm" />
                  <StatusBadge status={recruitment.finalStatus} size="sm" />
                </div>

                {showSchedule && (
                  <div className="mt-5 space-y-4 rounded-xl bg-c-primary-50 border border-c-primary-100 p-4">
                    <h3 className="font-caption text-caption uppercase tracking-wide text-c-text-subtle">
                      {INTERVIEW_SECTION_TITLE[recruitment.interviewStatus] ?? 'Interview'}
                    </h3>
                    <dl className="grid grid-cols-2 gap-4">
                      <div>
                        <dt className="font-caption text-caption text-c-text-subtle">Date</dt>
                        <dd className="font-body-sm text-body-sm text-on-surface mt-0.5">
                          {formatDay(recruitment.interviewDate)}
                        </dd>
                      </div>
                      <div>
                        <dt className="font-caption text-caption text-c-text-subtle">Time</dt>
                        <dd className="font-body-sm text-body-sm text-on-surface mt-0.5">
                          {recruitment.interviewStartTime
                            ? formatTime(recruitment.interviewStartTime)
                            : '—'}
                        </dd>
                      </div>
                      <div>
                        <dt className="font-caption text-caption text-c-text-subtle">Interviewer</dt>
                        <dd className="font-body-sm text-body-sm text-on-surface mt-0.5">
                          {recruitment.interviewer ?? '—'}
                        </dd>
                      </div>
                      <div>
                        <dt className="font-caption text-caption text-c-text-subtle">Location</dt>
                        <dd className="font-body-sm text-body-sm text-on-surface mt-0.5">
                          {recruitment.interviewLocation ?? '—'}
                        </dd>
                      </div>
                    </dl>
                  </div>
                )}

                <Link
                  className="btn btn-secondary btn-sm mt-5"
                  to={`/candidate/progress/${recruitment.recruitmentId}`}
                >
                  View progress
                </Link>
              </>
            ) : (
              <p className="font-body-md text-body-md text-c-text-muted mt-3">
                No interview process has started yet. A recruiter starts one when they shortlist you.
              </p>
            )}
          </Card>
        </div>
      </div>

      {isWithdrawOpen && (
        <ConfirmDialog
          onCancel={() => setIsWithdrawOpen(false)}
          onConfirm={() => void withdraw()}
          confirmLoading={isWithdrawing}
          title="Withdraw this application?"
          message={`This cannot be undone. The recruiter will no longer consider you for ${job?.role ?? 'this role'}.`}
          confirmLabel="Withdraw application"
          cancelLabel="Cancel"
        />
      )}
    </div>
  );
}
