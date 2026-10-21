/**
 * Authentication service.
 *
 * All auth-related API calls are defined here.
 * Page components must not make Axios calls directly.
 *
 * Endpoints (routed through API Gateway → Auth Service):
 *   POST /api/auth/register  → returns AuthResponse (201)
 *   POST /api/auth/login     → returns AuthResponse (200)
 *   GET  /api/auth/me        → returns User (200, requires JWT)
 */

import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  ResendVerificationResponse,
  User,
  VerifyEmailResponse,
} from '@/types/auth';
import api from './api';

/** Register a new user. Returns AuthResponse containing the JWT and user (or verification state). */
export async function register(data: RegisterRequest): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>('/api/auth/register', data);
  return response.data;
}

/** Log in with email and password. Returns AuthResponse containing the JWT and user. */
export async function login(data: LoginRequest): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>('/api/auth/login', data);
  return response.data;
}

/**
 * Fetch the current authenticated user from the backend.
 * Requires a valid JWT (attached automatically by the request interceptor).
 */
export async function getCurrentUser(): Promise<User> {
  const response = await api.get<User>('/api/auth/me');
  return response.data;
}

/**
 * Verify an email address using a raw verification token.
 * POST /api/auth/verify-email
 */
export async function verifyEmail(token: string): Promise<VerifyEmailResponse> {
  const response = await api.post<VerifyEmailResponse>('/api/auth/verify-email', { token });
  return response.data;
}

/**
 * Request resending a verification email for an account.
 * POST /api/auth/resend-verification
 */
export async function resendVerification(email: string): Promise<ResendVerificationResponse> {
  const response = await api.post<ResendVerificationResponse>('/api/auth/resend-verification', { email });
  return response.data;
}

export const authService = {
  register,
  login,
  getCurrentUser,
  verifyEmail,
  resendVerification,
};

export default authService;

