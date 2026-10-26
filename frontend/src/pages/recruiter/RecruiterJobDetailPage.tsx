import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { applicationsApi, jobsApi } from '../../api/client';
import type { ApplicationResponse, JobResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/AuthContext';
import { useApi } from '../../hooks/useApi';
import { errorMessage, isNotFound } from '../../utils/errors';

const PAGE_SIZE = 10;

/**
 * A single job posting from the recruiter's side.
 *
 * This is also the entry point into the application workflow: the job's
 * applications are listed here, and the only ownership fact needed to authorize
 * that list is the job's `ownerOrgId`, which the service checks.
 */
export function RecruiterJobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const { user } = useAuth();
  const { execute, isLoading: isMutating } = useApi();

  const [job, setJob] = useState<JobResponse | null>(null);
  const [applications, setApplications] = useState<ApplicationResponse[]>([]);
  const [applicationsError, setApplicationsError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadJob = useCallback(async () => {
    if (!jobId) {
      setIsLoading(false);
      setError('No job was specified.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      setJob(await jobsApi.get(jobId));
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load this job."));
    } finally {
      setIsLoading(false);
    }
  }, [jobId]);

  const loadApplications = useCallback(async () => {
    if (!jobId) return;

    setApplicationsError(null);
    try {
      const result = await applicationsApi.listByJob(jobId, page, PAGE_SIZE);
      setApplications(result.content ?? []);
      setTotalPages(result.page?.totalPages ?? 0);
      setTotalElements(result.page?.totalElements ?? 0);
    } catch (caught) {
      setApplications([]);
      // A 404 here means the job is outside the caller's organization: the job
      // itself loaded from the public catalogue, so it is not actually missing.
      setApplicationsError(
        isNotFound(caught)
          ? 'Applications are visible only to the organization that owns this job.'
          : errorMessage(caught, "We couldn't load the applications for this job.")
      );
    }
  }, [jobId, page]);

  useEffect(() => {
    void loadJob();
  }, [loadJob]);

  useEffect(() => {
    void loadApplications();
  }, [loadApplications]);

  const changeStatus = async (action: 'open' | 'close' | 'reopen') => {
    if (!job) return;

    const call =
      action === 'open'
        ? jobsApi.open(job.jobId)
        : action === 'close'
          ? jobsApi.close(job.jobId)
          : jobsApi.reopen(job.jobId);

    await execute(() => call, {
      successMessage:
        action === 'open' ? 'Job opened.' : action === 'close' ? 'Job closed.' : 'Job reopened.',
      errorMessage: "We couldn't change the job status.",
      onSuccess: data => setJob(data),
    });
  };

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl py-12">
        <SkeletonDetail />
      </div>
    );
  }

  if (error || !job) {
    return (
      <div className="mx-auto max-w-xl space-y-6 py-12 text-center">
        <Alert tone="danger" title="Job unavailable">
          {error ?? "We couldn't find this job."}
        </Alert>
        <div className="flex justify-center gap-3">
          <Button onClick={() => void loadJob()}>Try again</Button>
          <Link to="/recruiter/jobs" className="btn btn-secondary btn-md">
            Back to jobs
          </Link>
        </div>
      </div>
    );
  }

  const isOwner = user?.orgId ? user.orgId === job.ownerOrgId : false;

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruiter workspace"
        title={job.role}
        description={`${job.companyName} · ${job.location}`}
        action={<StatusBadge status={job.status} />}
      />

      <div className="grid items-start gap-8 lg:grid-cols-3">
        <Card padding="lg" className="lg:col-span-2">
          <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
            Job details
          </h2>
          <DefinitionList
            items={[
              { label: 'Company', value: job.companyName },
              { label: 'Role', value: job.role },
              { label: 'Location', value: job.location },
              { label: 'Status', value: <StatusBadge status={job.status} size="sm" /> },
              { label: 'Job ID', value: <CopyableId value={job.jobId} /> },
              {
                label: 'Organization',
                value: <CopyableId value={job.ownerOrgId} />,
              },
            ]}
          />
        </Card>

        <Card padding="lg">
          <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Actions</h2>

          {!isOwner ? (
            <p className="font-body-md text-body-md text-c-text-muted">
              Only the organization that owns this posting can change it.
            </p>
          ) : (
            <div className="space-y-3">
              {job.status === 'DRAFT' && (
                <Button fullWidth onClick={() => void changeStatus('open')} disabled={isMutating}>
                  Publish job
                </Button>
              )}
              {job.status === 'OPEN' && (
                <Button
                  variant="secondary"
                  fullWidth
                  onClick={() => void changeStatus('close')}
                  disabled={isMutating}
                >
                  Close job
                </Button>
              )}
              {job.status === 'CLOSED' && (
                <Button fullWidth onClick={() => void changeStatus('reopen')} disabled={isMutating}>
                  Reopen job
                </Button>
              )}

              <Link
                to={`/recruiter/jobs/${job.jobId}/edit`}
                className="btn btn-secondary btn-md w-full"
              >
                Edit job
              </Link>
            </div>
          )}
        </Card>
      </div>

      <Card padding="lg">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-headline-sm text-headline-sm text-on-surface">Applications</h2>
          <Link to="/recruiter/applications" className="btn btn-ghost btn-sm">
            Look up by ID
          </Link>
        </div>

        {applicationsError && (
          <Alert
            tone="danger"
            title="Applications unavailable"
            action={
              <Button size="sm" variant="secondary" onClick={() => void loadApplications()}>
                Try again
              </Button>
            }
          >
            {applicationsError}
          </Alert>
        )}

        {!applicationsError && applications.length === 0 ? (
          <EmptyState
            icon="inbox"
            title="No applications yet"
            message="Candidate applications for this job will appear here."
          />
        ) : (
          <div className="space-y-3">
            {applications.map(application => (
              <div
                key={application.applicationId}
                className="flex flex-col gap-3 rounded-lg border border-c-border bg-c-surface p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="font-body-strong text-body-strong text-on-surface">
                    Application {application.applicationId.slice(0, 8)}
                  </p>
                  <p className="font-caption text-caption text-c-text-subtle break-all">
                    Candidate {application.candidateId}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                  <StatusBadge status={application.status} />
                  <a
                    className="btn btn-ghost btn-sm"
                    href={application.resumeLink}
                    target="_blank"
                    rel="noreferrer noopener"
                  >
                    Résumé
                  </a>
                  <Link
                    to={`/recruiter/applications/${application.applicationId}`}
                    className="btn btn-secondary btn-sm"
                  >
                    Open
                  </Link>
                </div>
              </div>
            ))}

            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              size={PAGE_SIZE}
              onPageChange={setPage}
            />
          </div>
        )}
      </Card>
    </div>
  );
}
