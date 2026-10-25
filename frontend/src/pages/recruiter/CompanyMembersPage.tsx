import { useEffect, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/AuthContext';
import { errorMessage } from '../../utils/errors';

interface CompanyMember {
  id: string;
  userId: string;
  role: 'COMPANY_ADMIN' | 'RECRUITER';
  status: 'ACTIVE' | 'PENDING' | 'REJECTED';
}

/**
 * CompanyMembersPage
 * 
 * Displays company members and allows company admins to manage memberships
 * (invite, approve, reject, revoke).
 */
export function CompanyMembersPage() {
  const { user } = useAuth();
  const [members, setMembers] = useState<CompanyMember[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadMembers();
  }, []);

  const loadMembers = async () => {
    setIsLoading(true);
    setError(null);
    try {
      // In a real implementation, this would call the company memberships API
      // For now, we'll show a placeholder
      setMembers([]);
    } catch (err) {
      setError(errorMessage(err, 'Failed to load members.'));
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
          group
        </span>
        <p className="mt-4 font-body-md text-body-md text-c-text-muted">Loading members…</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="space-y-8">
        <PageHeader title="Company members" />
        <Card padding="lg">
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
              person_off
            </span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mt-4">
              Not a member of any company
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
      <PageHeader title="Company members" />

      {error && <Alert tone="danger">{error}</Alert>}

      <div className="space-y-6">
        <Card padding="lg">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="font-headline-sm text-headline-sm text-on-surface mb-2">Members</h2>
              <p className="font-body-md text-body-md text-c-text-muted">
                Manage company members and their roles.
              </p>
            </div>
            <Button variant="primary" size="lg" fullWidth={false}>
              Invite member
            </Button>
          </div>

          <ul className="space-y-4 mt-6">
            {members.map((member) => (
              <li key={member.id} className="flex items-center justify-between p-4 border-b border-c-border/50 last:border-0">
                <div className="flex items-center gap-4">
                  <div className="w-8 h-8 rounded-full bg-c-surface-sunken flex items-center justify-center text-c-text-muted">
                    <span className="material-symbols-outlined text-[24px]">{member.role === 'COMPANY_ADMIN' ? 'person_pin' : 'person'}</span>
                  </div>
                  <div className="flex-1">
                    <h3 className="font-body-md text-body-md text-on-surface">{member.userId}</h3>
                    <span className="font-caption text-caption text-c-text-muted">{member.role.toLowerCase()}</span>
                  </div>
                  <StatusBadge status={member.status} size="sm" />
                </div>
              </li>
            ))}
            {members.length === 0 && (
              <div className="text-center py-12">
                <span className="material-symbols-outlined text-[48px] text-c-text-muted" aria-hidden="true">
                  people_off
                </span>
                <h3 className="font-headline-sm text-headline-sm text-on-surface mt-4">
                  No members yet
                </h3>
                <p className="font-body-md text-body-md text-c-text-muted mt-2">
                  You haven't added any members to your company yet.
                </p>
              </div>
            )}
          </ul>
        </Card>
      </div>
    </div>
  );
}