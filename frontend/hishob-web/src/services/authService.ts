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

import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/auth';
import api from './api';

/** Register a new user. Returns AuthResponse containing the JWT and user. */
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
