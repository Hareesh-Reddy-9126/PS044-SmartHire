import { useCallback, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
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

const ROLE_EYEBROW: Record<AccountRole, string> = {
  CANDIDATE: 'Candidate registration',
  RECRUITER: 'Recruiter registration',
  ADMIN: 'Administrator registration',
};

const ROLE_INTRO: Record<AccountRole, string> = {
  CANDIDATE:
    'Create a candidate account to apply for roles and follow every step of your application.',
  RECRUITER:
    'Create a recruiter account for your organization to publish roles and run hiring decisions.',
  ADMIN:
    'Create an administrator account with the invitation code issued by your platform operator.',
};

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [adminInviteCode, setAdminInviteCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { register } = useAuth();

  const selectedRole = normaliseRole(searchParams.get('role'));
  const next = searchParams.get('next');

  const selectRole = (role: AccountRole) => {
    setError(null);
    setOrganizationName('');
    setAdminInviteCode('');
    const updated = new URLSearchParams(searchParams);
    updated.set('role', role);
    setSearchParams(updated, { replace: true });
  };

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setError(null);

      const trimmedEmail = email.trim();
      const trimmedOrganization = organizationName.trim();
      const trimmedInviteCode = adminInviteCode.trim();

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
      if (selectedRole === 'ADMIN' && !trimmedInviteCode) {
        setError('Enter the administrator invitation code issued to you.');
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
          adminInviteCode: selectedRole === 'ADMIN' ? trimmedInviteCode : undefined,
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
      adminInviteCode,
      selectedRole,
      next,
      navigate,
      register,
    ]
  );

  return (
    <>
      <div>
        <p className="page-eyebrow">{ROLE_EYEBROW[selectedRole]}</p>
        <h1 className="auth-form-title">Create an account</h1>
        <p className="auth-form-subtitle">{ROLE_INTRO[selectedRole]}</p>
      </div>

      <RoleSelector
        value={selectedRole}
        onChange={selectRole}
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

        {selectedRole === 'ADMIN' && (
          <Input
            id="adminInviteCode"
            label="Administrator invitation code"
            type="password"
            placeholder="Invitation code"
            value={adminInviteCode}
            onChange={event => setAdminInviteCode(event.target.value)}
            required
            autoComplete="off"
            maxLength={256}
            disabled={isSubmitting}
            helperText="Administrator signup is closed unless the deployment has issued you a code."
            showPasswordToggle
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
          {selectedRole === 'ADMIN'
            ? 'Administrator accounts are privileged. The deployment validates the invitation code before the account is created.'
            : 'By creating an account you agree to use the workspace only for its intended role.'}
        </p>
      </div>
    </>
  );
}
