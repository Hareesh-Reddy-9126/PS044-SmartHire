import { useCallback, useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { RoleSelector } from '../../components/auth/RoleSelector';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { errorMessage } from '../../utils/errors';
import { authPath, normaliseRole, roleLabel, type AccountRole } from '../../utils/roles';

/** Passwords are validated by auth-service; these limits mirror its contract. */
const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_LENGTH = 128;

/** Administrator accounts are provisioned by the platform operator, not self-service. */
type RegistrationRole = Extract<AccountRole, 'CANDIDATE' | 'RECRUITER'>;

const REGISTRATION_ROLES: readonly RegistrationRole[] = ['CANDIDATE', 'RECRUITER'];

const ROLE_EYEBROW: Record<RegistrationRole, string> = {
  CANDIDATE: 'Candidate registration',
  RECRUITER: 'Recruiter registration',
};

const ROLE_INTRO: Record<RegistrationRole, string> = {
  CANDIDATE:
    'Create a candidate account to apply for roles and follow every step of your application.',
  RECRUITER:
    'Create a recruiter account for your organization to publish roles and run hiring decisions.',
};

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { register } = useAuth();

  const requestedRole = normaliseRole(searchParams.get('role'));
  const next = searchParams.get('next');

  const selectRole = (role: RegistrationRole) => {
    setError(null);
    setOrganizationName('');
    const updated = new URLSearchParams(searchParams);
    updated.set('role', role);
    setSearchParams(updated, { replace: true });
  };

  const selectedRole: RegistrationRole =
    requestedRole === 'ADMIN' ? 'CANDIDATE' : requestedRole;

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setError(null);

      const trimmedEmail = email.trim();
      const trimmedOrganization = organizationName.trim();

      if (!trimmedEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
        setError('Enter a valid email address.');
        return;
      }
      if (password.length < PASSWORD_MIN_LENGTH) {
        setError(`Your password must be at least ${PASSWORD_MIN_LENGTH} characters.`);
        return;
      }
      if (password.length > PASSWORD_MAX_LENGTH) {
        setError(`Your password must be ${PASSWORD_MAX_LENGTH} characters or fewer.`);
        return;
      }
      if (password !== confirmPassword) {
        setError('The two passwords do not match.');
        return;
      }
      if (selectedRole === 'RECRUITER' && trimmedOrganization.length < 2) {
        setError('Enter the name of the organization you are hiring for.');
        return;
      }
      setIsSubmitting(true);
      try {
        await register({
          email: trimmedEmail,
          password,
          role: selectedRole,
          organizationName:
            selectedRole === 'RECRUITER' ? trimmedOrganization : undefined,
        });
        navigate(authPath('/login', selectedRole), {
          replace: true,
          state: { email: trimmedEmail, registered: true, next },
        });
      } catch (caught) {
        setError(errorMessage(caught, 'Registration failed. Please try again.'));
      } finally {
        setIsSubmitting(false);
      }
    },
    [
      email,
      password,
      confirmPassword,
      organizationName,
      selectedRole,
      next,
      navigate,
      register,
    ]
  );

  // Old bookmarks to administrator registration must not look like a broken
  // signup form. Administrators are provisioned by the operator and sign in.
  if (requestedRole === 'ADMIN') {
    return <Navigate to={authPath('/login', 'ADMIN', next)} replace />;
  }

  return (
    <>
      <div>
        <p className="page-eyebrow">{ROLE_EYEBROW[selectedRole]}</p>
        <h1 className="auth-form-title">Create an account</h1>
        <p className="auth-form-subtitle">{ROLE_INTRO[selectedRole]}</p>
      </div>

      <RoleSelector
        value={selectedRole}
        onChange={role => {
          if (role !== 'ADMIN') selectRole(role);
        }}
        roles={REGISTRATION_ROLES}
        legend="Create account as"
        disabled={isSubmitting}
      />

      {error && (
        <div className="mt-5">
          <Alert tone="danger" title="Account not created">
            {error}
          </Alert>
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-6 space-y-5" noValidate>
        <Input
          id="email"
          label="Email"
          type="email"
          placeholder="name@example.com"
          value={email}
          onChange={event => setEmail(event.target.value)}
          required
          autoComplete="email"
          maxLength={254}
          disabled={isSubmitting}
          helperText="Use an address you can access — it identifies your account."
        />

        {selectedRole === 'RECRUITER' && (
          <Input
            id="organizationName"
            label="Organization name"
            type="text"
            placeholder="Acme Corp"
            value={organizationName}
            onChange={event => setOrganizationName(event.target.value)}
            required
            autoComplete="organization"
            maxLength={255}
            disabled={isSubmitting}
            helperText="Your roles and job postings belong to this organization."
          />
        )}

        <Input
          id="password"
          label="Password"
          type="password"
          placeholder={`At least ${PASSWORD_MIN_LENGTH} characters`}
          value={password}
          onChange={event => setPassword(event.target.value)}
          required
          minLength={PASSWORD_MIN_LENGTH}
          maxLength={PASSWORD_MAX_LENGTH}
          helperText={`Use ${PASSWORD_MIN_LENGTH} characters or more.`}
          autoComplete="new-password"
          disabled={isSubmitting}
          showPasswordToggle
        />

        <Input
          id="confirmPassword"
          label="Confirm password"
          type="password"
          placeholder="Repeat your password"
          value={confirmPassword}
          onChange={event => setConfirmPassword(event.target.value)}
          required
          minLength={PASSWORD_MIN_LENGTH}
          maxLength={PASSWORD_MAX_LENGTH}
          autoComplete="new-password"
          disabled={isSubmitting}
          showPasswordToggle
        />

        <Button type="submit" fullWidth size="lg" isLoading={isSubmitting}>
          Create {roleLabel(selectedRole).toLowerCase()} account
        </Button>
      </form>

      <div className="mt-6 border-t border-c-border pt-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="font-body-sm text-body-sm text-c-text-muted">Already have an account?</p>
          <Link
            to={authPath('/login', selectedRole, next)}
            className="font-label-md text-label-md text-c-primary-700 transition-colors hover:text-c-primary-900"
          >
            Sign in
          </Link>
        </div>
        <p className="mt-4 font-caption text-caption leading-relaxed text-c-text-subtle">
          By creating an account you agree to use the workspace only for its intended role.
          Administrator accounts are provisioned by the platform operator.
        </p>
      </div>
    </>
  );
}
