import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { jobsApi } from '../../api/client';
import type { JobResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { Input } from '../../components/ui/Input';
import { Pagination } from '../../components/ui/Pagination';
import { PageHeader } from '../../components/ui/PageHeader';
import { Select } from '../../components/ui/Select';
import { SkeletonCards } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';
import { humanise } from '../../utils/format';

const PAGE_SIZE = 10;
const DEBOUNCE_MS = 300;

const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Open roles' },
  { value: 'DRAFT', label: 'Drafts' },
  { value: 'CLOSED', label: 'Closed roles' },
];

/**
 * The recruiter's job postings.
 *
 * Filter state lives in the URL so a filtered view survives a reload and can be
 * shared. Text inputs are debounced but only navigate when their value actually
 * differs from the URL, which keeps the effect from re-triggering itself.
 */
export function RecruiterJobsPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const query = searchParams.get('q') ?? '';
  const location = searchParams.get('location') ?? '';
  const status = searchParams.get('status') ?? '';
  const page = Number(searchParams.get('page')) || 0;

  const [queryInput, setQueryInput] = useState(query);
  const [locationInput, setLocationInput] = useState(location);

  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Keep the inputs in step with external URL changes (Clear, back/forward).
  useEffect(() => setQueryInput(query), [query]);
  useEffect(() => setLocationInput(location), [location]);

  const updateParams = useCallback(
    (patch: Record<string, string>) => {
      const next = new URLSearchParams(searchParams);
      Object.entries(patch).forEach(([key, value]) => {
        if (value) next.set(key, value);
        else next.delete(key);
      });
      if (!('page' in patch)) next.delete('page');
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams]
  );

  useEffect(() => {
    if (queryInput === query && locationInput === location) return;

    const timer = window.setTimeout(() => {
      updateParams({ q: queryInput, location: locationInput });
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [queryInput, locationInput, query, location, updateParams]);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await jobsApi.list({
        q: query,
        location,
        status,
        mine: true,
        page,
        size: PAGE_SIZE,
      });
      setJobs(result.content ?? []);
      setTotalElements(result.page?.totalElements ?? 0);
      setTotalPages(result.page?.totalPages ?? 0);
    } catch (caught) {
      setJobs([]);
      setError(errorMessage(caught, "We couldn't load your jobs."));
    } finally {
      setIsLoading(false);
    }
  }, [query, location, status, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const hasFilters = Boolean(query || location || status);

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruiter workspace"
        title="My jobs"
        description={
          isLoading
            ? 'Loading postings…'
            : `${totalElements} job${totalElements === 1 ? '' : 's'} in your organization`
        }
        action={
          <Link to="/recruiter/jobs/new" className="btn btn-primary btn-md">
            Create job
          </Link>
        }
      />

      {error && (
        <Alert
          tone="danger"
          title="Jobs unavailable"
          action={<Button size="sm" variant="secondary" onClick={() => void load()}>Try again</Button>}
        >
          {error}
        </Alert>
      )}

      <section className="card p-6 sm:p-8" aria-label="Filter job postings">
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_220px] lg:items-end">
          <Input
            label="Role or company"
            placeholder="Search by role or company"
            maxLength={200}
            value={queryInput}
            onChange={event => setQueryInput(event.target.value)}
            leftIcon={
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                search
              </span>
            }
          />

          <Input
            label="Location"
            placeholder="e.g. London, Remote"
            maxLength={200}
            value={locationInput}
            onChange={event => setLocationInput(event.target.value)}
            leftIcon={
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                location_on
              </span>
            }
          />

          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <Select
              label="Status"
              value={status}
              onChange={event => updateParams({ status: event.target.value })}
              options={STATUS_OPTIONS}
              placeholder="All statuses"
              className="w-full"
            />
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                setQueryInput('');
                setLocationInput('');
                setSearchParams(new URLSearchParams(), { replace: true });
              }}
              disabled={!hasFilters}
              className="w-full sm:w-auto"
            >
              Clear
            </Button>
          </div>
        </div>
      </section>

      {isLoading ? (
        <SkeletonCards count={6} label="Loading job postings" />
      ) : jobs.length === 0 ? (
        <EmptyState
          icon="work_off"
          title={hasFilters ? 'No matching jobs' : 'No jobs yet'}
          message={
            hasFilters
              ? 'No posting matches these filters. Try clearing them.'
              : 'Create your first job posting to start receiving applications.'
          }
          action={
            hasFilters ? (
              <Button
                variant="secondary"
                onClick={() => {
                  setQueryInput('');
                  setLocationInput('');
                  setSearchParams(new URLSearchParams(), { replace: true });
                }}
              >
                Clear filters
              </Button>
            ) : (
              <Link to="/recruiter/jobs/new" className="btn btn-primary btn-md">
                Create job
              </Link>
            )
          }
        />
      ) : (
        <div className="space-y-4">
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {jobs.map(job => (
              <li key={job.jobId}>
                <Link
                  to={`/recruiter/jobs/${job.jobId}`}
                  className="card card-hover flex h-full flex-col justify-between p-6 group"
                >
                  <div className="min-w-0">
                    <div className="mb-3 flex items-start justify-between gap-2">
                      <StatusBadge status={job.status} />
                      <span className="font-code-sm text-code-sm text-c-text-subtle tabular">
                        #{job.jobId.slice(0, 8)}
                      </span>
                    </div>
                    <h3 className="mb-2 font-headline-sm text-headline-sm text-c-text break-words group-hover:text-c-primary-700 transition-colors">
                      {job.role}
                    </h3>
                    <p className="mb-3 font-body-strong text-body-strong text-on-surface break-words">
                      {job.companyName}
                    </p>
                    <div className="mb-4 flex items-center gap-2 text-c-text-muted">
                      <span className="material-symbols-outlined text-[18px] shrink-0" aria-hidden="true">
                        location_on
                      </span>
                      <span className="font-body-md text-body-md truncate">{job.location}</span>
                    </div>
                    <p className="mb-5 font-caption text-caption text-c-text-subtle">
                      {humanise(job.status)} role
                    </p>
                  </div>
                  <span className="mt-auto flex items-center gap-1.5 font-label-md text-label-md text-c-primary-600 group-hover:text-c-primary-800 transition-colors">
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
            onPageChange={newPage => updateParams({ page: String(newPage) })}
          />
        </div>
      )}
    </div>
  );
}
