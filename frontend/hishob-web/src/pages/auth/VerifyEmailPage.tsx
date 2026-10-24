import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import BrandLogo from '@/components/common/BrandLogo';
import FormField from '@/components/common/FormField';
import LoadingState from '@/components/common/LoadingState';
import { authService } from '@/services/authService';
import type { VerifyEmailResponse } from '@/types/auth';
import { getErrorMessage } from '@/utils/errorUtils';

type VerificationStatus = 'loading' | 'success' | 'error' | 'no-token';

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const hasToken = Boolean(token && token.trim());

  const [status, setStatus] = useState<VerificationStatus>(() => (hasToken ? 'loading' : 'no-token'));
  const [message, setMessage] = useState<string>(() =>
    hasToken
      ? ''
      : 'No verification token was provided in the link. Please check your email or request a new verification link below.'
  );
  const [isExpired, setIsExpired] = useState(false);

  // Recovery / Resend state
  const [resendEmail, setResendEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [isResending, setIsResending] = useState(false);
  const [resendStatus, setResendStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);

  // Guard against React StrictMode double invocation consuming one-time token
  const verifiedTokenRef = useRef<string | null>(null);

  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const interval = setInterval(() => {
      setCooldownSeconds((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [cooldownSeconds]);

  useEffect(() => {
    const trimmedToken = token?.trim();
    if (!trimmedToken) {
      return;
    }

    if (verifiedTokenRef.current === trimmedToken) {
      return;
    }
    verifiedTokenRef.current = trimmedToken;

    let isSubscribed = true;

    authService
      .verifyEmail(trimmedToken)
      .then((res: VerifyEmailResponse) => {
        if (!isSubscribed) return;
        setStatus('success');
        setMessage(res.message || 'Email verified successfully. You can now log in.');
      })
      .catch((err: unknown) => {
        if (!isSubscribed) return;
        setStatus('error');
        const errMsg = getErrorMessage(err);
        setMessage(errMsg);
        if (errMsg.toLowerCase().includes('expired')) {
          setIsExpired(true);
        }
      });

    return () => {
      isSubscribed = false;
    };
  }, [token]);

  const handleResendSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setEmailError('');
    setResendStatus(null);

    const email = resendEmail.trim();
    if (!email) {
      setEmailError('Email is required.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Enter a valid email address.');
      return;
    }

    if (cooldownSeconds > 0 || isResending) return;

    setIsResending(true);
    try {
      const response = await authService.resendVerification(email);
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
      <div className="auth-card" style={{ textAlign: 'center' }}>
        <div className="auth-card__brand">
          <BrandLogo variant="full" size="xl" priority alt="HISHOB — Split, Track, Settle Together" />
        </div>

        {/* ── 1. LOADING STATE ──────────────────────────────────────────────── */}
        {status === 'loading' && (
          <div style={{ padding: 'var(--space-6) 0' }}>
            <LoadingState height="md" label="Verifying your email address…" />
            <p style={{ marginTop: 'var(--space-4)', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)' }}>
              Please wait while we confirm your verification link.
            </p>
          </div>
        )}

        {/* ── 2. SUCCESS STATE ──────────────────────────────────────────────── */}
        {status === 'success' && (
          <div>
            <div
              style={{
                width: '4rem',
                height: '4rem',
                borderRadius: '50%',
                backgroundColor: 'var(--color-success-bg)',
                color: 'var(--color-success)',
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
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <polyline points="20 6 9 17 4 12" />
              </svg>
            </div>

            <h2 className="auth-card__heading" style={{ marginBottom: 'var(--space-2)' }}>
              Email verified!
            </h2>
            <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)', marginBottom: 'var(--space-6)' }}>
              {message}
            </p>

            <Link to="/login" className="btn btn--primary btn--full">
              Sign in to your account
            </Link>
          </div>
        )}

        {/* ── 3. ERROR / NO-TOKEN STATE ─────────────────────────────────────── */}
        {(status === 'error' || status === 'no-token') && (
          <div>
            <div
              style={{
                width: '4rem',
                height: '4rem',
                borderRadius: '50%',
                backgroundColor: 'var(--color-error-bg)',
                color: 'var(--color-error)',
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
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>

            <h2 className="auth-card__heading" style={{ marginBottom: 'var(--space-2)' }}>
              {isExpired ? 'Verification link expired' : 'Verification failed'}
            </h2>

            <div className="alert alert--error" role="alert" style={{ textAlign: 'left', marginBottom: 'var(--space-6)' }}>
              <p>{message}</p>
              {isExpired && (
                <p style={{ marginTop: 'var(--space-2)', fontSize: 'var(--font-size-xs)' }}>
                  Verification links are valid for 24 hours. You can request a fresh link below.
                </p>
              )}
            </div>

            {/* Resend recovery form */}
            <div
              style={{
                textAlign: 'left',
                borderTop: '1px solid var(--color-border)',
                paddingTop: 'var(--space-5)',
                marginBottom: 'var(--space-4)',
              }}
            >
              <h3
                style={{
                  fontSize: 'var(--font-size-sm)',
                  fontWeight: 600,
                  marginBottom: 'var(--space-1)',
                  color: 'var(--color-text)',
                }}
              >
                Need a new verification link?
              </h3>
              <p
                style={{
                  fontSize: 'var(--font-size-xs)',
                  color: 'var(--color-text-muted)',
                  marginBottom: 'var(--space-4)',
                }}
              >
                Enter your registered email address to receive a new link.
              </p>

              <form onSubmit={handleResendSubmit} noValidate>
                <FormField
                  id="resend-email"
                  label="Email address"
                  type="email"
                  value={resendEmail}
                  onChange={(e) => setResendEmail(e.target.value)}
                  error={emailError}
                  autoComplete="email"
                  placeholder="you@example.com"
                  required
                  disabled={isResending}
                />

                <button
                  type="submit"
                  className="btn btn--secondary btn--full"
                  disabled={isResending || cooldownSeconds > 0}
                  aria-busy={isResending}
                >
                  {isResending
                    ? 'Sending…'
                    : cooldownSeconds > 0
                    ? `Resend in ${cooldownSeconds}s`
                    : 'Resend verification email'}
                </button>
              </form>

              {resendStatus && (
                <div
                  className={`alert alert--${resendStatus.type === 'success' ? 'success' : 'error'}`}
                  role={resendStatus.type === 'success' ? 'status' : 'alert'}
                  style={{ marginTop: 'var(--space-4)', marginBottom: 0 }}
                >
                  {resendStatus.message}
                </div>
              )}
            </div>

            <p className="auth-card__footer" style={{ marginTop: 'var(--space-4)' }}>
              <Link to="/login" className="link">
                Return to sign in
              </Link>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
