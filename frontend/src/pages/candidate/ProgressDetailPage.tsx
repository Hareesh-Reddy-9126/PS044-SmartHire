import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { recruitmentsApi } from '../../api/client';
import type { RecruitmentResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Timeline } from '../../components/ui/Timeline';
import type { TimelineItem } from '../../components/ui/Timeline';
import { errorMessage } from '../../utils/errors';
import { formatDay, formatTime } from '../../utils/format';

/**
 * A candidate's view of one recruitment.
 *
 * The recruitment response carries state, not an event log: there are no
 * timestamps for "shortlisted" or "decided", so the timeline is derived from the
 * two statuses the service does own. Only the interview, which has a real stored
 * date and time, is shown with a time.
 */
export function ProgressDetailPage() {
  const { recruitmentId } = useParams<{ recruitmentId: string }>();
  const [recruitment, setRecruitment] = useState<RecruitmentResponse | null>(null);
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
      setRecruitment(await recruitmentsApi.get(recruitmentId));
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
          <Link to="/candidate/progress" className="btn btn-secondary btn-md">
            My progress
          </Link>
        </div>
      </div>
    );
  }

  const interviewStarted = recruitment.interviewStatus !== 'NOT_SCHEDULED';
  const interviewDone = recruitment.interviewStatus === 'COMPLETED';
  const decided = recruitment.finalStatus !== 'PENDING';
  const hasInterviewDetails = interviewStarted && Boolean(recruitment.interviewDate);

  const timeline: TimelineItem[] = [
    {
      title: 'Shortlisted',
      description: 'A recruiter started an interview process for your application.',
      status: 'completed',
    },
    {
      title: 'Interview scheduled',
      description: hasInterviewDetails
        ? `${formatDay(recruitment.interviewDate)} at ${formatTime(recruitment.interviewStartTime)}`
        : 'Waiting for the recruiter to schedule an interview.',
      status: interviewStarted ? 'completed' : 'active',
    },
    {
      title:
        recruitment.interviewStatus === 'NO_SHOW'
          ? 'Interview not attended'
          : 'Interview completed',
      description:
        recruitment.interviewStatus === 'NO_SHOW'
          ? 'The recruiter recorded that the interview did not take place.'
          : 'The recruiter records the outcome after the interview.',
      status: interviewDone ? 'completed' : interviewStarted ? 'active' : 'pending',
    },
    {
      title: 'Final decision',
      description: decided
        ? `The recruiter has reached a decision: ${recruitment.finalStatus}.`
        : 'No decision has been made yet.',
      status: decided ? 'completed' : 'pending',
    },
  ];

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Candidate workspace"
        title="Recruitment progress"
        description="What the hiring team has recorded so far."
        action={
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={recruitment.interviewStatus} />
            <StatusBadge status={recruitment.finalStatus} />
          </div>
        }
      />

      <div className="grid items-start gap-8 lg:grid-cols-2">
        <Card padding="lg">
          <h2 className="mb-6 font-headline-sm text-headline-sm text-on-surface">Progress</h2>
          <Timeline items={timeline} />
        </Card>

        <div className="space-y-6">
          {hasInterviewDetails && (
            <Card padding="lg">
              <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
                Interview details
              </h2>
              <DefinitionList
                items={[
                  { label: 'Date', value: formatDay(recruitment.interviewDate) },
                  { label: 'Start time', value: formatTime(recruitment.interviewStartTime) },
                  { label: 'Interviewer', value: recruitment.interviewer ?? 'Not recorded' },
                  { label: 'Location', value: recruitment.interviewLocation ?? 'Not recorded' },
                  {
                    label: 'Interview status',
                    value: <StatusBadge status={recruitment.interviewStatus} size="sm" />,
                  },
                ]}
              />
            </Card>
          )}

          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">References</h2>
            <DefinitionList
              items={[
                { label: 'Recruitment', value: <CopyableId value={recruitment.recruitmentId} /> },
                { label: 'Application', value: <CopyableId value={recruitment.applicationId} /> },
                {
                  label: 'Job',
                  value: (
                    <Link
                      to={`/jobs/${recruitment.jobId}`}
                      className="text-c-primary-700 underline break-all"
                    >
                      View the job posting
                    </Link>
                  ),
                },
              ]}
            />
          </Card>

          <div className="flex flex-wrap gap-3">
            <Link
              to={`/candidate/applications/${recruitment.applicationId}`}
              className="btn btn-secondary btn-md"
            >
              View application
            </Link>
            <Link to="/candidate/progress" className="btn btn-ghost btn-md">
              All progress
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
