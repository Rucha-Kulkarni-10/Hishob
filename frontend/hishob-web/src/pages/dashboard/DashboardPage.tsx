/**
 * DashboardPage — user home after login.
 *
 * Phase 15A: Full dashboard implementation
 * - Balance summary across all groups
 * - My Groups list
 * - Recent expenses from most recent group
 * - User ID panel for member invites
 *
 * Uses existing APIs only (no backend changes):
 * - GET /api/groups
 * - GET /api/groups/{id}/balances (for each group)
 * - GET /api/groups/{id}/expenses (most recent group)
 * - GET /api/groups/{id}/members (for name resolution)
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { listMyGroups, listMembers } from '@/services/groupService';
import { getGroupBalances } from '@/services/balanceService';
import { getGroupExpenses } from '@/services/expenseService';
import { getPersonalBalances } from '@/services/personalBalanceService';
import type { GroupResponse, MemberResponse } from '@/types/groups';
import type { GroupBalanceResponse } from '@/types/balances';
import type { ExpenseResponse } from '@/types/expenses';
import type { PersonalBalanceSummaryResponse } from '@/types/personalBalances';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import BalanceSummaryCards from '@/components/dashboard/BalanceSummaryCards';
import GroupsList from '@/components/groups/GroupsList';
import RecentExpensesList from '@/components/dashboard/RecentExpensesList';

interface BalanceSummary {
  youOwe: number;
  youAreOwed: number;
  netBalance: number;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [copied, setCopied] = useState(false);

  // Data state
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [balanceSummary, setBalanceSummary] = useState<BalanceSummary>({
    youOwe: 0,
    youAreOwed: 0,
    netBalance: 0,
  });
  const [personalSummary, setPersonalSummary] = useState<PersonalBalanceSummaryResponse | null>(null);
  const [recentExpenses, setRecentExpenses] = useState<ExpenseResponse[]>([]);
  const [recentGroupId, setRecentGroupId] = useState<string | null>(null);
  const [recentGroupName, setRecentGroupName] = useState<string | null>(null);
  const [memberNames, setMemberNames] = useState<Record<string, string>>({});

  // Loading states
  const [groupsLoading, setGroupsLoading] = useState(true);
  const [balanceLoading, setBalanceLoading] = useState(false);
  const [personalLoading, setPersonalLoading] = useState(false);
  const [expensesLoading, setExpensesLoading] = useState(false);

  // Error states
  const [groupsError, setGroupsError] = useState<string | null>(null);
  const [balanceError, setBalanceError] = useState<string | null>(null);
  const [expensesError, setExpensesError] = useState<string | null>(null);

  const handleCopyId = () => {
    if (!user?.id) return;
    navigator.clipboard.writeText(user.id).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }).catch(() => {
      // Fallback for browsers without clipboard API
      const el = document.createElement('textarea');
      el.value = user.id;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  // Load groups
  const loadGroups = useCallback(async () => {
    setGroupsLoading(true);
    setGroupsError(null);
    try {
      const fetchedGroups = await listMyGroups();
      setGroups(fetchedGroups);
    } catch (err) {
      setGroupsError(getErrorMessage(err));
    } finally {
      setGroupsLoading(false);
    }
  }, []);

  // Load balances for all groups and aggregate
  const loadBalanceSummary = useCallback(async (groupList: GroupResponse[]) => {
    if (groupList.length === 0 || !user?.id) {
      setBalanceSummary({ youOwe: 0, youAreOwed: 0, netBalance: 0 });
      return;
    }

    setBalanceLoading(true);
    setBalanceError(null);

    try {
      // Fetch balances for all groups in parallel
      const balancePromises = groupList.map(async (group) => {
        try {
          const balanceData: GroupBalanceResponse = await getGroupBalances(group.id);
          return { groupId: group.id, balances: balanceData.balances };
        } catch {
          // Continue if one group fails
          return { groupId: group.id, balances: [] };
        }
      });

      const allBalances = await Promise.all(balancePromises);

      // Aggregate user's balance across all groups
      let totalOwed = 0;
      let totalGets = 0;

      allBalances.forEach(({ balances }) => {
        const userBalance = balances.find((b) => b.userId === user.id);
        if (userBalance) {
          if (userBalance.netBalance < 0) {
            // User owes
            totalOwed += Math.abs(userBalance.netBalance);
          } else if (userBalance.netBalance > 0) {
            // User is owed
            totalGets += userBalance.netBalance;
          }
        }
      });

      setBalanceSummary({
        youOwe: totalOwed,
        youAreOwed: totalGets,
        netBalance: totalGets - totalOwed,
      });
    } catch (err) {
      setBalanceError(getErrorMessage(err));
    } finally {
      setBalanceLoading(false);
    }
  }, [user]);

  // Load recent expenses from most recently updated group
  const loadRecentExpenses = useCallback(async (groupList: GroupResponse[]) => {
    if (groupList.length === 0) {
      setRecentExpenses([]);
      return;
    }

    setExpensesLoading(true);
    setExpensesError(null);

    try {
      // Find most recently updated group
      const mostRecentGroup = [...groupList].sort(
        (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      )[0];

      setRecentGroupId(mostRecentGroup.id);
      setRecentGroupName(mostRecentGroup.name);

      // Fetch expenses from that group
      const expenses = await getGroupExpenses(mostRecentGroup.id);

      // Fetch members for name resolution
      try {
        const members: MemberResponse[] = await listMembers(mostRecentGroup.id);
        const names: Record<string, string> = {};
        members.forEach((m) => {
          if (user && m.userId === user.id) {
            names[m.userId] = m.name ? `${m.name} (You)` : 'You';
          } else {
            names[m.userId] = m.name || 'Unknown member';
          }
        });
        setMemberNames(names);
      } catch {
        // Continue without member names
        setMemberNames({});
      }

      setRecentExpenses(expenses.slice(0, 5));
    } catch (err) {
      setExpensesError(getErrorMessage(err));
    } finally {
      setExpensesLoading(false);
    }
  }, [user]);

  // Load personal balances
  const loadPersonalBalances = useCallback(async () => {
    setPersonalLoading(true);
    try {
      const data = await getPersonalBalances();
      setPersonalSummary(data);
    } catch {
      // Continue without breaking dashboard
    } finally {
      setPersonalLoading(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    queueMicrotask(() => {
      loadGroups();
      void loadPersonalBalances();
    });
  }, [loadGroups, loadPersonalBalances]);

  // Load balances and expenses when groups are loaded
  useEffect(() => {
    if (!groupsLoading && groups.length > 0) {
      queueMicrotask(() => {
        void loadBalanceSummary(groups);
        void loadRecentExpenses(groups);
      });
    }
  }, [groupsLoading, groups, loadBalanceSummary, loadRecentExpenses]);

  // Personal net balance aggregation
  const personalNet = useMemo(() => {
    if (!personalSummary?.balances) return 0;
    return personalSummary.balances.reduce((acc, b) => acc + (b.netBalance || 0), 0);
  }, [personalSummary]);

  // Empty state: no groups
  if (!groupsLoading && groups.length === 0 && !groupsError) {
    return (
      <div className="dashboard-page">
        <div className="dashboard-welcome">
          <h2 className="dashboard-welcome__heading">
            Welcome to HISHOB{user?.name ? `, ${user.name}` : ''}!
          </h2>
          <p className="dashboard-welcome__sub">
            You're signed in as <strong>{user?.email}</strong>.
          </p>
        </div>

        {/* Personal Finance Quick Access */}
        <section className="dashboard-section" aria-label="Personal Finance">
          <div className="page-header page-header--section">
            <div className="page-header__title">
              <h3 className="page-header__heading page-header__heading--sm">Personal Finance</h3>
              <p className="page-header__sub">Direct expense tracking with friends</p>
            </div>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <Link to="/expenses" className="btn btn--secondary btn--sm">
                Personal Expenses
              </Link>
              <Link to="/balances" className="btn btn--primary btn--sm">
                Balances & Settlements
              </Link>
            </div>
          </div>
        </section>

        <EmptyState
          icon="👥"
          heading="No groups yet"
          description="Create your first group to start tracking shared expenses with friends."
          action={
            <Link to="/groups" className="btn btn--primary">
              Create Your First Group
            </Link>
          }
        />

        {/* User ID panel */}
        {user?.id && (
          <div className="dashboard-userid">
            <p className="dashboard-userid__label">Your User ID</p>
            <div className="dashboard-userid__row">
              <code className="dashboard-userid__value" title={user.id}>
                {user.id}
              </code>
              <button
                type="button"
                className="btn btn--ghost btn--sm dashboard-userid__copy"
                onClick={handleCopyId}
                aria-label="Copy your User ID to clipboard"
              >
                {copied ? '✓ Copied' : 'Copy'}
              </button>
            </div>
            <p className="dashboard-userid__hint">
              Share this ID with a group owner so they can add you as a member.
            </p>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      {/* Welcome banner */}
      <header className="dashboard-welcome">
        <h2 className="dashboard-welcome__heading">
          Welcome back{user?.name ? `, ${user.name}` : ''}!
        </h2>
        <p className="dashboard-welcome__sub">
          You're signed in as <strong>{user?.email}</strong>.
        </p>
      </header>

      {/* Balance Summary Section */}
      <section className="dashboard-section" aria-label="Balance Summary">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">
            Your Balance Summary
          </h3>
          <p className="page-header__sub">Across all your groups</p>
        </div>
        <BalanceSummaryCards
          youOwe={balanceSummary.youOwe}
          youAreOwed={balanceSummary.youAreOwed}
          netBalance={balanceSummary.netBalance}
          isLoading={balanceLoading}
          hasError={!!balanceError}
        />
        {balanceError && (
          <p className="dashboard-section__error">
            Some balance data could not be loaded: {balanceError}
          </p>
        )}
      </section>

      {/* Personal Finance Quick Section */}
      <section className="dashboard-section" aria-label="Personal Finance">
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <h3 className="page-header__heading page-header__heading--sm">Personal Finance</h3>
            <p className="page-header__sub">Direct 1-on-1 expenses & balances with friends</p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <Link to="/expenses" className="btn btn--ghost btn--sm">
              Personal Expenses
            </Link>
            <Link to="/balances" className="btn btn--secondary btn--sm">
              View Balances
            </Link>
          </div>
        </div>

        <div
          className="balance-card"
          style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            padding: '1.25rem 1.5rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '1rem',
          }}
        >
          <div>
            <span className="balance-card__label">Personal Net Balance</span>
            <span
              className={`balance-card__amount ${
                personalNet > 0
                  ? 'balance-card__amount--gets'
                  : personalNet < 0
                  ? 'balance-card__amount--owe'
                  : ''
              }`}
              style={{ fontSize: '1.5rem', marginTop: '0.25rem' }}
            >
              {personalLoading ? 'Loading…' : personalNet > 0 ? `+₹${personalNet.toFixed(2)}` : personalNet < 0 ? `-₹${Math.abs(personalNet).toFixed(2)}` : '₹0.00'}
            </span>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <Link to="/expenses" className="btn btn--primary btn--sm">
              + Personal Expense
            </Link>
            <Link to="/balances" className="btn btn--secondary btn--sm">
              Simplify & Settle
            </Link>
          </div>
        </div>
      </section>

      {/* My Groups Section */}
      <section className="dashboard-section" aria-label="My Groups">
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <h3 className="page-header__heading page-header__heading--sm">My Groups</h3>
          </div>
          <Link to="/groups" className="btn btn--ghost btn--sm">
            View All
          </Link>
        </div>
        {groupsLoading ? (
          <LoadingState label="Loading groups..." height="sm" />
        ) : groupsError ? (
          <div className="alert alert--error" role="alert">
            {groupsError}
          </div>
        ) : (
          <GroupsList groups={groups} currentUser={user} />
        )}
      </section>

      {/* Recent Expenses Section */}
      <section className="dashboard-section" aria-label="Recent Expenses">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">
            Recent Expenses
          </h3>
          <p className="page-header__sub">Latest activity from your most recent group</p>
        </div>
        <RecentExpensesList
          expenses={recentExpenses}
          groupId={recentGroupId}
          groupName={recentGroupName}
          memberNames={memberNames}
          isLoading={expensesLoading}
          isEmpty={!expensesLoading && groups.length > 0 && recentExpenses.length === 0}
        />
        {expensesError && (
          <p className="dashboard-section__error">
            Could not load recent expenses: {expensesError}
          </p>
        )}
      </section>

      {/* User ID panel — for sharing with group owners during Add Member flow */}
      {user?.id && (
        <div className="dashboard-userid">
          <p className="dashboard-userid__label">Your User ID</p>
          <div className="dashboard-userid__row">
            <code className="dashboard-userid__value" title={user.id}>
              {user.id}
            </code>
            <button
              type="button"
              className="btn btn--ghost btn--sm dashboard-userid__copy"
              onClick={handleCopyId}
              aria-label="Copy your User ID to clipboard"
            >
              {copied ? '✓ Copied' : 'Copy'}
            </button>
          </div>
          <p className="dashboard-userid__hint">
            Share this ID with a group owner so they can add you as a member.
          </p>
        </div>
      )}
    </div>
  );
}
