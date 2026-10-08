/**
 * AppLayout — wraps authenticated pages.
 *
 * Provides the top navigation bar with:
 *   - HISHOB brand
 *   - Nav links (Dashboard, Groups) — always via the nav wrapper for
 *     consistent spacing/active state.
 *   - User greeting + Logout button
 *
 * The main content area renders the active child route via <Outlet />.
 */

import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import NotificationBell from '@/components/notifications/NotificationBell';

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-layout">
      <header className="app-header">
        <div className="app-header__brand">
          <span className="app-header__logo">₹</span>
          <span className="app-header__name">HISHOB</span>
        </div>

        <nav className="app-header__nav" aria-label="Primary">
          <NavLink
            to="/dashboard"
            end
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Dashboard
          </NavLink>
          <NavLink
            to="/groups"
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Groups
          </NavLink>
          <NavLink
            to="/friends"
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Friends
          </NavLink>
          <NavLink
            to="/expenses"
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Expenses
          </NavLink>
          <NavLink
            to="/balances"
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Balances
          </NavLink>
          <NavLink
            to="/reports/personal"
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Reports
          </NavLink>
          <NavLink
            to="/notifications"
            className={({ isActive }) =>
              `app-header__nav-link${isActive ? ' is-active' : ''}`
            }
          >
            Notifications
          </NavLink>

        </nav>

        <div className="app-header__user">
          {user && (
            <span className="app-header__user-name" aria-label="Logged in as">
              {user.name}
            </span>
          )}
          <NotificationBell />
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={handleLogout}
            aria-label="Log out"
          >
            Log out
          </button>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
