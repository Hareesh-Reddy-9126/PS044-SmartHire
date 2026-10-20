import { useState, FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { AuthLayout } from '../../components/layout/Layout';

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [confirmError, setConfirmError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  const { register } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const registered = searchParams.get('registered') === '1';
  const registeredEmail = searchParams.get('email') || '';

  const validatePassword = (pwd: string): string[] => {
    const errors: string[] = [];
    if (pwd.length < 12) errors.push('At least 12 characters');
    if (!/[A-Z]/.test(pwd)) errors.push('One uppercase letter (advisory)');
    if (!/[a-z]/.test(pwd)) errors.push('One lowercase letter (advisory)');
    if (!/[0-9]/.test(pwd)) errors.push('One number (advisory)');
    if (!/[^A-Za-z0-9]/.test(pwd)) errors.push('One symbol (advisory)');
    return errors;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setEmailError('');
    setPasswordError('');
    setConfirmError('');

    if (!email) {
      setEmailError('Email is required.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Enter a valid email address.');
      return;
    }
    if (email.length > 255) {
      setEmailError('Email must be 255 characters or fewer.');
      return;
    }
    if (!password) {
      setPasswordError('Password is required.');
      return;
    }
    if (password.length < 12) {
      setPasswordError('Password must be at least 12 characters.');
      return;
    }
    if (password.length > 128) {
      setPasswordError('Password must be 128 characters or fewer.');
      return;
    }
    if (password !== confirmPassword) {
      setConfirmError('Passwords do not match.');
      return;
    }

    setIsLoading(true);

    try {
      await register(email, password);
      showToast('Account created. Sign in to continue.', 'success');
      navigate(`/login?registered=1&email=${encodeURIComponent(email)}`, { replace: true });
    } catch (err) {
      const apiError = err as { error?: { status: number; detail: string } } | null;
      if (apiError?.error?.status === 409) {
        setEmailError('An account with this email already exists.');
      } else if (apiError?.error?.status === 429) {
        setEmailError('Too many attempts. Wait a moment and try again.');
      } else {
        setEmailError(apiError?.error?.detail || 'Registration failed. Please try again.');
      }
      showToast('Registration failed', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const passwordErrors = validatePassword(password);
  const enforcedError = passwordErrors[0]; // Only the first (12 chars) is enforced
  const advisoryErrors = passwordErrors.slice(1);

  return (
    <AuthLayout>
      <div className="bg-c-surface rounded-xl shadow-sm p-8 relative overflow-hidden">
        <div className="absolute top-0 left-0 right-0 h-1 bg-c-primary-700" />
        
        <div className="mb-6 text-center">
          <h1 className="font-display text-display text-c-primary-900 tracking-tight">Create your candidate account</h1>
          <p className="font-caption text-caption text-c-text-muted mt-1 max-w-xs mx-auto">
            Recruiter and administrator accounts are provisioned by your administrator.
          </p>
        </div>

        {registered && (
          <div className="mb-4 p-3 bg-success-50 border border-success-200 rounded-lg flex items-center gap-2 text-success-700">
            <span className="material-symbols-outlined text-[18px]">check_circle</span>
            <span className="font-body-md text-body-md">Account created for <strong>{registeredEmail}</strong>. Please sign in.</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <Input
            label="Email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={e => { setEmail(e.target.value); setEmailError(''); }}
            placeholder="name@example.com"
            required
            error={emailError}
          />

          <Input
            label="Password"
            autoComplete="new-password"
            value={password}
            onChange={e => { 
              setPassword(e.target.value); 
              setPasswordError('');
              if (confirmPassword) setConfirmError('');
            }}
            placeholder="Create a password"
            required
            showPasswordToggle
            error={passwordError}
            helperText="At least 12 characters. Mixed case, number, and symbol recommended."
          />

          {password && (
            <div className="space-y-1 ml-1">
              <div className="flex items-center gap-1.5 text-caption text-c-text-muted">
                <span className={`material-symbols-outlined text-[14px] ${enforcedError && password.length >= 12 ? 'text-success-600' : enforcedError ? 'text-danger-600' : 'text-c-text-subtle'}`}>
                  {password.length >= 12 ? 'check_circle' : 'radio_button_unchecked'}
                </span>
                <span className={password.length >= 12 ? 'text-success-600' : 'text-c-text-subtle'}>
                  At least 12 characters (enforced)
                </span>
              </div>
              {advisoryErrors.map((err, i) => (
                <div key={i} className="flex items-center gap-1.5 text-caption text-c-text-subtle">
                  <span className="material-symbols-outlined text-[14px]">
                    {password.length > 0 && !enforcedError ? 'check_circle' : 'radio_button_unchecked'}
                  </span>
                  <span>{err}</span>
                </div>
              ))}
            </div>
          )}

          <Input
            label="Confirm password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={e => { 
              setConfirmPassword(e.target.value); 
              setConfirmError('');
            }}
            placeholder="Confirm your password"
            required
            error={confirmError}
          />

          <Button type="submit" className="w-full" isLoading={isLoading}>
            <span>{isLoading ? 'Creating...' : 'Create account'}</span>
            <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
          </Button>
        </form>

        <div className="mt-6 w-full flex flex-col items-center text-center space-y-3">
          <Link to="/login" className="font-body-md text-body-md text-c-primary-700 hover:text-c-primary-900 transition-colors inline-flex items-center gap-1">
            <span>Already have an account? Sign in</span>
            <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
          </Link>
        </div>
      </div>
    </AuthLayout>
  );
}