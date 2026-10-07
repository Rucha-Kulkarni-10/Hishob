import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '@/components/layout/AppLayout';
import { AuthProvider } from '@/context/AuthContext';
import DashboardPage from '@/pages/dashboard/DashboardPage';
import GroupsPage from '@/pages/groups/GroupsPage';
import GroupDetailsPage from '@/pages/groups/GroupDetailsPage';
import GroupExpensesPage from '@/pages/groups/GroupExpensesPage';
import GroupBalancesPage from '@/pages/groups/GroupBalancesPage';
import GroupReportsPage from '@/pages/groups/GroupReportsPage';
import FriendsPage from '@/pages/friends/FriendsPage';
import FriendDetailPage from '@/pages/friends/FriendDetailPage';
import PersonalExpensesPage from '@/pages/personal/PersonalExpensesPage';
import PersonalBalancesPage from '@/pages/personal/PersonalBalancesPage';
import NotificationsPage from '@/pages/notifications/NotificationsPage';
import LoginPage from '@/pages/auth/LoginPage';
import RegisterPage from '@/pages/auth/RegisterPage';
import ProtectedRoute from '@/routes/ProtectedRoute';
import PublicRoute from '@/routes/PublicRoute';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public routes: redirect authenticated users to dashboard */}
        <Route element={<PublicRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        {/* Protected routes: redirect unauthenticated users to login */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/groups" element={<GroupsPage />} />
            <Route path="/groups/:id" element={<GroupDetailsPage />} />
            <Route path="/groups/:id/expenses" element={<GroupExpensesPage />} />
            <Route path="/groups/:id/balances" element={<GroupBalancesPage />} />
            <Route path="/groups/:id/reports" element={<GroupReportsPage />} />
            <Route path="/friends" element={<FriendsPage />} />
            <Route path="/friends/:friendId" element={<FriendDetailPage />} />
            <Route path="/expenses" element={<PersonalExpensesPage />} />
            <Route path="/balances" element={<PersonalBalancesPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
        </Route>

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  );
}
