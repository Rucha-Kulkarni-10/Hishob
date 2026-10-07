import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '@/components/common/FormField';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage } from '@/utils/errorUtils';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [apiError, setApiError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // ── Client-side validation ─────────────────────────────────────────────────
  const validate = (): boolean => {
    const next: typeof errors = {};
    if (!email.trim()) {
      next.email = 'Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      next.email = 'Enter a valid email address.';
    }
    if (!password) {
      next.password = 'Password is required.';
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError('');

    if (!validate()) return;

    setIsLoading(true);
    try {
      await login({ email: email.trim(), password });
      navigate('/dashboard', { replace: true });
    } catch (err: unknown) {
      setApiError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        {/* Brand */}
        <div className="auth-card__brand">
          <span className="auth-card__logo" aria-hidden="true">₹</span>
          <h1 className="auth-card__title">HISHOB</h1>
          <p className="auth-card__subtitle">Shared expenses, simplified.</p>
        </div>

        <h2 className="auth-card__heading">Sign in to your account</h2>

        {/* API-level error */}
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <FormField
            id="email"
            label="Email address"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={errors.email}
            autoComplete="email"
            placeholder="you@example.com"
            required
            disabled={isLoading}
          />

          <FormField
            id="password"
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={errors.password}
            autoComplete="current-password"
            placeholder="••••••••"
            required
            disabled={isLoading}
          />

          <button
            type="submit"
            className="btn btn--primary btn--full"
            disabled={isLoading}
            aria-busy={isLoading}
          >
            {isLoading ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="auth-card__footer">
          Don't have an account?{' '}
          <Link to="/register" className="link">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}
