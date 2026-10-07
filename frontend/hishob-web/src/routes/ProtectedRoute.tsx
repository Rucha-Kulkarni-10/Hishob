/**
 * Route guard for authenticated routes.
 *
 * - If auth is still initialising, shows nothing (prevents flash).
 * - If unauthenticated, redirects to /login.
 * - If authenticated, renders the child route.
 */

import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

export default function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return null; // or a full-page spinner if preferred
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
