/**
 * Authentication context type definitions and context object.
 *
 * Separated from AuthContext.tsx so that the Provider component file
 * satisfies the react-refresh/only-export-components ESLint rule
 * (component files must not mix component and non-component exports).
 */

import { createContext } from 'react';
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/auth';

export interface AuthContextValue {
  /** The currently authenticated user, or null if unauthenticated. */
  user: User | null;
  /** True while the initial auth check is running (prevents flash of login page). */
  isLoading: boolean;
  /** True if a user is authenticated. */
  isAuthenticated: boolean;
  /** Log in with email and password. Stores the JWT and updates context. */
  login: (data: LoginRequest) => Promise<void>;
  /** Register a new account. Stores the JWT if verified, returns AuthResponse. */
  register: (data: RegisterRequest) => Promise<AuthResponse>;
  /** Clear stored token and reset user state. */
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
