/**
 * Authentication context provider.
 *
 * Provides the authenticated user state and auth actions to the entire app.
 * Initialises on mount by validating the stored JWT against the backend
 * (GET /api/auth/me). If the token is absent or rejected, the user is
 * treated as unauthenticated.
 *
 * The context object (AuthContext) and its type (AuthContextValue) live in
 * authContextTypes.ts to satisfy the react-refresh/only-export-components rule.
 * The useAuth hook lives in src/hooks/useAuth.ts for the same reason.
 */

import React, { useCallback, useEffect, useState } from 'react';
import { AuthContext } from './authContextTypes';
import { getCurrentUser, login as apiLogin, register as apiRegister } from '@/services/authService';
import type { LoginRequest, RegisterRequest, User } from '@/types/auth';
import { getToken, removeToken, setToken } from '@/utils/tokenStorage';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  /**
   * On mount, attempt to restore the session from the stored token.
   * Calls GET /api/auth/me — if it succeeds, the token is still valid.
   * If it fails (401 / network error), the token is cleared.
   */
  useEffect(() => {
    const initAuth = async () => {
      if (!getToken()) {
        setIsLoading(false);
        return;
      }

      try {
        const currentUser = await getCurrentUser();
        setUser(currentUser);
      } catch {
        // Token is stale or invalid — clear it silently
        removeToken();
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    void initAuth();
  }, []);

  const login = useCallback(async (data: LoginRequest) => {
    const response = await apiLogin(data);
    setToken(response.accessToken);
    setUser(response.user);
  }, []);

  const register = useCallback(async (data: RegisterRequest) => {
    // The HISHOB register endpoint returns an AuthResponse with a JWT,
    // so we authenticate the user immediately after registration.
    const response = await apiRegister(data);
    setToken(response.accessToken);
    setUser(response.user);
  }, []);

  const logout = useCallback(() => {
    removeToken();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: user !== null,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
