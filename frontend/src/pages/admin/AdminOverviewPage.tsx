import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { recruitmentsApi } from '../../api/client';
import type { RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonCards } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';

/** How many recent recruitments the breakdown is computed over. */
const SAMPLE_SIZE = 50;

/**
 * Administration overview.
 *
 * The recruitment service exposes paged reads but no aggregate-statistics
 * endpoint, so the only honest total is the page envelope's `totalElements`.
 * The hired/pending breakdown is therefore computed over the most recent page
 * and labelled as such rather than being presented as a platform-wide figure.
 */
export function AdminOverviewPage() {
  const [recruitments, setRecruitments] = useState<RecruitmentResponse[]>([]);
  const [totalRecruitments, setTotalRecruitments] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await recruitmentsApi.list(0, SAMPLE_SIZE);
      setRecruitments(result.content ?? []);
      setTotalRecruitments(result.page?.totalElements ?? 0);
    } catch (caught) {
      setRecruitments([]);
      setError(errorMessage(caught, "We couldn't load the platform overview."));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const hiredCount = recruitments.filter(r => r.finalStatus === 'HIRED').length;
  const pendingCount = recruitments.filter(r => r.finalStatus === 'PENDING').length;
  const sampleLabel =
    totalRecruitments > recruitments.length
      ? `of the ${recruitments.length} most recent`
      : 'across all recruitments';

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Administration"
        title="Platform overview"
        description="Operational read of interview and hiring activity."
      />

      {error && (
        <Alert
          tone="danger"
          title="Overview unavailable"
          action={
            <Button size="sm" variant="secondary" onClick={() => void load()}>
              Try again
            </Button>
          }
        >
          {error}
        </Alert>
      )}

      {isLoading ? (
        <SkeletonCards count={3} label="Loading overview" />
      ) : (
        <>
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <div className="metric-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="record-kicker">Record / 01</p>
                  <p className="metric-value mt-3 text-c-primary-800">{totalRecruitments}</p>
                  <p className="metric-label">Total recruitments</p>
                </div>
                <span
                  className="material-symbols-outlined text-[38px] text-c-primary-100"
                  aria-hidden="true"
                >
                  work
                </span>
              </div>
            </div>

            <div className="metric-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="record-kicker">Record / 02</p>
                  <p className="metric-value mt-3 text-success-700">{hiredCount}</p>
                  <p className="metric-label">
                    Hired <span className="text-c-text-subtle">{sampleLabel}</span>
                  </p>
                </div>
                <span
                  className="material-symbols-outlined text-[38px] text-success-100"
                  aria-hidden="true"
                >
                  verified_user
                </span>
              </div>
            </div>

            <div className="metric-card">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="record-kicker">Record / 03</p>
                  <p className="metric-value mt-3 text-c-accent-700">{pendingCount}</p>
                  <p className="metric-label">
                    Pending <span className="text-c-text-subtle">{sampleLabel}</span>
                  </p>
                </div>
                <span
                  className="material-symbols-outlined text-[38px] text-c-accent-100"
                  aria-hidden="true"
                >
                  pending
                </span>
              </div>
            </div>
          </div>

          <Card padding="none">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-c-border px-6 py-5">
              <div>
                <p className="record-kicker">Movement</p>
                <h2 className="mt-1 font-headline-lg text-headline-lg text-on-surface">
                  Recent activity
                </h2>
              </div>
              <Link to="/admin/recruitments" className="btn btn-ghost btn-sm">
                View all recruitments
              </Link>
            </div>

            <div className="p-4 sm:p-6">
              {recruitments.length === 0 ? (
                <EmptyState
                  icon="inbox"
                  title="No recruitment activity yet"
                  message="Recruitments appear here as soon as a recruiter shortlists an application."
                />
              ) : (
                <ul className="divide-y divide-c-border border-y border-c-border">
                  {recruitments.map((rec, index) => (
                    <li key={rec.recruitmentId}>
                      <Link
                        to={`/admin/recruitments/${rec.recruitmentId}`}
                        className="group flex flex-wrap items-center gap-4 py-4 transition-colors duration-normal hover:bg-c-surface-sunken/50"
                      >
                        <span className="w-10 shrink-0 font-mono text-[10px] text-c-accent-700 tabular">
                          {String(index + 1).padStart(2, '0')}
                        </span>
                        <span className="w-24 shrink-0 font-code-sm text-code-sm text-c-text-muted tabular">
                          #{rec.recruitmentId.slice(0, 8)}
                        </span>
                        <span className="min-w-0 flex-1 truncate font-body-md text-body-md text-on-surface">
                          Application {rec.applicationId.slice(0, 8)}
                          <span className="mx-2 text-c-border-strong">/</span>
                          Job {rec.jobId.slice(0, 8)}
                        </span>
                        <span className="flex flex-wrap gap-2">
                          <StatusBadge status={rec.interviewStatus} size="sm" />
                          <StatusBadge status={rec.finalStatus} size="sm" />
                        </span>
                        <span
                          className="material-symbols-outlined text-[18px] text-c-text-subtle transition-transform duration-fast group-hover:translate-x-0.5 group-hover:text-c-accent-700"
                          aria-hidden="true"
                        >
                          arrow_forward
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </Card>
        </>
      )}
    </div>
  );
}
