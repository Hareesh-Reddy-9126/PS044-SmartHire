import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { applicationsApi, candidateDirectoryApi, jobsApi, recruitmentsApi } from '../../api/client';
import type {
  ApplicationResponse,
  JobResponse,
  RecruitmentResponse,
} from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { Input } from '../../components/ui/Input';
import { ConfirmDialog } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../utils/errors';
import { formatDay, formatTime } from '../../utils/format';

type Decision = 'HIRED' | 'REJECTED';

/**
 * One recruitment from the recruiter's side.
 *
 * The screen exposes exactly the transitions the service accepts:
 * schedule (NOT_SCHEDULED → SCHEDULED), record the interview outcome
 * (SCHEDULED → COMPLETED | NO_SHOW) and decide (PENDING → HIRED | REJECTED).
 * Anything already past those states is shown read-only rather than with a
 * disabled control that would 409.
 */
export function RecruiterRecruitmentDetailPage() {
  const { recruitmentId } = useParams<{ recruitmentId: string }>();
  const { execute, isLoading: isMutating } = useApi();

  const [recruitment, setRecruitment] = useState<RecruitmentResponse | null>(null);
  const [application, setApplication] = useState<ApplicationResponse | null>(null);
  const [candidateEmail, setCandidateEmail] = useState<string | null>(null);
  const [job, setJob] = useState<JobResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [interviewDate, setInterviewDate] = useState('');
  const [interviewTime, setInterviewTime] = useState('');
  const [interviewer, setInterviewer] = useState('');
  const [interviewLocation, setInterviewLocation] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingDecision, setPendingDecision] = useState<Decision | null>(null);

  const load = useCallback(async () => {
    if (!recruitmentId) {
      setIsLoading(false);
      setError('No recruitment was specified.');
      return;
    }

    setIsLoading(true);
    setError(null);
    setCandidateEmail(null);
    try {
      const found = await recruitmentsApi.get(recruitmentId);
      setRecruitment(found);

      const [applicationResult, jobResult, emailResult] = await Promise.allSettled([
        applicationsApi.get(found.applicationId),
        jobsApi.get(found.jobId),
        candidateDirectoryApi.emails([found.candidateId]),
      ]);
      setApplication(applicationResult.status === 'fulfilled' ? applicationResult.value : null);
      setJob(jobResult.status === 'fulfilled' ? jobResult.value : null);
      setCandidateEmail(
        emailResult.status === 'fulfilled' ? emailResult.value[found.candidateId] ?? null : null
      );
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load this recruitment."));
    } finally {
      setIsLoading(false);
    }
  }, [recruitmentId]);

  useEffect(() => {
    void load();
  }, [load]);

  const schedule = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!recruitment) return;
    setFormError(null);

    if (!interviewDate || !interviewTime || !interviewer.trim() || !interviewLocation.trim()) {
      setFormError('Date, start time, interviewer and location are all required.');
      return;
    }

    const updated = await execute(() => recruitmentsApi.schedule(recruitment.recruitmentId, {
      interviewDate,
      interviewStartTime: interviewTime,
      interviewer: interviewer.trim(),
      interviewLocation: interviewLocation.trim(),
    }), {
      successMessage: 'Interview scheduled.',
      errorMessage: "We couldn't schedule the interview.",
      onSuccess: data => {
        setRecruitment(data);
        setInterviewDate('');
        setInterviewTime('');
        setInterviewer('');
        setInterviewLocation('');
      },
    });

    if (updated) {
      setFormError(null);
    } else {
      setFormError('The interview could not be scheduled. Check the details and try again.');
    }
  };

  const recordOutcome = async (status: 'COMPLETED' | 'NO_SHOW') => {
    if (!recruitment) return;
    await execute(() => recruitmentsApi.updateInterviewStatus(recruitment.recruitmentId, { status }), {
      successMessage: status === 'COMPLETED' ? 'Interview marked completed.' : 'Interview marked no-show.',
      errorMessage: "We couldn't update the interview.",
      onSuccess: data => setRecruitment(data),
    });
  };

  const decide = async () => {
    if (!recruitment || !pendingDecision) return;
    const decision = pendingDecision;
    setPendingDecision(null);

    await execute(() => recruitmentsApi.updateFinalStatus(recruitment.recruitmentId, { status: decision }), {
      successMessage: decision === 'HIRED' ? 'Candidate hired.' : 'Candidate rejected.',
      errorMessage: "We couldn't record the decision.",
      onSuccess: data => {
        setRecruitment(data);
        // Recording a final decision also moves the application to the same
        // status. Refresh the local copy so the "Application status" card does
        // not keep showing SHORTLISTED after a hire.
        setApplication(current => (current ? { ...current, status: decision } : current));
      },
    });
  };

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
          <Link to="/recruiter/recruitments" className="btn btn-secondary btn-md">
            All recruitments
          </Link>
        </div>
      </div>
    );
  }

  const canSchedule = recruitment.interviewStatus === 'NOT_SCHEDULED';
  const canRecordOutcome = recruitment.interviewStatus === 'SCHEDULED';
  const canDecide = recruitment.finalStatus === 'PENDING';
  const hasInterviewDetails = Boolean(recruitment.interviewDate);

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruitment"
        title={job?.role ?? `Application ${recruitment.applicationId.slice(0, 8)}`}
        description={job ? `${job.companyName} · ${job.location}` : undefined}
        action={
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={recruitment.interviewStatus} />
            <StatusBadge status={recruitment.finalStatus} />
          </div>
        }
      />

      <div className="grid items-start gap-8 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {canSchedule && (
            <Card padding="lg">
              <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
                Schedule the interview
              </h2>
              <form className="space-y-4" onSubmit={schedule} noValidate>
                {formError && <Alert tone="danger">{formError}</Alert>}

                <div className="grid gap-4 sm:grid-cols-2">
                  <Input
                    label="Date"
                    type="date"
                    required
                    value={interviewDate}
                    min={new Date().toISOString().slice(0, 10)}
                    onChange={event => setInterviewDate(event.target.value)}
                  />
                  <Input
                    label="Start time"
                    type="time"
                    required
                    value={interviewTime}
                    onChange={event => setInterviewTime(event.target.value)}
                  />
                </div>
                <Input
                  label="Interviewer"
                  required
                  maxLength={200}
                  value={interviewer}
                  onChange={event => setInterviewer(event.target.value)}
                  helperText="Name of the person conducting the interview."
                />
                <Input
                  label="Location"
                  required
                  maxLength={300}
                  value={interviewLocation}
                  onChange={event => setInterviewLocation(event.target.value)}
                  helperText="An address, a meeting room, or a video link."
                />

                <Button type="submit" isLoading={isMutating}>
                  Schedule interview
                </Button>
              </form>
            </Card>
          )}

          {hasInterviewDetails && (
            <Card padding="lg">
              <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
                Interview
              </h2>
              <DefinitionList
                items={[
                  { label: 'Date', value: formatDay(recruitment.interviewDate) },
                  { label: 'Start time', value: formatTime(recruitment.interviewStartTime) },
                  { label: 'Interviewer', value: recruitment.interviewer ?? 'Not recorded' },
                  { label: 'Location', value: recruitment.interviewLocation ?? 'Not recorded' },
                  {
                    label: 'Status',
                    value: <StatusBadge status={recruitment.interviewStatus} size="sm" />,
                  },
                ]}
              />

              {canRecordOutcome && (
                <div className="mt-5 flex flex-wrap gap-3">
                  <Button
                    variant="secondary"
                    onClick={() => void recordOutcome('COMPLETED')}
                    disabled={isMutating}
                  >
                    Mark completed
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={() => void recordOutcome('NO_SHOW')}
                    disabled={isMutating}
                  >
                    Mark no-show
                  </Button>
                </div>
              )}
            </Card>
          )}

          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">
              Final decision
            </h2>

            {canDecide ? (
              <>
                <p className="font-body-md text-body-md text-c-text-muted">
                  Deciding also moves the candidate's application to the same status. This is
                  final.
                </p>
                <div className="mt-5 flex flex-wrap gap-3">
                  <Button onClick={() => setPendingDecision('HIRED')} disabled={isMutating}>
                    Hire candidate
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => setPendingDecision('REJECTED')}
                    disabled={isMutating}
                  >
                    Reject candidate
                  </Button>
                </div>
              </>
            ) : (
              <p className="font-body-md text-body-md text-c-text-muted">
                This recruitment has been decided: <strong>{recruitment.finalStatus}</strong>. The
                decision cannot be changed.
              </p>
            )}
          </Card>
        </div>

        <div className="space-y-6">
          <Card padding="lg">
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Candidate</h2>
            <DefinitionList
              columns={2}
              items={[
                { label: 'Candidate email', value: candidateEmail ?? 'Unavailable' },
                { label: 'Candidate ID', value: <CopyableId value={recruitment.candidateId} /> },
                {
                  label: 'Application status',
                  value: application ? (
                    <StatusBadge status={application.status} size="sm" />
                  ) : (
                    'Unavailable'
                  ),
                },
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
            <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">References</h2>
            <DefinitionList
              columns={1}
              items={[
                { label: 'Recruitment', value: <CopyableId value={recruitment.recruitmentId} /> },
                { label: 'Application', value: <CopyableId value={recruitment.applicationId} /> },
                {
                  label: 'Job',
                  value: (
                    <Link
                      to={`/recruiter/jobs/${recruitment.jobId}`}
                      className="text-c-primary-700 underline break-all"
                    >
                      Open the job
                    </Link>
                  ),
                },
              ]}
            />
          </Card>
        </div>
      </div>

      {pendingDecision && (
        <ConfirmDialog
          title={pendingDecision === 'HIRED' ? 'Hire this candidate?' : 'Reject this candidate?'}
          message={
            pendingDecision === 'HIRED'
              ? 'The application will be marked HIRED. This cannot be undone.'
              : 'The application will be marked REJECTED. This cannot be undone.'
          }
          confirmLabel={pendingDecision === 'HIRED' ? 'Hire candidate' : 'Reject candidate'}
          onConfirm={() => void decide()}
          onCancel={() => setPendingDecision(null)}
          confirmLoading={isMutating}
        />
      )}
    </div>
  );
}
