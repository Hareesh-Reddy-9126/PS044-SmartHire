import { useState, FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { AuthLayout } from '../../components/layout/Layout';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const next = searchParams.get('next') || '/candidate/applications';

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      await login(email, password);
      showToast('Signed in successfully', 'success');
      navigate(next, { replace: true });
    } catch (err) {
      const apiError = err as { error?: { status: number; detail: string } } | null;
      if (apiError?.error?.status === 401) {
        setError('Incorrect email or password.');
      } else if (apiError?.error?.status === 429) {
        setError('Too many attempts. Wait a moment and try again.');
      } else {
        setError(apiError?.error?.detail || 'Sign in failed. Please try again.');
      }
      showToast('Sign in failed', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout>
      <div className="bg-c-surface rounded-xl shadow-sm p-8 relative overflow-hidden">
        <div className="absolute top-0 left-0 right-0 h-1 bg-c-primary-700" />
        
        <div className="mb-6 text-center">
          <h1 className="font-display text-display text-c-primary-900 tracking-tight">Sign in</h1>
          <p className="font-caption text-caption text-c-text-muted mt-1">
            Provide your credentials to access the employment architecture.
          </p>
        </div>

        {error && (
          <div className="error-panel mb-4" role="alert">
            <span className="error-panel-icon">error</span>
            <div className="flex-1 min-w-0">
              <h2 className="font-body-strong text-body-strong text-danger-700">Authentication Failed</h2>
              <p className="font-caption text-caption text-danger-800 mt-0.5">{error}</p>
              <div className="mt-2 flex items-center gap-2 font-code-sm text-code-sm text-danger-800 bg-danger-50 px-2 py-1 rounded">
                <span>status: 401</span>
                <span>•</span>
                <span className="truncate">code: invalid_credentials</span>
              </div>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <Input
            label="Email address"
            type="email"
            autoComplete="username"
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="name@example.com"
            required
            helperText="Authorized organizational or personal email."
          />

          <div className="flex flex-col">
            <label className="label">
              Password <span aria-hidden="true" className="text-danger-600">*</span>
            </label>
            <div className="relative flex items-center">
              <input
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                className="input pr-10"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Enter your password"
                required
              />
              <button
                type="button"
                className="absolute right-2 p-1.5 text-c-text-muted hover:text-on-surface transition-colors"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                aria-pressed={showPassword}
              >
                <span className="material-symbols-outlined text-[20px]">
                  {showPassword ? 'visibility_off' : 'visibility'}
                </span>
              </button>
            </div>
            <p className="helper-text">UTF-8 credential sequence. Case-sensitive.</p>
          </div>

          <Button type="submit" className="w-full" isLoading={isLoading}>
            <span>{isLoading ? 'Verifying...' : 'Sign in'}</span>
            <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
          </Button>
        </form>

        <div className="mt-6 pt-4 flex items-center justify-between bg-c-surface-sunken p-3 rounded-lg">
          <span className="font-overline text-overline uppercase text-c-text-muted">Simulate 401 Exception</span>
          <Button variant="secondary" size="sm" type="button" onClick={() => setError('Incorrect email or password.')}>
            POST /api/v1/auth/login
          </Button>
        </div>

        <div className="mt-6 w-full flex flex-col items-center text-center space-y-3">
          <Link to="/register" className="font-body-md text-body-md text-c-primary-700 hover:text-c-primary-900 transition-colors inline-flex items-center gap-1">
            <span>New here? Create a candidate account</span>
            <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
          </Link>
          <div className="w-full p-3 bg-c-surface-sunken rounded-lg flex items-start gap-2 text-left shadow-sm">
            <span className="material-symbols-outlined text-c-accent-700 text-[18px] shrink-0 mt-0.5">lock_clock</span>
            <p className="font-caption text-caption text-c-text-muted leading-relaxed">
              Sessions are secured via 15-minute access tokens with encrypted cookie refresh. 
              Audit logging enabled for all authentication events under RFC 9457 protocols.
            </p>
          </div>
        </div>
      </div>
    </AuthLayout>
  );
}