import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { errorMessage } from '../../utils/errors';

/**
 * CompanyJobCreatePage
 * 
 * Allows a recruiter to create a new job posting for their company.
 * The job starts as a DRAFT and can be submitted for approval.
 */
export function CompanyJobCreatePage() {
  const [formData, setFormData] = useState({
    role: '',
    companyName: '',
    location: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');

    try {
      const response = await jobsApi.create({
        companyName: formData.companyName,
        role: formData.role,
        location: formData.location,
      });
      navigate(`/recruiter/jobs/${response.jobId}`);
    } catch (err) {
      setError(errorMessage(err, 'Failed to create job. Please try again.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-8">
      <PageHeader
        title="Create job posting"
        description="Create a new job posting for your company. The job will start as a draft and can be published when ready."
      />

      {error && <Alert tone="danger">{error}</Alert>}

      <Card padding="lg">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid gap-6 sm:grid-cols-2">
            <Input
              id="role"
              label="Job title"
              placeholder="e.g. Senior Software Engineer"
              value={formData.role}
              onChange={event => setFormData({ ...formData, role: event.target.value })}
              required
              maxLength={200}
            />
            <Input
              id="companyName"
              label="Company name"
              value={formData.companyName}
              disabled
              required
              helperText="Your company name"
            />
          </div>

          <Input
            id="location"
            label="Location"
            placeholder="e.g. London, UK or Remote"
            value={formData.location}
            onChange={event => setFormData({ ...formData, location: event.target.value })}
            required
            maxLength={200}
          />

          <div className="alert alert-info">
            <p className="font-body-md text-body-md text-c-text-muted">
              <span className="font-caption text-caption text-c-text-muted">Note:</span>
              {' '}
              The job will be created as a draft. You can publish it when ready to accept applications.
            </p>
          </div>

          <div className="flex justify-end">
            <Button type="submit" fullWidth size="lg" isLoading={isSubmitting}>
              Create job
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}