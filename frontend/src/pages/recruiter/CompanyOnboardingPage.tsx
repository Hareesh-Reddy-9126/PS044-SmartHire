import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { companiesApi } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { errorMessage } from '../../utils/errors';

/**
 * CompanyOnboardingPage
 * 
 * Allows an authenticated user to register a new company.
 * The company is created in PENDING_APPROVAL status and must be
 * approved by a Platform Admin before it becomes active.
 * The registering user becomes a PENDING COMPANY_ADMIN member.
 */
export function CompanyOnboardingPage() {
  const [companyName, setCompanyName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');

    try {
      await companiesApi.create({ name: companyName.trim() });
      navigate('/candidate/applications');
    } catch (err) {
      setError(errorMessage(err, 'Failed to register company. Please try again.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-8">
      <PageHeader
        title="Register your company"
        description="Create a company account to post jobs and manage recruitment. Your company will be reviewed by a platform administrator before it becomes active."
      />

      {error && <Alert tone="danger">{error}</Alert>}

      <Card padding="lg">
        <h2 className="font-headline-sm text-headline-sm text-on-surface mb-6">Company details</h2>

        <form onSubmit={handleSubmit} className="space-y-6">
          <Input
            id="companyName"
            label="Company name"
            type="text"
            placeholder="e.g. Acme Corp"
            value={companyName}
            onChange={event => setCompanyName(event.target.value)}
            required
            helperText="The legal name of your organization"
            maxLength={200}
          />

          <div className="card p-4 text-sm text-c-text-muted">
            <p className="font-body-md text-body-md text-on-surface mb-2">
              What happens next?
            </p>
            <ul className="list-disc list-inside space-y-2 font-body-md text-body-sm text-c-text-muted">
              <li>
                Your company will be created as <strong>pending approval</strong>.
              </li>
              <li>
                A platform administrator will review your application.
              </li>
                <li>
                Once approved, you can post jobs, schedule interviews, and manage your
                recruitment workflow.
              </li>
            </ul>
          </div>

          <Button type="submit" fullWidth size="lg" isLoading={isSubmitting}>
            Register company
          </Button>
        </form>
      </Card>
    </div>
  );
}