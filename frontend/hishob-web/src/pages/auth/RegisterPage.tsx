import { type FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '@/components/common/FormField';
import BrandLogo from '@/components/common/BrandLogo';
import { useAuth } from '@/hooks/useAuth';
import { resendVerification } from '@/services/authService';
import { getErrorMessage } from '@/utils/errorUtils';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<{
    name?: string;
    email?: string;
    password?: string;
    confirmPassword?: string;
  }>({});
  const [apiError, setApiError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Email verification state
  const [isVerificationRequired, setIsVerificationRequired] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [resendStatus, setResendStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [isResending, setIsResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  // Cooldown countdown timer
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  // ── Client-side validation ─────────────────────────────────────────────────
  // Rules mirror the backend RegisterRequest constraints:
  //   name:     required, 2–100 chars
  //   email:    required, valid format, max 150 chars
  //   password: required, min 6 chars
  const validate = (): boolean => {
    const next: typeof errors = {};

    const trimmedName = name.trim();
    if (!trimmedName) {
      next.name = 'Name is required.';
    } else if (trimmedName.length < 2) {
      next.name = 'Name must be at least 2 characters.';
    } else if (trimmedName.length > 100) {
      next.name = 'Name cannot exceed 100 characters.';
    }

    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      next.email = 'Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      next.email = 'Enter a valid email address.';
    } else if (trimmedEmail.length > 150) {
      next.email = 'Email cannot exceed 150 characters.';
    }

    if (!password) {
      next.password = 'Password is required.';
    } else if (password.length < 6) {
      next.password = 'Password must be at least 6 characters.';
    }

    if (!confirmPassword) {
      next.confirmPassword = 'Please confirm your password.';
    } else if (password !== confirmPassword) {
      next.confirmPassword = 'Passwords do not match.';
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
      const response = await register({ name: name.trim(), email: email.trim(), password });
      if (response.emailVerificationRequired) {
        setRegisteredEmail(email.trim());
        setIsVerificationRequired(true);
      } else {
        navigate('/dashboard', { replace: true });
      }
    } catch (err: unknown) {
      setApiError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    if (isResending || cooldown > 0 || !registeredEmail) return;

    setIsResending(true);
    setResendStatus(null);
    try {
      const result = await resendVerification(registeredEmail);
      setResendStatus({
        type: 'success',
        message: result.message || 'If an account with that email exists, a verification email has been sent.',
      });
      setCooldown(60);
    } catch (err: unknown) {
      setResendStatus({
        type: 'error',
        message: getErrorMessage(err),
      });
    } finally {
      setIsResending(false);
    }
  };

  if (isVerificationRequired) {
    return (
      <div className="auth-page">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <div className="auth-card__brand">
            <BrandLogo variant="full" size="xl" priority alt="HISHOB — Split, Track, Settle Together" />
          </div>

          <div
            style={{
              width: '4rem',
              height: '4rem',
              borderRadius: '50%',
              backgroundColor: 'var(--color-primary-light)',
              color: 'var(--color-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto var(--space-4)',
            }}
          >
            <svg
              width="32"
              height="32"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <rect width="20" height="16" x="2" y="4" rx="2" />
              <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
            </svg>
          </div>

          <h2 className="auth-card__heading" style={{ marginBottom: 'var(--space-2)' }}>
            Check your email
          </h2>

          <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--space-4)', lineHeight: 1.5 }}>
            We've sent a verification link to{' '}
            <strong style={{ color: 'var(--color-text)', wordBreak: 'break-all' }}>{registeredEmail}</strong>.
            Please verify your email address to complete your registration and activate your account.
          </p>

          <p style={{ color: 'var(--color-text-light)', fontSize: 'var(--font-size-xs)', marginBottom: 'var(--space-6)' }}>
            The verification link is valid for 24 hours.
          </p>

          {resendStatus && (
            <div
              className={`alert alert--${resendStatus.type === 'success' ? 'success' : 'error'}`}
              role="alert"
              style={{ textAlign: 'left', marginBottom: 'var(--space-4)' }}
            >
              {resendStatus.message}
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <Link to="/login" className="btn btn--primary btn--full">
              Proceed to Sign in
            </Link>

            <button
              type="button"
              onClick={handleResend}
              className="btn btn--ghost btn--full"
              disabled={isResending || cooldown > 0}
              aria-busy={isResending}
            >
              {isResending
                ? 'Sending verification link…'
                : cooldown > 0
                ? `Resend available in ${cooldown}s`
                : 'Resend verification email'}
            </button>
          </div>

          <p className="auth-card__footer" style={{ marginTop: 'var(--space-6)' }}>
            Entered the wrong email?{' '}
            <button
              type="button"
              onClick={() => {
                setIsVerificationRequired(false);
                setPassword('');
                setConfirmPassword('');
                setResendStatus(null);
              }}
              className="link"
              style={{ background: 'none', border: 'none', padding: 0, font: 'inherit', cursor: 'pointer' }}
            >
              Register with a different address
            </button>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        {/* Brand */}
        <div className="auth-card__brand">
          <BrandLogo variant="full" size="xl" priority alt="HISHOB — Split, Track, Settle Together" />
        </div>

        <h2 className="auth-card__heading">Create your account</h2>

        {/* API-level error */}
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <FormField
            id="name"
            label="Full name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={errors.name}
            autoComplete="name"
            placeholder="Aarav Shah"
            required
            disabled={isLoading}
          />

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
            autoComplete="new-password"
            placeholder="At least 6 characters"
            required
            disabled={isLoading}
          />

          <FormField
            id="confirmPassword"
            label="Confirm password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            error={errors.confirmPassword}
            autoComplete="new-password"
            placeholder="Repeat your password"
            required
            disabled={isLoading}
          />

          <button
            type="submit"
            className="btn btn--primary btn--full"
            disabled={isLoading}
            aria-busy={isLoading}
          >
            {isLoading ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="auth-card__footer">
          Already have an account?{' '}
          <Link to="/login" className="link">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
