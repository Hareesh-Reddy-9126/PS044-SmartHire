import { Link, useNavigate } from 'react-router-dom';
import { useCallback, useState, type FormEvent } from 'react';
import { authApi } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Alert';
import { errorMessage } from '../../utils/errors';

/** Passwords are validated by auth-service; these limits mirror its contract. */
const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_LENGTH = 128;

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setError(null);

      if (!email.trim()) {
        setError('Enter your email address.');
        return;
      }
      if (password.length < PASSWORD_MIN_LENGTH) {
        setError(`Your password must be at least ${PASSWORD_MIN_LENGTH} characters.`);
        return;
      }

      setIsSubmitting(true);
      try {
        await authApi.register(email.trim(), password);
        navigate('/login', { state: { email: email.trim() } });
      } catch (caught) {
        setError(errorMessage(caught, 'Registration failed. Please try again.'));
      } finally {
        setIsSubmitting(false);
      }
    },
    [email, password, navigate]
  );

  return (
    <>
      <div>
        <p className="page-eyebrow">Candidate registration</p>
        <h1 className="auth-form-title">Create an account</h1>
        <p className="auth-form-subtitle">
          Register as a candidate to apply for roles and follow every step of your
          application from one place.
        </p>
      </div>

      {error && (
        <div className="mt-7">
          <Alert tone="danger" title="Account not created">
            {error}
          </Alert>
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-7 space-y-5" noValidate>
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
          helperText="Use an address you can access — it identifies your application record."
        />

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
          showPasswordToggle
        />

        <Button type="submit" fullWidth size="lg" isLoading={isSubmitting}>
          Create account
        </Button>
      </form>

      <div className="mt-6 border-t border-c-border pt-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="font-body-sm text-body-sm text-c-text-muted">Already have an account?</p>
          <Link
            to="/login"
            className="font-label-md text-label-md text-c-primary-700 transition-colors hover:text-c-primary-900"
          >
            Sign in
          </Link>
        </div>
        <p className="mt-4 font-caption text-caption leading-relaxed text-c-text-subtle">
          Recruiter and administrator accounts are provisioned by the platform and
          cannot be created here.
        </p>
      </div>
    </>
  );
}
