import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { applicationsApi, jobsApi, recruitmentsApi } from '../../api/client';
import type { ApplicationResponse, JobResponse, RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonList } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';

const PAGE_SIZE = 10;

/**
 * The candidate's own applications.
 *
 * `GET /api/v1/applications` is candidate-scoped and returns applications, not
 * recruitments: an application exists from the moment it is submitted, whereas a
 * recruitment only starts when a recruiter shortlists it. Both facts are shown by
 * joining the two lists on `applicationId`; neither call blocks the other.
 */
export function CandidateApplicationsPage() {
  const [applications, setApplications] = useState<ApplicationResponse[]>([]);
  const [jobs, setJobs] = useState<Record<string, JobResponse>>({});
  const [recruitments, setRecruitments] = useState<Record<string, RecruitmentResponse>>({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await applicationsApi.list(page, PAGE_SIZE);
      const items = result.content ?? [];

      // Job context and recruitment progress are enrichment. A failure to read
      // either must not hide the applications themselves.
      const [jobResults, recruitmentResult] = await Promise.all([
        Promise.allSettled(items.map(application => jobsApi.get(application.jobId))),
        recruitmentsApi.list(0, 100).catch(() => null),
      ]);

      const jobMap: Record<string, JobResponse> = {};
      jobResults.forEach(outcome => {
        if (outcome.status === 'fulfilled') {
          jobMap[outcome.value.jobId] = outcome.value;
        }
      });

      const recruitmentMap: Record<string, RecruitmentResponse> = {};
      (recruitmentResult?.content ?? []).forEach(recruitment => {
        recruitmentMap[recruitment.applicationId] = recruitment;
      });

      setApplications(items);
      setJobs(jobMap);
      setRecruitments(recruitmentMap);
      setTotalPages(result.page?.totalPages ?? 0);
      setTotalElements(result.page?.totalElements ?? items.length);
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load your applications."));
      setApplications([]);
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Candidate workspace"
        title="My applications"
        description="Every role you have applied for, with its current status."
        action={
          <Link to="/jobs" className="btn btn-primary btn-md">
            Browse jobs
          </Link>
        }
      />

      {error && (
        <Alert
          tone="danger"
          title="Applications unavailable"
          action={<Button size="sm" variant="secondary" onClick={() => void load()}>Try again</Button>}
        >
          {error}
        </Alert>
      )}

      {isLoading ? (
        <SkeletonList label="Loading your applications" />
      ) : error ? null : applications.length === 0 ? (
        <EmptyState
          icon="assignment"
          title="No applications yet"
          message="When you apply for a role it appears here, along with the recruiter's progress."
          action={
            <Link to="/jobs" className="btn btn-primary btn-md">
              Browse open roles
            </Link>
          }
        />
      ) : (
        <div className="space-y-4">
          {applications.map(application => {
            const job = jobs[application.jobId];
            const recruitment = recruitments[application.applicationId];

            return (
              <article
                key={application.applicationId}
                className="card p-5 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <h2 className="font-headline-sm text-headline-sm text-on-surface break-words">
                    {job?.role ?? 'Role no longer available'}
                  </h2>
                  <p className="font-body-sm text-body-sm text-c-text-muted">
                    {job ? `${job.companyName} · ${job.location}` : `Job ${application.jobId.slice(0, 8)}`}
                  </p>
                  <p className="font-caption text-caption text-c-text-subtle">
                    Application {application.applicationId.slice(0, 8)}
                    {recruitment
                      ? ` · Interview ${recruitment.interviewStatus.toLowerCase().replace('_', ' ')}`
                      : ' · Not shortlisted yet'}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-3 sm:justify-end">
                  <StatusBadge status={application.status} />
                  <Link
                    to={`/candidate/applications/${application.applicationId}`}
                    className="btn btn-secondary btn-sm"
                  >
                    View
                  </Link>
                  {recruitment && (
                    <Link
                      to={`/candidate/progress/${recruitment.recruitmentId}`}
                      className="btn btn-ghost btn-sm"
                    >
                      Progress
                    </Link>
                  )}
                </div>
              </article>
            );
          })}

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={PAGE_SIZE}
            onPageChange={setPage}
          />
        </div>
      )}
    </div>
  );
}
