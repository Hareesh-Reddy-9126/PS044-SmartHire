import { useState, useEffect, useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { jobsApi, JobResponse } from '../../api/client';
import { useApi } from '../../hooks/useApi';
import { PublicLayout } from '../../components/layout/Layout';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Card } from '../../components/ui/Card';

export function JobBrowsePage() {
  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [location, setLocation] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [debouncedLocation, setDebouncedLocation] = useState('');

  const { execute } = useApi();

  // Debounce search inputs
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
      setDebouncedLocation(location);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search, location]);

  const fetchJobs = useCallback(async () => {
    const result = await execute(
      () => jobsApi.list({ q: debouncedSearch, location: debouncedLocation, page, size }),
      { errorMessage: 'Failed to load jobs' }
    );
    if (result) {
      setJobs(result.content);
      setTotalElements(result.page.totalElements);
      setTotalPages(result.page.totalPages);
    }
    setIsLoading(false);
  }, [execute, debouncedSearch, debouncedLocation, page, size]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const filteredJobs = useMemo(() => {
    if (!debouncedSearch && !debouncedLocation) return jobs;
    return jobs.filter(job => {
      const searchMatch = !debouncedSearch || 
        job.companyName.toLowerCase().includes(debouncedSearch.toLowerCase()) ||
        job.role.toLowerCase().includes(debouncedSearch.toLowerCase());
      const locMatch = !debouncedLocation ||
        job.location.toLowerCase().includes(debouncedLocation.toLowerCase());
      return searchMatch && locMatch;
    });
  }, [jobs, debouncedSearch, debouncedLocation]);

  const clearFilters = () => {
    setSearch('');
    setLocation('');
  };

  const hasFilters = !!debouncedSearch || !!debouncedLocation;

  return (
    <PublicLayout>
      {/* Page Header */}
      <div className="w-full flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-c-border mb-6">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-c-primary-50 text-c-primary-700 font-overline text-overline uppercase tracking-wider font-semibold">
              <span className="w-1.5 h-1.5 rounded-full bg-primary" />
              Candidate Directory
            </span>
            <span className="font-code-sm text-code-sm text-c-text-subtle">AUDIT-ROLES-V2.4</span>
          </div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Open roles</h1>
          <p className="font-body-md text-body-md text-c-text-muted mt-0.5">
            {totalElements} postings available across verified organizations
          </p>
        </div>
        <div className="flex items-center gap-3 self-start md:self-auto bg-c-surface-sunken px-4 py-2 rounded border border-c-border">
          <span className="material-symbols-outlined text-c-accent-700 text-[18px]">verified_user</span>
          <div className="flex flex-col">
            <span className="font-label-md text-label-md text-on-surface">RFC 9457 Verified Directory</span>
            <span className="font-caption text-caption text-c-text-subtle">Strict server-state synchronization</span>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-c-surface border-b border-c-border py-4 px-0 mb-6">
        <div className="flex flex-col lg:flex-row items-stretch lg:items-center gap-4">
          <div className="flex-1 relative">
            <label htmlFor="search-input" className="block font-label-md text-label-md text-on-surface mb-1.5">
              Role or Organization
            </label>
            <div className="relative flex items-center">
              <span className="material-symbols-outlined absolute left-3 text-c-text-subtle text-[20px] pointer-events-none">search</span>
              <Input
                id="search-input"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search company or role..."
                maxLength={200}
              />
              <span className="absolute right-3 font-caption text-caption text-c-text-subtle bg-c-surface-sunken px-1.5 py-0.5 rounded border border-c-border select-none">
                300ms debounced
              </span>
            </div>
          </div>
          <div className="w-full lg:w-80 relative">
            <label htmlFor="location-input" className="block font-label-md text-label-md text-on-surface mb-1.5">
              Location Search
            </label>
            <div className="relative flex items-center">
              <span className="material-symbols-outlined absolute left-3 text-c-text-subtle text-[19px] pointer-events-none">location_on</span>
              <Input
                id="location-input"
                value={location}
                onChange={e => setLocation(e.target.value)}
                placeholder="Filter by location (e.g. London, Remote)..."
              />
            </div>
          </div>
          <div className="flex items-end gap-2 pt-2 lg:pt-0">
            <Button variant="secondary" size="sm" onClick={clearFilters} disabled={!hasFilters}>
              <span className="material-symbols-outlined text-[18px]">filter_alt_off</span>
              <span>Clear filters</span>
            </Button>
          </div>
        </div>

        {/* Active Metadata & Scope Bar */}
        <div className="flex flex-wrap items-center justify-between gap-3 mt-4 pt-3 border-t border-c-border font-caption text-caption">
          <div className="flex items-center gap-3">
            <span className="font-body-strong text-body-strong text-on-surface">
              {hasFilters 
                ? `Showing 1–${filteredJobs.length} of ${totalElements} open roles`
                : `Showing 1–${Math.min(size, totalElements)} of ${totalElements} open roles`
              }
            </span>
            <span className="text-c-border-strong">|</span>
            <span className="text-c-text-subtle flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px]">lock</span>
              Public candidate view: Status locked to <strong className="font-medium text-on-surface">OPEN</strong>
            </span>
          </div>
          <div className="flex items-center gap-1 text-c-text-subtle">
            <span className="material-symbols-outlined text-[15px]">sort</span>
            <span>Sorted by requisition ID (fixed system order)</span>
          </div>
        </div>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="bg-c-surface border border-c-border rounded p-6 flex flex-col justify-between animate-pulse">
              <div className="space-y-3">
                <div className="h-5 bg-c-surface-sunken rounded w-1/4" />
                <div className="h-6 bg-c-surface-sunken rounded w-3/4" />
                <div className="h-4 bg-c-surface-sunken rounded w-1/2" />
                <div className="h-4 bg-c-surface-sunken rounded w-1/3" />
              </div>
              <div className="pt-4 border-t border-c-border h-8 bg-c-surface-sunken rounded" />
            </div>
          ))}
        </div>
      )}

      {/* Job Grid */}
      {!isLoading && filteredJobs.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8" id="jobs-grid">
          {filteredJobs.map(job => (
            <Card key={job.jobId} className="flex flex-col justify-between shadow-resting hover:border-c-border-strong transition-all">
              <div>
                <div className="flex items-start justify-between gap-2 mb-2">
                  <StatusBadge status="OPEN" />
                  <button
                    className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-c-surface-sunken text-c-text-muted hover:text-on-surface hover:bg-c-border font-code-sm text-code-sm border border-c-border transition-colors group cursor-pointer"
                    onClick={() => navigator.clipboard.writeText(job.jobId)}
                    title="Click to copy requisition ID"
                  >
                    <span>{job.jobId}</span>
                    <span className="material-symbols-outlined text-[13px] opacity-60 group-hover:opacity-100">content_copy</span>
                  </button>
                </div>
                <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight mb-2">{job.role}</h2>
                <div className="flex flex-col gap-1 text-body-md text-c-text-muted mb-4">
                  <div className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-c-text-subtle text-[18px]">domain</span>
                    <span className="font-body-strong text-body-strong text-on-surface">{job.companyName}</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-c-text-subtle">
                    <span className="material-symbols-outlined text-[17px]">place</span>
                    <span>{job.location}</span>
                  </div>
                </div>
              </div>
              <div className="pt-4 border-t border-c-border flex items-center justify-between">
                <span className="font-caption text-caption text-c-text-subtle">ID Ref: #{job.jobId.slice(-4).toUpperCase()}</span>
                <Link
                  to={`/jobs/${job.jobId}`}
                  className="inline-flex items-center gap-1 text-primary hover:text-c-primary-700 font-label-md text-label-md group transition-colors"
                >
                  <span>View role & apply</span>
                  <span className="material-symbols-outlined text-[16px] transition-transform group-hover:translate-x-0.5">arrow_forward</span>
                </Link>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && filteredJobs.length === 0 && (
        <div className="w-full bg-c-surface border border-dashed border-c-border-strong rounded p-12 text-center mb-8" id="empty-state-banner">
          <div className="w-12 h-12 rounded-full bg-c-surface-sunken flex items-center justify-center mx-auto mb-4 text-c-text-subtle">
            <span className="material-symbols-outlined text-[24px]">search_off</span>
          </div>
          <h3 className="font-headline-sm text-headline-sm text-on-surface mb-2">
            {hasFilters ? 'No matching verified postings' : 'No open roles right now'}
          </h3>
          <p className="font-body-md text-body-md text-c-text-muted max-w-md mx-auto mb-6">
            {hasFilters
              ? 'No open roles matched your search parameters across our verified directories. Try removing filters or searching by partial title.'
              : 'Check back soon for new opportunities.'}
          </p>
          {hasFilters && (
            <Button onClick={clearFilters} className="inline-flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">restart_alt</span>
              <span>Reset all filters</span>
            </Button>
          )}
        </div>
      )}

      {/* Pagination */}
      {!isLoading && totalPages > 1 && (
        <div className="w-full bg-c-surface border border-c-border rounded p-3 flex flex-col md:flex-row items-center justify-between gap-3 shadow-resting">
          <div className="flex items-center gap-2 order-2 md:order-1 font-body-md text-body-md text-on-surface-variant">
            <label htmlFor="rows-per-page" className="font-label-md text-label-md text-c-text-muted">
              Rows per page:
            </label>
            <select
              id="rows-per-page"
              value={size}
              onChange={e => { setSize(Number(e.target.value)); setPage(0); }}
              className="h-8 pl-2 pr-6 bg-c-surface-sunken border border-c-border rounded font-code-sm text-code-sm text-on-surface focus:outline-none focus:border-primary"
            >
              <option value="10">10</option>
              <option value="20">20</option>
              <option value="50">50</option>
              <option value="100">100 (Max limit)</option>
            </select>
            <span className="font-caption text-caption text-c-text-subtle hidden sm:inline">(Server clamped: 100 max)</span>
          </div>
          <div className="flex items-center gap-1 order-1 md:order-2">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage(0)}
              aria-label="First Page"
            >
              « First
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
              aria-label="Previous Page"
            >
              ‹ Prev
            </Button>
            <span className="px-3 font-body-strong text-body-strong text-on-surface select-none">
              Page <span className="font-bold text-primary">{page + 1}</span> of {totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={page === totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              aria-label="Next Page"
            >
              Next ›
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={page === totalPages - 1}
              onClick={() => setPage(totalPages - 1)}
              aria-label="Last Page"
            >
              Last »
            </Button>
          </div>
        </div>
      )}
    </PublicLayout>
  );
}