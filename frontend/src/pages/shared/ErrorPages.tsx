import { Link } from 'react-router-dom';
// Shared terminal states: 403 permission, 404 no route, offline, expired session.
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { useAuth } from '../../context/AuthContext';

interface ErrorPageProps {
  code?: string;
  title: string;
  description: string;
  primaryTo?: string;
  primaryLabel?: string;
  secondaryTo?: string;
  secondaryLabel?: string;
}

function ErrorPageShell({
  code,
  title,
  description,
  primaryTo = '/jobs',
  primaryLabel = 'Browse jobs',
  secondaryTo,
  secondaryLabel,
}: ErrorPageProps) {
  return (
    <div className="mx-auto flex min-h-[60vh] max-w-xl flex-col items-center justify-center py-16 text-center">
      {code && (
        <p className="font-serif text-[5.5rem] font-light leading-none tracking-tightest text-c-border-strong">
          {code}
        </p>
      )}
      <h1 className="page-title mt-3">{title}</h1>
      <p className="mt-3 max-w-prose font-body-md text-body-md text-c-text-muted">{description}</p>
      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <Link to={primaryTo} className="btn btn-primary btn-md">
          {primaryLabel}
        </Link>
        {secondaryTo && secondaryLabel && (
          <Link to={secondaryTo} className="btn btn-secondary btn-md">
            {secondaryLabel}
          </Link>
        )}
      </div>
    </div>
  );
}

export function ForbiddenPage() {
  const { isAuthenticated } = useAuth();
  return (
    <ErrorPageShell
      code="403"
      title="You don't have access"
      description="This area is limited to a different role. Use an account that has permission, or return to a screen you can use."
      primaryTo={isAuthenticated ? '/workspace' : '/login'}
      primaryLabel={isAuthenticated ? 'Go to my workspace' : 'Sign in'}
      secondaryTo="/jobs"
      secondaryLabel="Browse jobs"
    />
  );
}

export function NotFoundPage() {
  return (
    <ErrorPageShell
      code="404"
      title="Page not found"
      description="That URL does not match any screen in SmartHire. Check the address, or go back to job listings."
      primaryTo="/jobs"
      primaryLabel="Browse jobs"
    />
  );
}

export function UnexpectedErrorPage() {
  return (
    <ErrorPageShell
      code="Error"
      title="Something went wrong"
      description="An unexpected error stopped this screen. Try again, or return to job listings."
      primaryTo="/jobs"
      primaryLabel="Browse jobs"
    />
  );
}

export function OfflinePage() {
  return (
    <div className="mx-auto flex min-h-[60vh] max-w-lg flex-col items-center justify-center py-16">
      <Card padding="lg" className="w-full">
        <p className="record-kicker">Connection</p>
        <h1 className="font-display text-[2.35rem] leading-none text-on-surface mt-2">
          Network unreachable
        </h1>
        <p className="mt-3 font-body-md text-body-md text-c-text-muted">
          SmartHire cannot reach the API Gateway. Check your connection and that the backend is
          running, then try again.
        </p>
        <div className="mt-6">
          <Button type="button" onClick={() => window.location.reload()}>
            Retry
          </Button>
        </div>
      </Card>
    </div>
  );
}

export function SessionExpiredPage() {
  return (
    <div>
      <p className="page-eyebrow">Session</p>
      <h1 className="auth-form-title">Session expired</h1>
      <p className="auth-form-subtitle">
        Your signed-in session ended. Sign in again to continue. On local HTTP, silent refresh may
        fail because the refresh cookie requires Secure.
      </p>
      <div className="mt-7 flex flex-wrap gap-3">
        <Link to="/login" className="btn btn-primary btn-md">
          Sign in
        </Link>
        <Link to="/jobs" className="btn btn-secondary btn-md">
          Browse jobs
        </Link>
      </div>
    </div>
  );
}
