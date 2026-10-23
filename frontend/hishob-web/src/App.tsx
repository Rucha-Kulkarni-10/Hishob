import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '@/components/layout/AppLayout';
import LoadingState from '@/components/common/LoadingState';
import { AuthProvider } from '@/context/AuthContext';
import ProtectedRoute from '@/routes/ProtectedRoute';
import PublicRoute from '@/routes/PublicRoute';

const DashboardPage = lazy(() => import('@/pages/dashboard/DashboardPage'));
const ExpenseOverviewPage = lazy(() => import('@/pages/dashboard/ExpenseOverviewPage'));
const GroupsPage = lazy(() => import('@/pages/groups/GroupsPage'));
const GroupDetailsPage = lazy(() => import('@/pages/groups/GroupDetailsPage'));
const GroupExpensesPage = lazy(() => import('@/pages/groups/GroupExpensesPage'));
const GroupBalancesPage = lazy(() => import('@/pages/groups/GroupBalancesPage'));
const GroupReportsPage = lazy(() => import('@/pages/groups/GroupReportsPage'));
const FriendsPage = lazy(() => import('@/pages/friends/FriendsPage'));
const FriendDetailPage = lazy(() => import('@/pages/friends/FriendDetailPage'));
const PersonalExpensesPage = lazy(() => import('@/pages/personal/PersonalExpensesPage'));
const PersonalBalancesPage = lazy(() => import('@/pages/personal/PersonalBalancesPage'));
const PersonalReportsPage = lazy(() => import('@/pages/personal/PersonalReportsPage'));
const NotificationsPage = lazy(() => import('@/pages/notifications/NotificationsPage'));
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'));
const VerifyEmailPage = lazy(() => import('@/pages/auth/VerifyEmailPage'));

export default function App() {
  return (
    <AuthProvider>
      <Suspense fallback={<LoadingState height="lg" label="Loading…" />}>
        <Routes>
        {/* Email verification: accessible without authentication checks so tokens are never discarded */}
        <Route path="/verify-email" element={<VerifyEmailPage />} />

        {/* Public routes: redirect authenticated users to dashboard */}
        <Route element={<PublicRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        {/* Protected routes: redirect unauthenticated users to login */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/expense-overview" element={<ExpenseOverviewPage />} />
            <Route path="/groups" element={<GroupsPage />} />
            <Route path="/groups/:id" element={<GroupDetailsPage />} />
            <Route path="/groups/:id/expenses" element={<GroupExpensesPage />} />
            <Route path="/groups/:id/balances" element={<GroupBalancesPage />} />
            <Route path="/groups/:id/reports" element={<GroupReportsPage />} />
            <Route path="/friends" element={<FriendsPage />} />
            <Route path="/friends/:friendId" element={<FriendDetailPage />} />
            <Route path="/expenses" element={<PersonalExpensesPage />} />
            <Route path="/balances" element={<PersonalBalancesPage />} />
            <Route path="/reports/personal" element={<PersonalReportsPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
        </Route>

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
      </Suspense>
    </AuthProvider>
  );
}
