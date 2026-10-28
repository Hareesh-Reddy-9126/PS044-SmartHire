import { useCallback, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { RoleSelector } from '../../components/auth/RoleSelector';
import { GoogleSignInButton } from '../../components/auth/GoogleSignInButton';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { errorMessage } from '../../utils/errors';
import type { MeResponse } from '../../api/client';
import {
  authPath,
  normaliseRole,
  primaryRole,
  roleHome,
  roleLabel,
  safeNextPath,
  type AccountRole,
} from '../../utils/roles';

interface LoginLocationState {
  /** Prefilled after registration. */
  email?: string;
  /** Set by the registration screen after a successful account creation. */
  registered?: boolean;
  /** Destination preserved across the registration round-trip. */
  next?: string;
}

const ROLE_SUBTITLE: Record<AccountRole, string> = {
  CANDIDATE:
    'Track your applications, documents and interview progress in one accountable record.',
  RECRUITER:
    'Publish roles, review applicants and record interview and hiring decisions.',
  ADMIN: 'Review the platform operating picture and the decisions recorded across workspaces.',
};

export function LoginPage() {
  const location = useLocation();
  const locationState = (location.state as LoginLocationState | null) ?? null;
  const [email, setEmail] = useState(locationState?.email?.trim() ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { login, loginWithGoogle } = useAuth();

  const roleParam = searchParams.get('role');
  const selectedRole = normaliseRole(roleParam);
  // Only an explicit portal choice can be "wrong". A generic /login link must not
  // greet a recruiter with a mismatch notice.
  const roleWasExplicit = roleParam !== null;
  const next = locationState?.next ?? searchParams.get('next');

  const selectRole = (role: AccountRole) => {
    setError('');
    const updated = new URLSearchParams(searchParams);
    updated.set('role', role);
    // Keep the requested destination when the visitor switches portal.
    setSearchParams(updated, { replace: true });
  };

  const completeSignIn = useCallback(
    (me: MeResponse) => {
      const roles = me.roles ?? [];

      if (roleWasExplicit && !roles.includes(selectedRole)) {
        // The token is authoritative: never tear the session down because the
        // visitor picked the wrong tab. Open the account's real workspace and
        // explain the switch instead.
        const actualRole = roleLabel(primaryRole(roles));
        navigate(roleHome(roles), {
          replace: true,
          state: {
            notice: {
              tone: 'info',
              title: `Signed in to the ${actualRole} workspace`,
              message: `This account is registered as ${actualRole}, so we opened that portal instead of the ${roleLabel(
                selectedRole
              )} portal.`,
            },
          },
        });
        return;
      }

      navigate(safeNextPath(next, roles), { replace: true });
    },
    [navigate, next, roleWasExplicit, selectedRole]
  );

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setError('');
      setIsSubmitting(true);

      try {
        completeSignIn(await login(email, password));
      } catch (caught) {
        setError(errorMessage(caught, 'Login failed. Please check your credentials and try again.'));
      } finally {
        setIsSubmitting(false);
      }
    },
    [email, password, login, completeSignIn]
  );

  const handleGoogleCredential = useCallback(
    async (credential: string) => {
      setError('');
      setIsGoogleSubmitting(true);

      try {
        completeSignIn(await loginWithGoogle(credential));
      } catch (caught) {
        setError(
          errorMessage(
            caught,
            'Google sign-in failed. Please try again or use your email and password.'
          )
        );
      } finally {
        setIsGoogleSubmitting(false);
      }
    },
    [loginWithGoogle, completeSignIn]
  );

  return (
    <>
      <div>
        <p className="page-eyebrow">Your workspace</p>
        <h1 className="auth-form-title">Sign in</h1>
        <p className="auth-form-subtitle">{ROLE_SUBTITLE[selectedRole]}</p>
      </div>

      <RoleSelector
        value={selectedRole}
        onChange={selectRole}
        legend="Sign in as"
        disabled={isSubmitting}
      />

      {locationState?.registered && (
        <div className="mt-5">
          <Alert tone="success" title="Account created">
            Sign in with the credentials you just created.
          </Alert>
        </div>
      )}

      {error && (
        <div className="mt-5">
          <Alert tone="danger">{error}</Alert>
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-6 space-y-5">
        <Input
          id="email"
          label="Email"
          type="email"
          placeholder="name@example.com"
          value={email}
          onChange={event => setEmail(event.target.value)}
          required
          error={error.toLowerCase().includes('email') ? error : undefined}
          autoComplete="email"
        />

        <Input
          id="password"
          label="Password"
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={event => setPassword(event.target.value)}
          required
          error={error.toLowerCase().includes('password') ? error : undefined}
          autoComplete="current-password"
          showPasswordToggle
        />

        <Button type="submit" fullWidth size="lg" isLoading={isSubmitting}>
          Sign in
        </Button>
      </form>

      <GoogleSignInButton
        onCredential={handleGoogleCredential}
        disabled={isSubmitting || isGoogleSubmitting}
      />

      {isGoogleSubmitting && (
        <p className="mt-3 text-center font-body-sm text-body-sm text-c-text-muted">
          Signing in with Google…
        </p>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-c-border pt-5">
        <p className="font-body-sm text-body-sm text-c-text-muted">New to SmartHire?</p>
        <Link
          to={authPath('/register', selectedRole)}
          className="font-label-md text-label-md text-c-primary-700 transition-colors hover:text-c-primary-900"
        >
          Create a {roleLabel(selectedRole).toLowerCase()} account
        </Link>
      </div>
    </>
  );
}
