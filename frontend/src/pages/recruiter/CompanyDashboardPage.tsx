import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { companiesApi, CompanyResponse } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/AuthContext';
import { errorMessage } from '../../utils/errors';

/**
 * CompanyDashboardPage
 * 
 * Displays company overview information for company admins and recruiters.
 * Shows company status, member count, and quick actions.
 */
export function CompanyDashboardPage() {
  useAuth();
  const [company, setCompany] = useState<CompanyResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadCompany = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      // For now, we list companies and find the one the user is a member of
      // In a full implementation, we'd query by user membership
      const companies = await companiesApi.list();
      const userCompany = companies.content.find((c: CompanyResponse) => companyMembershipHasAccess(c.companyId));
      if (userCompany) {
        setCompany(userCompany);
      } else {
        setError('You are not a member of any company.');
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to load company.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCompany();
  }, [loadCompany]);

  // Placeholder - in a real app, we'd check membership via API
  const companyMembershipHasAccess = (_companyId: string): boolean => {
    // This would check if the user is a member of this company
    // For now, return true if there's at least one company
    return true;
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
          business
        </span>
        <p className="mt-4 font-body-md text-body-md text-c-text-muted">Loading company…</p>
      </div>
    );
  }

  if (!company) {
    return (
      <div className="space-y-8">
        <PageHeader title="Company dashboard" />
        {error && <Alert tone="danger">{error}</Alert>}
        <Card padding="lg">
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
              business
            </span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mt-4">
              No company
            </h2>
            <p className="font-body-md text-body-md text-c-text-muted mt-2">
              You are not a member of any company. Register a company or ask a company admin to invite you.
            </p>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title={company.name}
        description={`Company status: ${company.status.replace('_', ' ').toLowerCase()}`}
        action={
          company.status === 'PENDING_APPROVAL' ? (
            <Alert tone="warning">
              Your company is pending approval by a platform administrator.
            </Alert>
          ) : company.status === 'SUSPENDED' ? (
            <Alert tone="danger">
              Your company has been suspended. Contact platform support.
            </Alert>
          ) : null
        }
      />

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <Card padding="lg">
          <h3 className="font-headline-sm text-headline-sm text-on-surface mb-4">Company name</h3>
          <p className="font-body-lg text-body-lg text-on-surface">{company.name}</p>
        </Card>

        <Card padding="lg">
          <h3 className="font-headline-sm text-headline-sm text-on-surface mb-4">Status</h3>
          <StatusBadge status={company.status} size="md" />
        </Card>

        <Card padding="lg">
          <h3 className="font-headline-sm text-headline-sm text-on-surface mb-4">Company ID</h3>
          <p className="font-code-sm text-code-sm text-c-text-muted break-all">{company.companyId}</p>
        </Card>
      </div>

      <div className="grid gap-6 sm:grid-cols-3">
        <Card padding="lg">
          <h3 className="font-headline-sm text-headline-sm text-on-surface mb-4">Company actions</h3>
          <div className="space-y-4">
            <Link to="/recruiter/jobs/new" className="btn btn-primary btn-md w-full">
              Create job posting
            </Link>
            <Link to="/recruiter/recruitments" className="btn btn-secondary btn-md w-full">
              View recruitments
            </Link>
          </div>
        </Card>

        <Card padding="lg">
          <h3 className="font-headline-sm text-headline-sm text-on-surface mb-4">Quick links</h3>
          <div className="space-y-4">
            <Link to="/candidate/applications" className="btn btn-secondary btn-md w-full">
              Browse jobs
            </Link>
            <Link to="/candidate/profile" className="btn btn-secondary btn-md w-full">
              My profile
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
}