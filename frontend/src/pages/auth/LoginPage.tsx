import { useCallback, useState, FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { errorMessage } from '../../utils/errors';
import { safeNextPath } from '../../utils/roles';

type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: 'CANDIDATE', label: 'Candidate' },
  { value: 'RECRUITER', label: 'Recruiter' },
  { value: 'ADMIN', label: 'Admin' },
];

export function LoginPage() {
  const location = useLocation();
  const registeredEmail =
    (location.state as { email?: string } | null)?.email?.trim() ?? '';
  const [email, setEmail] = useState(registeredEmail);
  const [password, setPassword] = useState('');
  const [selectedRole, setSelectedRole] = useState<Role>('CANDIDATE');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, logout } = useAuth();

  const handleSubmit = useCallback(async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const me = await login(email, password);

      if (!me.roles.includes(selectedRole)) {
        await logout();
        setError('This account does not have the selected role.');
        return;
      }

      const next = searchParams.get('next');
      navigate(safeNextPath(next, me.roles), { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Login failed. Please check your credentials and try again.'));
    } finally {
      setIsSubmitting(false);
    }
  }, [email, password, navigate, login, logout, searchParams, selectedRole]);

  return (
    <>
      <div>
        <p className="page-eyebrow">Your workspace</p>
        <h1 className="auth-form-title">Sign in</h1>
        <p className="auth-form-subtitle">
          Welcome back. Sign in to see your applications, recruitment progress and
          notifications.
        </p>
      </div>

      {error && <Alert tone="danger">{error}</Alert>}

      <form onSubmit={handleSubmit} className="mt-6 space-y-5">
        <div className="space-y-2">
          <label htmlFor="role" className="font-label-md text-label-md text-c-text-muted">
            Sign in as
          </label>
          <select
            id="role"
            value={selectedRole}
            onChange={event => setSelectedRole(event.target.value as Role)}
            className="input w-full"
          >
            {ROLE_OPTIONS.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <Input
          id="email"
          label="Email"
          type="email"
          placeholder="name@example.com"
          value={email}
          onChange={event => setEmail(event.target.value)}
          required
          error={error.includes('email') ? error : undefined}
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
          error={error.includes('password') ? error : undefined}
          autoComplete="current-password"
          showPasswordToggle
        />

        <Button type="submit" fullWidth size="lg" isLoading={isSubmitting}>
          Sign in
        </Button>
      </form>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-c-border pt-5">
        <p className="font-body-sm text-body-sm text-c-text-muted">New to SmartHire?</p>
        <Link
          to="/register"
          className="font-label-md text-label-md text-c-primary-700 transition-colors hover:text-c-primary-900"
        >
          Create an account
        </Link>
      </div>
    </>
  );
}