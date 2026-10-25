import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { jobsApi, JobResponse } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { humanise } from '../../utils/format';
import { errorMessage } from '../../utils/errors';

const PAGE_SIZE = 10;

export function CompanyRecruitmentsPage() {
  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await jobsApi.list({ q: query, location: '', status: statusFilter, page, size: PAGE_SIZE });
      setJobs(result.content ?? []);
      setTotalElements(result.page?.totalElements ?? 0);
      setTotalPages(result.page?.totalPages ?? 0);
    } catch (err) {
      setError(errorMessage(err, 'Failed to load jobs.'));
    } finally {
      setIsLoading(false);
    }
  }, [query, statusFilter, page]);

  useEffect(() => {
    load();
  }, [load]);

  const filteredJobs = useMemo(() => {
    return jobs.filter((job) => {
      const matchesQuery = job.role.toLowerCase().includes(query.toLowerCase()) ||
        job.companyName.toLowerCase().includes(query.toLowerCase());
      const matchesStatus = !statusFilter || job.status === statusFilter;
      return matchesQuery && matchesStatus;
    });
  }, [jobs, query, statusFilter]);

  return (
    <div className="space-y-8">
      <PageHeader
        title="Company jobs"
        description="Manage your company's job postings."
        action={
          <Link
            to="/recruiter/jobs/new"
            className="btn btn-primary btn-lg"
          >
            Create job
          </Link>
        }
      />

      {error && <Alert tone="danger">{error}</Alert>}

      {isLoading ? (
        <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {[...Array(6)].map((_, i) => (
            <li key={i} className="card p-6 animate-pulse">
              <div className="space-y-4">
                <div className="skeleton h-4 w-1/3" />
                <div className="skeleton h-5 w-full" />
                <div className="skeleton h-3 w-1/2" />
                <div className="skeleton h-3 w-1/3" />
              </div>
            </li>
          ))}
        </ul>
      ) : filteredJobs.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
            work_off
          </span>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mt-4">
            {query || statusFilter ? 'No matching jobs' : 'No jobs yet'}
          </h2>
          <p className="font-body-md text-body-md text-c-text-muted mt-2 max-w-prose mx-auto">
            {query || statusFilter
              ? 'No jobs match these filters. Try clearing them.'
              : 'You haven\'t posted any jobs yet. Create your first job posting to start receiving applications.'}
          </p>
          {query || statusFilter ? (
            <Button variant="secondary" size="sm" onClick={() => { setQuery(''); setStatusFilter(''); }}>
              Clear filters
            </Button>
          ) : (
            <Link to="/recruiter/jobs/new" className="btn btn-primary btn-lg">
              Create your first job
            </Link>
          )}
        </div>
      ) : (
        <>
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {filteredJobs.map(job => (
              <li key={job.jobId}>
                <Link
                  to={`/recruiter/jobs/${job.jobId}`}
                  className="card card-hover flex h-full flex-col justify-between p-6 group"
                >
                  <div className="min-w-0">
                    <div className="flex items-start justify-between gap-2 mb-3">
                      <StatusBadge status={job.status} />
                      <span className="font-code-sm text-code-sm text-c-text-subtle tabular">
                        #{job.jobId.slice(0, 8)}
                      </span>
                    </div>

                    <h3 className="font-headline-sm text-headline-sm text-c-text mb-2 break-words group-hover:text-c-primary-700 transition-colors duration-normal">
                      {job.role}
                    </h3>

                    <p className="font-body-strong text-body-strong text-on-surface mb-3 break-words">
                      {job.companyName}
                    </p>

                    <div className="flex items-center gap-2 mb-4 text-c-text-muted">
                      <span className="material-symbols-outlined text-[18px] shrink-0" aria-hidden="true">
                        location_on
                      </span>
                      <span className="font-body-md text-body-md truncate">
                        {job.location}
                      </span>
                    </div>

                    <p className="font-caption text-caption text-c-text-subtle mb-5">
                      {humanise(job.status)} role ·
                      {job.status === 'OPEN' ? 'Accepting applications' : 'Not accepting applications'}
                    </p>
                  </div>

                  <span className="mt-auto flex items-center gap-1.5 font-label-md text-label-md text-c-primary-600 group-hover:text-c-primary-800 transition-colors duration-normal">
                    View details
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
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}