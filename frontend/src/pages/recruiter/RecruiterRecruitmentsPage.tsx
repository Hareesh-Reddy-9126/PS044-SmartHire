import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { recruitmentsApi } from '../../api/client';
import type { RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonCards } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';
import { formatDay } from '../../utils/format';

const PAGE_SIZE = 10;

/**
 * Recruitments for the recruiter's organization.
 *
 * A recruitment has two independent lifecycle states (interview and final
 * decision), so both are shown rather than collapsing them into one badge —
 * `finalStatus` is PENDING from the start and would otherwise hide the
 * interview progress entirely.
 */
export function RecruiterRecruitmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(Number(searchParams.get('page')) || 0, 0);

  const [recruitments, setRecruitments] = useState<RecruitmentResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await recruitmentsApi.list(page, PAGE_SIZE);
      setRecruitments(result.content ?? []);
      setTotalElements(result.page?.totalElements ?? 0);
      setTotalPages(result.page?.totalPages ?? 0);
    } catch (caught) {
      setRecruitments([]);
      setError(errorMessage(caught, "We couldn't load recruitments."));
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  const goToPage = (next: number) => {
    const params = new URLSearchParams(searchParams);
    if (next > 0) params.set('page', String(next));
    else params.delete('page');
    setSearchParams(params, { replace: true });
  };

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruiter workspace"
        title="Recruitments"
        description={
          error
            ? 'The record could not be loaded.'
            : isLoading
              ? 'Loading recruitments…'
              : `${totalElements} recruitment${totalElements === 1 ? '' : 's'} in your organization`
        }
        action={
          <Link to="/recruiter/applications" className="btn btn-secondary btn-md">
            Find an application
          </Link>
        }
      />

      {error && (
        <Alert
          tone="danger"
          title="Recruitments unavailable"
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
        <SkeletonCards count={6} label="Loading recruitments" />
      ) : error ? null : recruitments.length === 0 ? (
        <EmptyState
          icon="work_off"
          title="No recruitments yet"
          message="Shortlist an application to start the interview and hiring workflow."
          action={
            <Link to="/recruiter/applications" className="btn btn-primary btn-md">
              Find an application
            </Link>
          }
        />
      ) : (
        <>
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {recruitments.map(rec => (
              <li key={rec.recruitmentId}>
                <Link
                  to={`/recruiter/recruitments/${rec.recruitmentId}`}
                  className="card card-hover group flex h-full flex-col justify-between p-6"
                >
                  <div className="min-w-0">
                    <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
                      <span className="flex flex-wrap gap-1.5">
                        <StatusBadge status={rec.interviewStatus} size="sm" />
                        <StatusBadge status={rec.finalStatus} size="sm" />
                      </span>
                      <span className="font-code-sm text-code-sm text-c-text-subtle tabular">
                        #{rec.recruitmentId.slice(0, 8)}
                      </span>
                    </div>

                    <h3 className="mb-2 break-words font-headline-sm text-headline-sm text-c-text transition-colors group-hover:text-c-primary-700">
                      Application {rec.applicationId.slice(0, 8)}
                    </h3>

                    <dl className="space-y-1.5 font-body-md text-body-md text-c-text-muted">
                      <div className="flex items-center gap-2">
                        <dt className="sr-only">Job</dt>
                        <span
                          className="material-symbols-outlined shrink-0 text-[18px]"
                          aria-hidden="true"
                        >
                          work
                        </span>
                        <dd className="truncate">Job {rec.jobId.slice(0, 8)}</dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="sr-only">Interview date</dt>
                        <span
                          className="material-symbols-outlined shrink-0 text-[18px]"
                          aria-hidden="true"
                        >
                          event
                        </span>
                        <dd className="truncate">
                          {rec.interviewDate ? formatDay(rec.interviewDate) : 'No interview scheduled'}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  <span className="mt-5 flex items-center gap-1.5 font-label-md text-label-md text-c-primary-600 transition-colors group-hover:text-c-primary-800">
                    Manage recruitment
                    <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                      arrow_forward
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={PAGE_SIZE}
            onPageChange={goToPage}
          />
        </>
      )}
    </div>
  );
}
