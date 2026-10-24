import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { applicationsApi, jobsApi, recruitmentsApi } from '../../api/client';
import type { ApplicationResponse, JobResponse, RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';
import { formatDay, formatTime } from '../../utils/format';

/**
 * Read-only recruitment view for administrators.
 *
 * ADMIN is deliberately not a recruitment actor (no interview or hiring writes),
 * so this screen exposes no actions — it is the operational read that backs the
 * admin overview and audit workflow.
 */
export function AdminRecruitmentDetailPage() {
  const { recruitmentId } = useParams<{ recruitmentId: string }>();

  const [recruitment, setRecruitment] = useState<RecruitmentResponse | null>(null);
  const [application, setApplication] = useState<ApplicationResponse | null>(null);
  const [job, setJob] = useState<JobResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!recruitmentId) {
      setIsLoading(false);
      setError('No recruitment was specified.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const found = await recruitmentsApi.get(recruitmentId);
      setRecruitment(found);

      const [applicationResult, jobResult] = await Promise.allSettled([
        applicationsApi.get(found.applicationId),
        jobsApi.get(found.jobId),
      ]);
      setApplication(applicationResult.status === 'fulfilled' ? applicationResult.value : null);
      setJob(jobResult.status === 'fulfilled' ? jobResult.value : null);
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load this recruitment."));
    } finally {
      setIsLoading(false);
    }
  }, [recruitmentId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl py-12">
        <SkeletonDetail />
      </div>
    );
  }

  if (error || !recruitment) {
    return (
      <div className="mx-auto max-w-xl space-y-6 py-12 text-center">
        <Alert tone="danger" title="Recruitment unavailable">
          {error ?? "We couldn't find this recruitment."}
        </Alert>
        <div className="flex justify-center gap-3">
          <Button onClick={() => void load()}>Try again</Button>
          <Link to="/admin/recruitments" className="btn btn-secondary btn-md">
            All recruitments
          </Link>
        </div>
      </div>
    );
  }

  const isInterviewScheduled = recruitment.interviewStatus !== 'NOT_SCHEDULED';

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Administration"
        title={job?.role ?? `Recruitment ${recruitment.recruitmentId.slice(0, 8)}`}
        description={job ? `${job.companyName} · ${job.location}` : undefined}
        action={
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={recruitment.interviewStatus} />
            <StatusBadge status={recruitment.finalStatus} />
          </div>
        }
      />

      <div className="grid items-start gap-8 lg:grid-cols-3">
        <Card padding="lg" className="lg:col-span-2">
          <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
            Interview
          </h2>

          {isInterviewScheduled ? (
            <DefinitionList
              items={[
                { label: 'Status', value: <StatusBadge status={recruitment.interviewStatus} size="sm" /> },
                { label: 'Date', value: formatDay(recruitment.interviewDate) },
                { label: 'Start time', value: formatTime(recruitment.interviewStartTime) },
                { label: 'Interviewer', value: recruitment.interviewer ?? 'Not recorded' },
                { label: 'Location', value: recruitment.interviewLocation ?? 'Not recorded' },
              ]}
            />
          ) : (
            <p className="font-body-md text-body-md text-c-text-muted">
              No interview has been scheduled for this recruitment yet.
            </p>
          )}

          <h2 className="mb-4 mt-8 font-headline-sm text-headline-sm text-on-surface">
            Final decision
          </h2>
          <DefinitionList
            columns={2}
            items={[
              { label: 'Final status', value: <StatusBadge status={recruitment.finalStatus} size="sm" /> },
              {
                label: 'Decision',
                value:
                  recruitment.finalStatus === 'PENDING'
                    ? 'Awaiting a recruiter decision.'
                    : `Recorded as ${recruitment.finalStatus}.`,
              },
            ]}
          />
          <p className="mt-6 font-caption text-caption text-c-text-subtle">
            Administrators have read-only access to recruitments; hiring decisions belong to the
            recruiting organization.
          </p>
        </Card>

        <div className="space-y-6">
          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
              Application
            </h2>
            <DefinitionList
              columns={1}
              items={[
                { label: 'Application ID', value: <CopyableId value={recruitment.applicationId} /> },
                {
                  label: 'Status',
                  value: application ? (
                    <StatusBadge status={application.status} size="sm" />
                  ) : (
                    'Unavailable'
                  ),
                },
                { label: 'Candidate ID', value: <CopyableId value={recruitment.candidateId} /> },
              ]}
            />

            {application && (
              <a
                className="btn btn-secondary btn-sm mt-5"
                href={application.resumeLink}
                target="_blank"
                rel="noreferrer noopener"
              >
                Open résumé
              </a>
            )}
          </Card>

          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Job</h2>
            <DefinitionList
              columns={1}
              items={[
                { label: 'Job ID', value: <CopyableId value={recruitment.jobId} /> },
                { label: 'Role', value: job?.role ?? 'Unavailable' },
                { label: 'Company', value: job?.companyName ?? 'Unavailable' },
                {
                  label: 'Status',
                  value: job ? <StatusBadge status={job.status} size="sm" /> : 'Unavailable',
                },
              ]}
            />

            <Link
              to={`/jobs/${recruitment.jobId}`}
              className="btn btn-ghost btn-sm mt-5"
            >
              View public posting
            </Link>
          </Card>
        </div>
      </div>
    </div>
  );
}
