import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { jobsApi } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../utils/errors';

/**
 * Edits a posting's three mutable fields.
 *
 * The job contract has no description, publish date or salary, so the form does
 * not offer them: a control that cannot be saved is worse than no control.
 */
export function JobEditPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [companyName, setCompanyName] = useState('');
  const [role, setRole] = useState('');
  const [location, setLocation] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (!jobId) {
      setIsLoading(false);
      setError('No job was specified.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const job = await jobsApi.get(jobId);
      setCompanyName(job.companyName);
      setRole(job.role);
      setLocation(job.location);
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load this job."));
    } finally {
      setIsLoading(false);
    }
  }, [jobId]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      if (!jobId) return;

      setError(null);
      if (!companyName.trim() || !role.trim() || !location.trim()) {
        setError('Company name, role and location are all required.');
        return;
      }

      setIsSubmitting(true);
      try {
        await jobsApi.update(jobId, {
          companyName: companyName.trim(),
          role: role.trim(),
          location: location.trim(),
        });
        showToast('Job updated.', 'success');
        navigate(`/recruiter/jobs/${jobId}`);
      } catch (caught) {
        setError(errorMessage(caught, "We couldn't update the job."));
      } finally {
        setIsSubmitting(false);
      }
    },
    [jobId, companyName, role, location, navigate, showToast]
  );

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl py-12">
        <SkeletonDetail />
      </div>
    );
  }

  if (!jobId || (error && !companyName)) {
    return (
      <div className="mx-auto max-w-xl space-y-6 py-12 text-center">
        <Alert tone="danger" title="Job unavailable">
          {error ?? "We couldn't find this job."}
        </Alert>
        <div className="flex justify-center gap-3">
          <Button onClick={() => void load()}>Try again</Button>
          <Link to="/recruiter/jobs" className="btn btn-secondary btn-md">
            Back to jobs
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Recruiter workspace"
        title="Edit job posting"
        description="Changes apply to the posting candidates see."
      />

      {error && (
        <Alert tone="danger" title="Job not saved">
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
            required
          />

          <Input
            id="role"
            label="Job role"
            value={role}
            onChange={event => setRole(event.target.value)}
            maxLength={200}
            required
          />

          <Input
            id="location"
            label="Location"
            value={location}
            onChange={event => setLocation(event.target.value)}
            maxLength={200}
            required
          />

          <div className="flex flex-wrap gap-3">
            <Button type="submit" isLoading={isSubmitting}>
              Save changes
            </Button>
            <Link to={`/recruiter/jobs/${jobId}`} className="btn btn-secondary btn-md">
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  );
}
