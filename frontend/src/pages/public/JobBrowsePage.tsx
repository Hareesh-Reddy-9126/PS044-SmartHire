import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { jobsApi } from '../../api/client';
import type { JobResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { Input } from '../../components/ui/Input';
import { Pagination } from '../../components/ui/Pagination';
import { Select } from '../../components/ui/Select';
import { SkeletonList } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/AuthContext';
import { errorMessage } from '../../utils/errors';

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50, 100];
const DEBOUNCE_MS = 300;

const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Open roles' },
  { value: 'DRAFT', label: 'Drafts' },
  { value: 'CLOSED', label: 'Closed roles' },
];

/**
 * Public job discovery.
 *
 * Filter state lives in the URL so a filtered result set is shareable and
 * survives a reload. Text inputs keep local state and are debounced into the
 * URL only when they differ from it — writing on every render would re-trigger
 * the effect that performs the write. A recruiter additionally sees their own
 * organization's DRAFT and CLOSED postings, so the status filter is offered
 * only to them.
 */
export function JobBrowsePage() {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const query = searchParams.get('q') ?? '';
  const location = searchParams.get('location') ?? '';
  const status = searchParams.get('status') ?? '';
  const page = Math.max(Number(searchParams.get('page')) || 0, 0);
  const requestedSize = Number(searchParams.get('size'));
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : DEFAULT_PAGE_SIZE;

  const isRecruiter = user?.roles?.includes('RECRUITER') ?? false;

  const [queryInput, setQueryInput] = useState(query);
  const [locationInput, setLocationInput] = useState(location);

  // Keep inputs in step with external URL changes (Clear, back/forward).
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

  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await jobsApi.list({ q: query, location, status, page, size });
      setJobs(result.content ?? []);
      setTotalElements(result.page?.totalElements ?? 0);
      setTotalPages(result.page?.totalPages ?? 0);
    } catch (caught) {
      setJobs([]);
      setTotalElements(0);
      setTotalPages(0);
      setError(errorMessage(caught, "We couldn't load job postings."));
    } finally {
      setIsLoading(false);
    }
  }, [query, location, status, page, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const clearFilters = () => {
    setQueryInput('');
    setLocationInput('');
    setSearchParams(new URLSearchParams(), { replace: true });
  };

  const hasFilters = Boolean(query || location || status);

  return (
    <div className="space-y-8">
      <header className="browse-head">
        <div>
          <p className="page-eyebrow">Open roles</p>
          <h1 className="browse-title">Find the work that fits.</h1>
          <p className="browse-meta">
            {error
              ? 'The record could not be loaded.'
              : isLoading
                ? 'Loading postings…'
                : `${totalElements} published role${totalElements === 1 ? '' : 's'} across the SmartHire record.`}
          </p>
        </div>

        {isRecruiter && (
          <Link to="/recruiter/jobs/new" className="btn btn-primary btn-md">
            Create posting
          </Link>
        )}
      </header>

      <section className="search-dock" aria-label="Filter job postings">
        <div
          className={`grid gap-4 sm:gap-5 lg:items-end ${
            isRecruiter
              ? 'lg:grid-cols-[1fr_1fr_13rem_auto]'
              : 'lg:grid-cols-[1fr_1fr_auto]'
          }`}
        >
          <Input
            id="filter-q"
            label="Role or company"
            placeholder="Search by role or company"
            value={queryInput}
            onChange={event => setQueryInput(event.target.value)}
            maxLength={200}
            leftIcon={
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                search
              </span>
            }
          />

          <Input
            id="filter-location"
            label="Location"
            placeholder="e.g. London, Remote"
            value={locationInput}
            onChange={event => setLocationInput(event.target.value)}
            maxLength={200}
            leftIcon={
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                location_on
              </span>
            }
          />

          {isRecruiter && (
            <Select
              id="filter-status"
              label="Status"
              value={status}
              onChange={event => updateParams({ status: event.target.value })}
              options={STATUS_OPTIONS}
              placeholder="All statuses"
              className="w-full"
            />
          )}

          <Button
            variant="secondary"
            size="md"
            onClick={clearFilters}
            disabled={!hasFilters && !queryInput && !locationInput}
            className="w-full shrink-0 lg:w-auto"
          >
            Clear filters
          </Button>
        </div>
      </section>

      {error && (
        <Alert
          tone="danger"
          title="Could not load postings"
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
        <SkeletonList rows={6} label="Loading job postings" />
      ) : error ? null : jobs.length === 0 ? (
        <EmptyState
          icon="work_off"
          title={hasFilters ? 'No roles match these filters' : 'No open roles right now'}
          message={
            hasFilters
              ? 'Try a different search term, or clear the filters to see everything.'
              : 'Check back soon — new postings appear here as soon as they are published.'
          }
          action={
            hasFilters ? (
              <Button variant="secondary" size="sm" onClick={clearFilters}>
                Clear filters
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <ul className="job-records" aria-label="Job postings">
            {jobs.map((job, index) => (
              <li key={job.jobId}>
                <Link to={`/jobs/${job.jobId}`} className="job-record group">
                  <span className="job-record-index tabular">
                    {String(page * size + index + 1).padStart(2, '0')}
                  </span>

                  <div className="min-w-0">
                    <h2 className="job-record-role break-words">{job.role}</h2>
                    <p className="job-record-company truncate">{job.companyName}</p>
                  </div>

                  <span className="job-record-location">
                    <span className="material-symbols-outlined text-[15px]" aria-hidden="true">
                      location_on
                    </span>
                    <span className="truncate">{job.location}</span>
                  </span>

                  <StatusBadge status={job.status} />

                  <span className="job-record-arrow" aria-hidden="true">
                    <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={size}
            sizeOptions={PAGE_SIZES}
            onPageChange={next => {
              updateParams({ page: next === 0 ? '' : String(next), size: String(size) });
              window.scrollTo({ top: 0, behavior: 'smooth' });
            }}
            onSizeChange={nextSize => {
              updateParams({ size: String(nextSize), page: '' });
              window.scrollTo({ top: 0, behavior: 'smooth' });
            }}
          />
        </>
      )}
    </div>
  );
}
