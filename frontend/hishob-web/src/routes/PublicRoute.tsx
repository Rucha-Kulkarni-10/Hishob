/**
 * Route guard for public-only routes (login, register).
 *
 * - If auth is still initialising, shows nothing (prevents flash).
 * - If already authenticated, redirects to /dashboard.
 * - If unauthenticated, renders the child route.
 */

import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

export default function PublicRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return null;
  }

  return isAuthenticated ? <Navigate to="/dashboard" replace /> : <Outlet />;
}
