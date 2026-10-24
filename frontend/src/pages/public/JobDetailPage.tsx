import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiErrorResponse, JobResponse, jobsApi } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { CopyableId } from '../../components/ui/CopyableId';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { Breadcrumbs } from '../../components/ui/Breadcrumbs';
import { humanise } from '../../utils/format';

type LoadState = 'loading' | 'ready' | 'not-found' | 'error';

function DetailRow({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="record-fact">
      <dt className="record-fact-label">{label}</dt>
      <dd
        className={`record-fact-value break-words ${
          mono ? 'font-code-sm text-code-sm tabular' : ''
        }`}
      >
        {value}
      </dd>
    </div>
  );
}

/** Renders the empty and error outcomes for a posting that cannot be shown. */
function EmptyJobState({ notFound, onRetry }: { notFound: boolean; onRetry?: () => void }) {
  return (
    <section className="mx-auto max-w-xl py-16 text-center">
      <span
        className={`material-symbols-outlined text-[48px] ${
          notFound ? 'text-c-text-subtle' : 'text-danger-600'
        }`}
        aria-hidden="true"
      >
        {notFound ? 'search_off' : 'cloud_off'}
      </span>

      <h1 className="page-title mt-4">
        {notFound ? 'Job posting not found' : "We couldn't load this posting"}
      </h1>

      <p className="mx-auto mt-3 max-w-prose font-body-md text-body-md text-c-text-muted">
        {notFound
          ? 'This posting may have been removed, or it may not be visible to you.'
          : 'The job service did not respond. Try again, or return to the list of open roles.'}
      </p>

      <div className="mt-8 flex flex-wrap justify-center gap-3">
        {!notFound && onRetry && <Button onClick={onRetry}>Try again</Button>}
        <Link to="/jobs" className="btn btn-secondary btn-md">
          Back to jobs
        </Link>
      </div>
    </section>
  );
}

export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { user, isLoading: isAuthLoading } = useAuth();

  const [job, setJob] = useState<JobResponse | null>(null);
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoadState('loading');

    if (!jobId) {
      setLoadState('not-found');
      return;
    }

    jobsApi
      .get(jobId)
      .then(result => {
        if (cancelled) return;
        setJob(result);
        setLoadState('ready');
      })
      .catch(error => {
        if (cancelled) return;
        setJob(null);
        const notFound = error instanceof ApiErrorResponse && error.error.status === 404;
        setLoadState(notFound ? 'not-found' : 'error');
      });

    return () => {
      cancelled = true;
    };
  }, [jobId, attempt]);

  const retry = useCallback(() => setAttempt(count => count + 1), []);

  if (loadState === 'loading') {
    return (
      <div className="mx-auto max-w-xl py-16 text-center">
        <SkeletonDetail label="Loading job details" />
        <p className="mt-6 font-body-md text-body-md text-c-text-muted">Loading job details…</p>
      </div>
    );
  }
  if (loadState === 'not-found') return <EmptyJobState notFound />;
  if (loadState === 'error') return <EmptyJobState notFound={false} onRetry={retry} />;
  if (!job) return null;

  const isCandidate = user?.roles?.includes('CANDIDATE') ?? false;
  const isOwnerRecruiter =
    (user?.roles?.includes('RECRUITER') ?? false) && user?.orgId === job.ownerOrgId;
  const isOpen = job.status === 'OPEN';
  const canApply = !isAuthLoading && isCandidate && isOpen;
  const applyPath = `/jobs/${job.jobId}/apply`;
  const signInPath = `/login?next=${encodeURIComponent(applyPath)}`;

  return (
    <div className="space-y-8">
      <Breadcrumbs
        items={[
          { label: 'Jobs', href: '/jobs' },
          { label: job.role },
        ]}
      />

      <header className="record-hero">
        <div className="flex flex-col gap-7 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <p className="record-kicker">Role record</p>
            <h1 className="record-title">{job.role}</h1>
            <div className="record-meta">
              <span>
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                  domain
                </span>
                {job.companyName}
              </span>
              <span>
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                  location_on
                </span>
                {job.location}
              </span>
              <span>
                <StatusBadge status={job.status} size="sm" />
              </span>
            </div>
          </div>

          {canApply && (
            <Button
              size="lg"
              onClick={() => navigate(applyPath)}
              rightIcon={
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                  arrow_forward
                </span>
              }
            >
              Apply now
            </Button>
          )}
        </div>

        <div className="record-rule" />

        <dl className="record-facts">
          <DetailRow label="ROLE" value={job.role} />
          <DetailRow label="COMPANY" value={job.companyName} />
          <DetailRow label="LOCATION" value={job.location} />
          <DetailRow label="STATUS" value={humanise(job.status)} />
        </dl>
      </header>

      <div className="grid items-start gap-8 lg:grid-cols-12">
        <Card className="lg:col-span-8" padding="lg">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-c-border pb-4">
            <h2 className="font-headline-md text-headline-md text-on-surface">About this role</h2>
            <span className="font-mono text-[10px] tracking-[0.12em] text-c-text-subtle">
              RECORD / POSTING
            </span>
          </div>

          <Alert tone="info">
            A job description, salary, skills, and employment details are not
            available from the job service in this release. Everything the
            posting stores is shown below.
          </Alert>

          <dl className="mt-7 grid gap-5 md:grid-cols-2">
            <DetailRow label="Role title" value={job.role} />
            <DetailRow label="Company" value={job.companyName} />
            <DetailRow label="Location" value={job.location} />
            <DetailRow label="Status" value={job.status} />
          </dl>

          <div className="mt-8 flex flex-wrap items-center gap-3 border-t border-c-border pt-5">
            <span className="font-caption text-caption text-c-text-subtle">Requisition ID</span>
            <CopyableId value={job.jobId} />
          </div>
        </Card>

        <aside className="space-y-5 lg:col-span-4">
          <Card padding="lg">
            <p className="record-kicker">Apply</p>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mt-2">
              Apply for this role
            </h2>

            <div
              className={`mt-5 flex items-start gap-3 rounded-lg border p-4 ${
                isOpen
                  ? 'border-success-200 bg-success-50'
                  : 'border-c-border bg-c-surface-sunken'
              }`}
            >
              <span
                className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${
                  isOpen ? 'bg-success-600' : 'bg-c-text-subtle'
                }`}
                aria-hidden="true"
              />
              <div>
                <p className="font-label-md text-label-md text-on-surface">
                  {isOpen
                    ? 'Open for applications'
                    : job.status === 'CLOSED'
                      ? 'This posting is closed'
                      : 'Not yet published'}
                </p>
                <p className="mt-1 font-caption text-caption text-c-text-muted">
                  Status: {humanise(job.status)}
                </p>
              </div>
            </div>

            <div className="mt-6 space-y-3">
              {canApply && (
                <Button fullWidth size="lg" onClick={() => navigate(applyPath)}>
                  Apply now
                </Button>
              )}

              {!isAuthLoading && !user && isOpen && (
                <Link to={signInPath} className="btn btn-primary btn-lg w-full">
                  Sign in to apply
                </Link>
              )}

              {!isAuthLoading && isOwnerRecruiter && (
                <Link
                  to={`/recruiter/jobs/${job.jobId}`}
                  className="btn btn-secondary btn-lg w-full"
                >
                  Manage posting
                </Link>
              )}

              {!isAuthLoading && user && !isCandidate && !isOwnerRecruiter && (
                <p className="text-center font-body-md text-body-md text-c-text-muted">
                  Applications are submitted by candidates.
                </p>
              )}

              {!isAuthLoading && isCandidate && !isOpen && (
                <Alert tone="info" className="text-center">
                  This posting is not accepting applications.
                </Alert>
              )}
            </div>
          </Card>

          <div className="rounded-xl border border-c-border bg-c-surface-sunken p-5">
            <p className="font-label-md text-label-md text-on-surface">Candidate control</p>
            <p className="mt-2 font-caption text-caption leading-relaxed text-c-text-muted">
              Your application is authorized by the backend using your signed-in
              identity. You can withdraw it from the application record while the
              role is still open.
            </p>
          </div>
        </aside>
      </div>

      <p className="flex items-center gap-1.5 font-caption text-caption text-c-text-subtle">
        <span className="material-symbols-outlined text-[15px]" aria-hidden="true">
          lock
        </span>
        Your application is authorized by the backend using your signed-in identity.
      </p>
    </div>
  );
}
