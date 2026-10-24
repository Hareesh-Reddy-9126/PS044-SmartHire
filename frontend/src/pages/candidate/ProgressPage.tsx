import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { jobsApi, recruitmentsApi } from '../../api/client';
import type { JobResponse, RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonList } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';
import { formatDay, formatTime } from '../../utils/format';

/**
 * Every recruitment process the candidate is part of.
 *
 * A recruitment only exists once a recruiter shortlists an application, so this
 * screen is the shortlist-and-onwards view; applications that are still APPLIED
 * or UNDER_REVIEW live on "My applications".
 */
export function ProgressPage() {
  const [recruitments, setRecruitments] = useState<RecruitmentResponse[]>([]);
  const [jobs, setJobs] = useState<Record<string, JobResponse>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await recruitmentsApi.list(0, 100);
      const items = result.content ?? [];
      setRecruitments(items);

      const jobResults = await Promise.allSettled(
        items.map(recruitment => jobsApi.get(recruitment.jobId))
      );
      const jobMap: Record<string, JobResponse> = {};
      jobResults.forEach(outcome => {
        if (outcome.status === 'fulfilled') {
          jobMap[outcome.value.jobId] = outcome.value;
        }
      });
      setJobs(jobMap);
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load your recruitment progress."));
      setRecruitments([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Candidate workspace"
        title="My progress"
        description="Recruitment processes a recruiter has started for you."
      />

      {error && (
        <Alert
          tone="danger"
          title="Progress unavailable"
          action={<Button size="sm" variant="secondary" onClick={() => void load()}>Try again</Button>}
        >
          {error}
        </Alert>
      )}

      {isLoading ? (
        <SkeletonList label="Loading your recruitment progress" />
      ) : recruitments.length === 0 ? (
        <EmptyState
          icon="timeline"
          title="No recruitment processes yet"
          message="When a recruiter shortlists one of your applications, the interview process appears here."
          action={
            <Link to="/candidate/applications" className="btn btn-secondary btn-md">
              View my applications
            </Link>
          }
        />
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {recruitments.map(recruitment => {
            const job = jobs[recruitment.jobId];

            return (
              <Link
                key={recruitment.recruitmentId}
                to={`/candidate/progress/${recruitment.recruitmentId}`}
                className="card p-6 transition-shadow hover:shadow-hover group block"
              >
                <div className="mb-3 flex items-start justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={recruitment.interviewStatus} size="sm" />
                    <StatusBadge status={recruitment.finalStatus} size="sm" />
                  </div>
                  <span className="font-code-sm text-code-sm text-c-text-subtle tabular">
                    #{recruitment.recruitmentId.slice(0, 8)}
                  </span>
                </div>

                <h3 className="mb-1 font-headline-sm text-headline-sm text-c-text break-words group-hover:text-c-primary-700 transition-colors">
                  {job?.role ?? `Job ${recruitment.jobId.slice(0, 8)}`}
                </h3>
                <p className="mb-4 font-body-sm text-body-sm text-c-text-muted">
                  {job ? `${job.companyName} · ${job.location}` : 'Job details unavailable'}
                </p>

                <div className="flex items-center gap-2 text-c-text-muted">
                  <span className="material-symbols-outlined text-[18px] shrink-0" aria-hidden="true">
                    event
                  </span>
                  <span className="font-body-md text-body-md truncate">
                    {recruitment.interviewDate
                      ? `${formatDay(recruitment.interviewDate)} at ${formatTime(recruitment.interviewStartTime)}`
                      : 'No interview scheduled'}
                  </span>
                </div>

                <span className="mt-4 flex items-center gap-1.5 font-label-md text-label-md text-c-primary-600 group-hover:text-c-primary-800 transition-colors">
                  View details
                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                    arrow_forward
                  </span>
                </span>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
