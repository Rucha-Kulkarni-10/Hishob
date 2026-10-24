import { type FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '@/components/common/FormField';
import BrandLogo from '@/components/common/BrandLogo';
import { useAuth } from '@/hooks/useAuth';
import { authService } from '@/services/authService';
import { getErrorMessage, isEmailNotVerifiedError } from '@/utils/errorUtils';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [apiError, setApiError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Email verification state
  const [isUnverified, setIsUnverified] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [resendStatus, setResendStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);

  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const interval = setInterval(() => {
      setCooldownSeconds((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [cooldownSeconds]);

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
    setIsUnverified(false);
    setResendStatus(null);

    if (!validate()) return;

    setIsLoading(true);
    try {
      await login({ email: email.trim(), password });
      navigate('/dashboard', { replace: true });
    } catch (err: unknown) {
      setApiError(getErrorMessage(err));
      if (isEmailNotVerifiedError(err)) {
        setIsUnverified(true);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendVerification = async () => {
    const targetEmail = email.trim();
    if (!targetEmail || isResending || cooldownSeconds > 0) return;

    setIsResending(true);
    setResendStatus(null);

    try {
      const response = await authService.resendVerification(targetEmail);
      setResendStatus({
        type: 'success',
        message: response.message || 'If an account exists with this email, a verification link has been sent.',
      });
      setCooldownSeconds(60);
    } catch (err: unknown) {
      setResendStatus({
        type: 'error',
        message: getErrorMessage(err),
      });
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        {/* Brand */}
        <div className="auth-card__brand">
          <BrandLogo variant="full" size="xl" priority alt="HISHOB — Split, Track, Settle Together" />
        </div>

        <h2 className="auth-card__heading">Sign in to your account</h2>

        {/* API-level error */}
        {apiError && (
          <div className="alert alert--error" role="alert">
            <p>{apiError}</p>
            {isUnverified && (
              <div style={{ marginTop: '0.75rem', paddingTop: '0.75rem', borderTop: '1px solid var(--color-error-border)' }}>
                <p style={{ fontSize: 'var(--font-size-xs)', marginBottom: '0.5rem', color: 'inherit' }}>
                  Need a new verification link?
                </p>
                <button
                  type="button"
                  onClick={handleResendVerification}
                  disabled={isResending || cooldownSeconds > 0}
                  className="btn btn--secondary"
                  style={{
                    fontSize: 'var(--font-size-xs)',
                    padding: '0.375rem 0.75rem',
                    width: 'auto',
                  }}
                  aria-busy={isResending}
                >
                  {isResending
                    ? 'Sending…'
                    : cooldownSeconds > 0
                    ? `Resend in ${cooldownSeconds}s`
                    : 'Resend verification email'}
                </button>
              </div>
            )}
          </div>
        )}

        {/* Resend feedback */}
        {resendStatus && (
          <div
            className={`alert alert--${resendStatus.type === 'success' ? 'success' : 'error'}`}
            role={resendStatus.type === 'success' ? 'status' : 'alert'}
          >
            {resendStatus.message}
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
