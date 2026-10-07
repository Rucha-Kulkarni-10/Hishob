import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '@/components/common/FormField';
import { useAuth } from '@/hooks/useAuth';
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
      // POST /api/auth/register returns AuthResponse with JWT →
      // AuthContext stores the token and updates user state immediately.
      await register({ name: name.trim(), email: email.trim(), password });
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
