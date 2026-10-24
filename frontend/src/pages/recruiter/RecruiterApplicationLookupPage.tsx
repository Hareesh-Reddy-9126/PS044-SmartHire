import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { applicationsApi, jobsApi, recruitmentsApi } from '../../api/client';
import type { ApplicationResponse, JobResponse, RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { Input } from '../../components/ui/Input';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../utils/errors';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Finds one application and starts its recruitment.
 *
 * A candidate's application id is the only handle the API exposes for a single
 * application, so this screen accepts one directly (and is deep-linkable). The
 * normal route into it is a job's "Applications" list, which fills the id in.
 */
export function RecruiterApplicationLookupPage() {
  const { applicationId: routeApplicationId } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();
  const { execute, isLoading: isMutating } = useApi();

  const [applicationId, setApplicationId] = useState(routeApplicationId ?? '');
  const [application, setApplication] = useState<ApplicationResponse | null>(null);
  const [job, setJob] = useState<JobResponse | null>(null);
  const [recruitment, setRecruitment] = useState<RecruitmentResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);

  const load = useCallback(async (id: string) => {
    setError(null);
    setLookupError(null);
    setIsLoading(true);

    try {
      const found = await applicationsApi.get(id);
      setApplication(found);

      const [jobResult, recruitmentResult] = await Promise.allSettled([
        jobsApi.get(found.jobId),
        recruitmentsApi.list(0, 100),
      ]);
      setJob(jobResult.status === 'fulfilled' ? jobResult.value : null);

      const existing =
        recruitmentResult.status === 'fulfilled'
          ? (recruitmentResult.value.content ?? []).find(
              item => item.applicationId === found.applicationId
            ) ?? null
          : null;
      setRecruitment(existing);
    } catch (caught) {
      setApplication(null);
      setJob(null);
      setRecruitment(null);
      setError(errorMessage(caught, "We couldn't find that application."));
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Deep link: load immediately when the route carries an id.
  useEffect(() => {
    if (routeApplicationId && UUID.test(routeApplicationId)) {
      void load(routeApplicationId);
    }
  }, [routeApplicationId, load]);

  const handleLookup = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const id = applicationId.trim();

    if (!UUID.test(id)) {
      setLookupError('Enter the application identifier exactly as it appears, e.g. 5aab7ae9-d1ad-4052-a1b4-74312769bb41.');
      setApplication(null);
      return;
    }

    void load(id);
  };

  const shortlist = async () => {
    if (!application) return;

    const created = await execute(() => recruitmentsApi.create(application.applicationId), {
      successMessage: 'Application shortlisted. The recruitment is ready to schedule.',
      errorMessage: "We couldn't shortlist this application.",
      onSuccess: data => setRecruitment(data),
    });

    if (created) {
      navigate(`/recruiter/recruitments/${created.recruitmentId}`);
    }
  };

  const canShortlist =
    application !== null &&
    recruitment === null &&
    ['APPLIED', 'UNDER_REVIEW'].includes(application.status);

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruitment"
        title="Application lookup"
        description="Open an application by its identifier, then shortlist it to start the interview process."
      />

      <Card padding="lg">
        <form onSubmit={handleLookup} className="space-y-4">
          <Input
            id="applicationId"
            label="Application ID"
            placeholder="5aab7ae9-d1ad-4052-a1b4-74312769bb41"
            value={applicationId}
            onChange={event => setApplicationId(event.target.value)}
            error={lookupError ?? undefined}
            required
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" isLoading={isLoading}>
              Look up application
            </Button>
            <Link to="/recruiter/jobs" className="btn btn-ghost btn-md">
              Find applications by job
            </Link>
          </div>
        </form>
      </Card>

      {error && (
        <Alert tone="danger" title="Application unavailable">
          {error}
        </Alert>
      )}

      {application && (
        <>
          <div className="grid items-start gap-8 lg:grid-cols-2">
            <Card padding="lg">
              <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
                Application
              </h2>
              <DefinitionList
                items={[
                  {
                    label: 'Status',
                    value: <StatusBadge status={application.status} />,
                  },
                  {
                    label: 'Application ID',
                    value: <CopyableId value={application.applicationId} />,
                  },
                  { label: 'Candidate ID', value: <CopyableId value={application.candidateId} /> },
                  {
                    label: 'Résumé',
                    value: (
                      <a
                        className="text-c-primary-700 underline break-all"
                        href={application.resumeLink}
                        target="_blank"
                        rel="noreferrer noopener"
                      >
                        Open résumé
                      </a>
                    ),
                  },
                ]}
              />
            </Card>

            <Card padding="lg">
              <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Job</h2>
              {job ? (
                <>
                  <DefinitionList
                    items={[
                      { label: 'Role', value: job.role },
                      { label: 'Company', value: job.companyName },
                      { label: 'Location', value: job.location },
                      { label: 'Status', value: <StatusBadge status={job.status} size="sm" /> },
                    ]}
                  />
                  <Link to={`/recruiter/jobs/${job.jobId}`} className="btn btn-secondary btn-sm mt-5">
                    Open the job
                  </Link>
                </>
              ) : isLoading ? (
                <p className="font-body-md text-body-md text-c-text-muted">
                  Loading job details…
                </p>
              ) : (
                <p className="font-body-md text-body-md text-c-text-muted">
                  The job for this application is no longer available.
                </p>
              )}
            </Card>
          </div>

          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
              Recruitment
            </h2>

            {isLoading ? (
              <p className="font-body-md text-body-md text-c-text-muted">
                Loading recruitment…
              </p>
            ) : recruitment ? (
              <>
                <div className="mb-4 flex flex-wrap gap-2">
                  <StatusBadge status={recruitment.interviewStatus} />
                  <StatusBadge status={recruitment.finalStatus} />
                </div>
                <Link
                  to={`/recruiter/recruitments/${recruitment.recruitmentId}`}
                  className="btn btn-primary btn-md"
                >
                  Open recruitment
                </Link>
              </>
            ) : canShortlist ? (
              <>
                <p className="mb-4 font-body-md text-body-md text-c-text-muted">
                  Shortlisting creates the recruitment and moves the application to SHORTLISTED.
                  You can schedule the interview on the next screen.
                </p>
                <Button onClick={() => void shortlist()} isLoading={isMutating}>
                  Shortlist application
                </Button>
              </>
            ) : (
              <p className="font-body-md text-body-md text-c-text-muted">
                This application is {application.status} and cannot be shortlisted.
              </p>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
