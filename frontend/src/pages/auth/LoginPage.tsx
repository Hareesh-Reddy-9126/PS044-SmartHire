import { useCallback, useState, FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Alert } from '../../components/ui/Alert';
import { errorMessage } from '../../utils/errors';
import { safeNextPath } from '../../utils/roles';

type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

const ROLE_OPTIONS: { value: Role; label: string; email?: string; password?: string; note?: string }[] = [
  { value: 'CANDIDATE', label: 'Candidate', email: undefined, note: 'Create a new account via the registration page.' },
  { value: 'RECRUITER', label: 'Recruiter', email: 'recruiter@smarthire.local', password: 'Recruiter123!', note: 'Pre-seeded recruiter account for development.' },
  { value: 'ADMIN', label: 'Admin', email: 'admin@smarthire.local', password: 'Admin123!', note: 'Pre-seeded admin account for development.' },
];

const ROLE_SELECT_OPTIONS = ROLE_OPTIONS.map(({ value, label }) => ({ value, label }));

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
  const { login } = useAuth();

  const handleRoleChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    const role = event.target.value as Role;
    setSelectedRole(role);
    const option = ROLE_OPTIONS.find(r => r.value === role);
    setEmail(role === 'CANDIDATE' ? '' : (option?.email || ''));
    setPassword(role === 'CANDIDATE' ? '' : (option?.password || ''));
  };

  const handleSubmit = useCallback(async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const me = await login(email, password);
      const next = searchParams.get('next');
      navigate(safeNextPath(next, me.roles), { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Login failed. Please check your credentials and try again.'));
    } finally {
      setIsSubmitting(false);
    }
  }, [email, password, navigate, login, searchParams]);

  const selectedOption = ROLE_OPTIONS.find(r => r.value === selectedRole);

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

      <div className="mt-7 space-y-4">
        {registeredEmail && !error && (
          <Alert tone="success" title="Account created">
            Sign in with your new account to start applying.
          </Alert>
        )}

        {error && <Alert tone="danger">{error}</Alert>}
      </div>

      <form onSubmit={handleSubmit} className="mt-6 space-y-5">
        <Select
          id="role"
          label="Sign in as"
          value={selectedRole}
          onChange={handleRoleChange}
          options={ROLE_SELECT_OPTIONS}
        />

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

      {selectedRole !== 'CANDIDATE' && selectedOption && (
        <div className="auth-demo">
          <h2 className="auth-demo-title">Development credentials</h2>
          <div className="auth-demo-row">
            <span>Email</span>
            <span className="font-mono">{selectedOption.email}</span>
          </div>
          <div className="auth-demo-row">
            <span>Password</span>
            <span className="font-mono">{selectedOption.password}</span>
          </div>
          <p className="mt-2 text-[11px] leading-relaxed text-c-accent-700">{selectedOption.note}</p>
        </div>
      )}
    </>
  );
}
