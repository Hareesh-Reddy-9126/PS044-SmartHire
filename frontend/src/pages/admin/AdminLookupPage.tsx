import { useCallback, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { apiErrorStatus, applicationsApi, jobsApi } from '../../api/client';
import type { ApplicationResponse, JobResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { Input } from '../../components/ui/Input';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';

/**
 * Administrative lookup by application identifier.
 *
 * The application contract exposes identifiers and a résumé link, not candidate
 * names or e-mail addresses — those live in auth-service, which has no admin
 * directory endpoint. The screen therefore shows only what the contract can
 * actually return and links to the job for context.
 */
export function AdminLookupPage() {
  const [applicationId, setApplicationId] = useState('');
  const [application, setApplication] = useState<ApplicationResponse | null>(null);
  const [job, setJob] = useState<JobResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const id = applicationId.trim();

      setError(null);
      setApplication(null);
      setJob(null);

      if (!id) {
        setError('Enter an application ID to look up.');
        return;
      }

      setIsLoading(true);
      try {
        const found = await applicationsApi.get(id);
        setApplication(found);

        const jobResult = await Promise.allSettled([jobsApi.get(found.jobId)]);
        setJob(jobResult[0].status === 'fulfilled' ? jobResult[0].value : null);
      } catch (caught) {
        const status = apiErrorStatus(caught);
        setError(
          status === 404
            ? 'No application exists with that ID.'
            : status === 400
              ? 'That is not a valid application ID.'
              : errorMessage(caught, "We couldn't look up that application.")
        );
      } finally {
        setIsLoading(false);
      }
    },
    [applicationId]
  );

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Administration"
        title="Application lookup"
        description="Find an application and the job it was submitted to."
      />

      <Card padding="lg">
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <Input
            id="applicationId"
            label="Application ID"
            type="text"
            placeholder="e.g. 5aab7ae9-d1ad-4052-a1b4-74312769bb41"
            value={applicationId}
            onChange={event => setApplicationId(event.target.value)}
            autoComplete="off"
            spellCheck={false}
            required
          />
          <Button type="submit" isLoading={isLoading} disabled={isLoading}>
            Look up application
          </Button>
        </form>
      </Card>

      {error && (
        <Alert tone="danger" title="Lookup failed">
          {error}
        </Alert>
      )}

      {application && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Application</h2>
            <DefinitionList
              items={[
                { label: 'Application ID', value: <CopyableId value={application.applicationId} /> },
                { label: 'Status', value: <StatusBadge status={application.status} size="sm" /> },
                { label: 'Candidate ID', value: <CopyableId value={application.candidateId} /> },
                { label: 'Job ID', value: <CopyableId value={application.jobId} /> },
              ]}
            />

            <a
              className="btn btn-secondary btn-sm mt-5"
              href={application.resumeLink}
              target="_blank"
              rel="noreferrer noopener"
            >
              Open résumé
            </a>
          </Card>

          <Card padding="lg" className="lg:col-span-2">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Job</h2>

            {job ? (
              <>
                <DefinitionList
                  items={[
                    { label: 'Role', value: job.role },
                    { label: 'Company', value: job.companyName },
                    { label: 'Location', value: job.location },
                    { label: 'Status', value: <StatusBadge status={job.status} size="sm" /> },
                    { label: 'Owner organization', value: <CopyableId value={job.ownerOrgId} /> },
                  ]}
                />
                <Link to={`/jobs/${job.jobId}`} className="btn btn-ghost btn-sm mt-5">
                  View public posting
                </Link>
              </>
            ) : (
              <p className="font-body-md text-body-md text-c-text-muted">
                The job record for this application could not be loaded. The IDs above still
                identify it.
              </p>
            )}

            <p className="mt-6 font-caption text-caption text-c-text-subtle">
              Candidate names and e-mail addresses are held by auth-service, which exposes no
              administrative directory endpoint, so they are not shown here.
            </p>
          </Card>
        </div>
      )}
    </div>
  );
}
