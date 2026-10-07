/**
 * useAuth — convenience hook to access the authentication context.
 * Separated from AuthContext.tsx to satisfy the react-refresh/only-export-components rule.
 * The context object (AuthContext) and its type (AuthContextValue) live in authContextTypes.ts.
 */

import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from '@/context/authContextTypes';

export type { AuthContextValue };

/** Returns the auth context. Must be used inside <AuthProvider>. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
