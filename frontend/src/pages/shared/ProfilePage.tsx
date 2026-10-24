import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { GapNotice } from '../../components/ui/Alert';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { PageHeader } from '../../components/ui/PageHeader';
import { RoleChip } from '../../components/ui/RoleChip';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../utils/errors';

/** Seconds remaining until the access token expires, refreshed every second. */
function useExpiryCountdown(expiresAt: number): number {
  const [remaining, setRemaining] = useState(() =>
    expiresAt ? Math.max(Math.floor((expiresAt - Date.now()) / 1000), 0) : 0
  );

  useEffect(() => {
    if (!expiresAt) {
      setRemaining(0);
      return;
    }

    const tick = () => setRemaining(Math.max(Math.floor((expiresAt - Date.now()) / 1000), 0));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [expiresAt]);

  return remaining;
}

function formatCountdown(seconds: number): string {
  if (seconds <= 0) return 'expired';
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return minutes > 0 ? `${minutes}m ${rest}s` : `${rest}s`;
}

/**
 * Read-only identity and session screen.
 *
 * There is no profile update endpoint, so this screen offers no editing
 * affordance rather than a disabled one — the gap notice explains why.
 */
export function ProfilePage({
  eyebrow,
  extraCards,
}: {
  eyebrow?: string;
  extraCards?: ReactNode;
}) {
  const { user, refreshUser, logout, tokenExpiresAt } = useAuth();
  const { showToast } = useToast();
  const [isRefreshing, setIsRefreshing] = useState(false);
  const remaining = useExpiryCountdown(tokenExpiresAt);

  if (!user) {
    return (
      <div className="space-y-6">
        <PageHeader eyebrow={eyebrow} title="Profile" />
        <Card padding="lg">
          <p className="font-body-md text-body-md text-c-text-muted">
            Your profile is not available right now.
          </p>
        </Card>
      </div>
    );
  }

  const refresh = async () => {
    setIsRefreshing(true);
    try {
      await refreshUser();
      showToast('Session refreshed.', 'success');
    } catch (error) {
      showToast(errorMessage(error, 'We could not refresh your session.'), 'error');
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={eyebrow}
        title="Profile"
        description="Your identity as the backend sees it."
      />

      <Card padding="lg">
        <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Account</h2>
        <DefinitionList
          items={[
            { label: 'Email', value: user.email },
            {
              label: 'Role',
              value: (
                <span className="flex flex-wrap gap-2">
                  {user.roles?.map(role => <RoleChip key={role} role={role} size="sm" />)}
                </span>
              ),
            },
            { label: 'User ID', value: <CopyableId value={user.sub} /> },
            {
              label: 'Organization',
              value: user.orgId ? (
                <CopyableId value={user.orgId} />
              ) : (
                <span className="text-c-text-muted">Not linked to an organization</span>
              ),
            },
          ]}
        />
      </Card>

      <Card padding="lg">
        <h2 className="font-headline-sm text-headline-sm text-on-surface">Session</h2>
        <p className="mt-3 font-body-md text-body-md text-c-text-muted">
          Access token expires in{' '}
          <span className="font-body-strong text-body-strong text-on-surface tabular">
            {formatCountdown(remaining)}
          </span>
          . It renews automatically while you are active.
        </p>

        <div className="mt-5 flex flex-wrap gap-3">
          <Button
            variant="secondary"
            onClick={() => void refresh()}
            isLoading={isRefreshing}
            disabled={remaining <= 0}
          >
            Refresh now
          </Button>
          <Button variant="secondary" onClick={() => void logout()}>
            Sign out
          </Button>
        </div>
      </Card>

      {extraCards}

      <GapNotice message="Profile details and password changes are not editable here — the API does not expose an update endpoint. Contact your administrator to change them." />
    </div>
  );
}
