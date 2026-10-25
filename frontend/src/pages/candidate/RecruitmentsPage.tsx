import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { applicationsApi, ApplicationResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { humanise } from '../../utils/format';
import { errorMessage } from '../../utils/errors';

const PAGE_SIZE = 10;

export function RecruitmentsPage() {
  const [applications, setApplications] = useState<ApplicationResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query] = useState('');
  const [statusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await applicationsApi.list(page, PAGE_SIZE);
      setApplications(result.content ?? []);
      setTotalElements(result.page?.totalElements ?? 0);
      setTotalPages(result.page?.totalPages ?? 0);
    } catch (err) {
      setError(errorMessage(err, 'Failed to load applications.'));
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  const filteredApplications = useMemo(() => {
    return applications.filter((app) => {
      const matchesQuery = app.jobId.toLowerCase().includes(query.toLowerCase());
      const matchesStatus = !statusFilter || app.status === statusFilter;
      return matchesQuery && matchesStatus;
    });
  }, [applications, query, statusFilter]);

  return (
    <div className="space-y-8">
      <PageHeader
        title="Candidate Applications"
        description="Manage your applications across companies."
        action={
          <Link to="/candidate/applications/new" className="btn btn-primary btn-lg">
            Apply for job
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
      ) : filteredApplications.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
            work_off
          </span>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mt-4">
            No applications yet
          </h2>
          <p className="font-body-md text-body-md text-c-text-muted mt-2">
            You haven't applied to any jobs yet.
          </p>
        </div>
      ) : (
        <>
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {filteredApplications.map(app => (
              <li key={app.applicationId}>
                <Link
                  to={`/candidate/applications/${app.applicationId}`}
                  className="card card-hover flex h-full flex-col justify-between p-6 group"
                >
                  <div className="min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <StatusBadge status={app.status} />
                      <span className="font-code-sm text-code-sm text-c-text-subtle tabular">
                        #{app.jobId.slice(0, 8)}
                      </span>
                    </div>
                    <p className="font-body-md text-body-md text-c-text-muted">
                      {app.jobId}
                    </p>
                    <p className="font-caption text-caption text-c-text-subtle">
                      {app.applicationId} · {humanise(app.status)}
                    </p>
                  </div>
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