/**
 * TypeScript types derived directly from the HISHOB Auth Service DTOs.
 *
 * Backend sources:
 *   AuthResponse.java   → AuthResponse
 *   UserResponse.java   → User
 *   RegisterRequest.java → RegisterRequest
 *   LoginRequest.java   → LoginRequest
 *   ErrorResponse.java  → ApiError
 */

/** Authenticated user as returned by the backend. */
export interface User {
  id: string;          // UUID
  name: string;
  email: string;
  createdAt: string;   // ISO-8601 Instant
}

/** POST /api/auth/register  and  POST /api/auth/login response. */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;   // always "Bearer"
  user: User;
}

/** POST /api/auth/register request body. */
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

/** POST /api/auth/login request body. */
export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Backend error shape (ErrorResponse.java).
 * validationErrors is present on 400 responses only.
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  validationErrors?: Record<string, string>;
}
