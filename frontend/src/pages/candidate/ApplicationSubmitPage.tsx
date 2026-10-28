import { useEffect, useState, FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiErrorResponse, applicationsApi, jobsApi } from '../../api/client';
import type { JobResponse } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { errorMessage } from '../../utils/errors';

export function ApplicationSubmitPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const [job, setJob] = useState<JobResponse | null>(null);
  const [resumeLink, setResumeLink] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!jobId) return;
    let cancelled = false;
    jobsApi.get(jobId).then(j => {
      if (!cancelled) setJob(j);
    });
    return () => {
      cancelled = true;
    };
  }, [jobId]);

  const jobTitle = job?.role ?? '';
  const companyName = job?.companyName ?? '';

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');

    if (!resumeLink.trim()) {
      setError('Enter a link to your resume before applying.');
      return;
    }

    try {
      new URL(resumeLink);
    } catch {
      setError('Enter a valid http or https resume link.');
      return;
    }

    setSubmitting(true);

    applicationsApi
      .create({
        jobId: jobId!,
        resumeLink: resumeLink.trim(),
      })
      .then(result => {
        navigate(`/candidate/applications/${result.applicationId}`);
      })
      .catch(async err => {
        setSubmitting(false);
        if (err instanceof ApiErrorResponse && err.error.status === 409) {
          // The backend rejects duplicate active applications; send the candidate to the
          // application they already hold instead of showing a dead-end error.
          try {
            const page = await applicationsApi.list(0, 100);
            const existing = page.content.find(
              application =>
                application.jobId === jobId &&
                application.status !== 'WITHDRAWN' &&
                application.status !== 'REJECTED',
            );
            if (existing) {
              navigate(`/candidate/applications/${existing.applicationId}`, { replace: true });
              return;
            }
          } catch {
            // Fall through to the inline message if the lookup fails.
          }
          setError(
            'You already have an active application for this job. Open My applications to view it.',
          );
          return;
        }

        setError(errorMessage(err, 'Application submission failed. Please try again.'));
      });
  };

  return (
    <div className="space-y-8">
      <PageHeader
        title="Apply for a job"
        description={jobTitle ? `Applying for ${jobTitle} at ${companyName}` : undefined}
      />

      {error && <Alert tone="danger">{error}</Alert>}

      <Card padding="lg">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid gap-6 md:grid-cols-2">
            <Input
              id="jobTitle"
              label="Job title"
              value={jobTitle}
              disabled
              readOnly
              required
            />
            <Input
              id="companyName"
              label="Company"
              value={companyName}
              disabled
              readOnly
              required
            />
          </div>

          <Input
            id="resumeLink"
            label="Resume link"
            type="text"
            placeholder="https://example.com/resume.pdf"
            value={resumeLink}
            onChange={event => setResumeLink(event.target.value)}
            helperText="A public link to your résumé or CV"
          />

          <Button type="submit" fullWidth size="lg" isLoading={submitting}>
            {submitting ? 'Submitting…' : 'Submit application'}
          </Button>
        </form>
      </Card>
    </div>
  );
}