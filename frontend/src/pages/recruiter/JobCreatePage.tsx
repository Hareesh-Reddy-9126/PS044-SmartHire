import { useCallback, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { PageHeader } from '../../components/ui/PageHeader';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../utils/errors';

/**
 * Creates a job posting.
 *
 * A new posting starts in DRAFT. The recruiter is taken to the posting itself,
 * where "Publish job" performs the DRAFT → OPEN transition, so creating and
 * publishing stay two explicit steps.
 */
export function JobCreatePage() {
  const [companyName, setCompanyName] = useState('');
  const [role, setRole] = useState('');
  const [location, setLocation] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const { showToast } = useToast();

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setError(null);

      if (!companyName.trim() || !role.trim() || !location.trim()) {
        setError('Company name, role and location are all required.');
        return;
      }

      setIsSubmitting(true);
      try {
        const created = await jobsApi.create({
          companyName: companyName.trim(),
          role: role.trim(),
          location: location.trim(),
        });
        showToast('Job created as a draft. Publish it when it is ready.', 'success');
        navigate(`/recruiter/jobs/${created.jobId}`);
      } catch (caught) {
        setError(errorMessage(caught, "We couldn't create the job. Please try again."));
      } finally {
        setIsSubmitting(false);
      }
    },
    [companyName, role, location, navigate, showToast]
  );

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruiter workspace"
        title="Create a job posting"
        description="New postings start as drafts and are only visible to candidates once published."
      />

      {error && (
        <Alert tone="danger" title="Job not created">
          {error}
        </Alert>
      )}

      <Card padding="lg">
        <form onSubmit={handleSubmit} className="space-y-6" noValidate>
          <Input
            id="companyName"
            label="Company name"
            value={companyName}
            onChange={event => setCompanyName(event.target.value)}
            maxLength={200}
            placeholder="Acme Corporation"
            required
          />

          <Input
            id="role"
            label="Job role"
            value={role}
            onChange={event => setRole(event.target.value)}
            maxLength={200}
            placeholder="Senior Backend Engineer"
            required
          />

          <Input
            id="location"
            label="Location"
            value={location}
            onChange={event => setLocation(event.target.value)}
            maxLength={200}
            placeholder="e.g. London, Remote"
            required
          />

          <div className="flex flex-wrap gap-3">
            <Button type="submit" isLoading={isSubmitting}>
              Create job
            </Button>
            <Link to="/recruiter/jobs" className="btn btn-secondary btn-md">
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  );
}
