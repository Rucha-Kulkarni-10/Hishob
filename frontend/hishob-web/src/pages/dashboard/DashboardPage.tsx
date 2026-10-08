/**
 * DashboardPage — user home after login.
 *
 * Phase 19D: Comprehensive Dashboard Personal & Group Finance Integration
 * - Personal Finance 3-Stat Balance Summary (You Owe, You Are Owed, Net Balance)
 * - Recent Personal Expenses (latest 1-on-1 activity)
 * - Outstanding Friend Balances (top debtors/creditors with direction & navigation)
 * - Group Finance 3-Stat Balance Summary (across all groups)
 * - My Groups & Recent Group Expenses
 * - Compact Quick Actions bar (+ Personal Expense, + Create Group, Friends, Balances)
 * - Isolated loading and error states between Personal and Group domains
 * - Safe name resolution (no raw UUIDs or truncated IDs)
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { listMyGroups, listMembers } from '@/services/groupService';
import { getGroupBalances } from '@/services/balanceService';
import { getGroupExpenses } from '@/services/expenseService';
import { getPersonalBalances } from '@/services/personalBalanceService';
import { getPersonalExpenses } from '@/services/personalExpenseService';
import { getFriends } from '@/services/friendService';
import type { GroupResponse, MemberResponse } from '@/types/groups';
import type { GroupBalanceResponse } from '@/types/balances';
import type { ExpenseResponse } from '@/types/expenses';
import type { PersonalBalanceSummaryResponse } from '@/types/personalBalances';
import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import BalanceSummaryCards from '@/components/dashboard/BalanceSummaryCards';
import GroupsList from '@/components/groups/GroupsList';
import RecentExpensesList from '@/components/dashboard/RecentExpensesList';
import RecentPersonalExpensesList from '@/components/dashboard/RecentPersonalExpensesList';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function resolveFriendName(
  userId: string | undefined | null,
  currentUserId: string | undefined | null,
  friendsList: Friend[]
): string {
  if (!userId) return 'Unknown member';
  if (currentUserId && userId === currentUserId) return 'You';
  const friend = friendsList.find((f) => f.userId === userId);
  if (friend) {
    if (friend.name && friend.name.trim() && !UUID_REGEX.test(friend.name.trim())) {
      return friend.name.trim();
    }
    if (friend.email && friend.email.trim()) {
      return friend.email.trim();
    }
  }
  return 'Unknown member';
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

interface BalanceSummary {
  youOwe: number;
  youAreOwed: number;
  netBalance: number;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [copied, setCopied] = useState(false);

  // Group data state
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [groupBalanceSummary, setGroupBalanceSummary] = useState<BalanceSummary>({
    youOwe: 0,
    youAreOwed: 0,
    netBalance: 0,
  });
  const [recentGroupExpenses, setRecentGroupExpenses] = useState<ExpenseResponse[]>([]);
  const [recentGroupId, setRecentGroupId] = useState<string | null>(null);
  const [recentGroupName, setRecentGroupName] = useState<string | null>(null);
  const [groupMemberNames, setGroupMemberNames] = useState<Record<string, string>>({});

  // Personal data state
  const [personalSummary, setPersonalSummary] = useState<PersonalBalanceSummaryResponse | null>(null);
  const [personalExpenses, setPersonalExpenses] = useState<PersonalExpenseResponse[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);

  // Loading states
  const [groupsLoading, setGroupsLoading] = useState(true);
  const [groupBalanceLoading, setGroupBalanceLoading] = useState(false);
  const [groupExpensesLoading, setGroupExpensesLoading] = useState(false);
  const [personalLoading, setPersonalLoading] = useState(true);
  const [personalExpensesLoading, setPersonalExpensesLoading] = useState(true);

  // Error states
  const [groupsError, setGroupsError] = useState<string | null>(null);
  const [groupBalanceError, setGroupBalanceError] = useState<string | null>(null);
  const [groupExpensesError, setGroupExpensesError] = useState<string | null>(null);
  const [personalError, setPersonalError] = useState<string | null>(null);
  const [personalExpensesError, setPersonalExpensesError] = useState<string | null>(null);

  const handleCopyId = () => {
    if (!user?.id) return;
    navigator.clipboard.writeText(user.id).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }).catch(() => {
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

  // Load group balances for all groups
  const loadGroupBalances = useCallback(async (groupList: GroupResponse[]) => {
    if (groupList.length === 0 || !user?.id) {
      setGroupBalanceSummary({ youOwe: 0, youAreOwed: 0, netBalance: 0 });
      return;
    }

    setGroupBalanceLoading(true);
    setGroupBalanceError(null);

    try {
      const balancePromises = groupList.map(async (group) => {
        try {
          const balanceData: GroupBalanceResponse = await getGroupBalances(group.id);
          return { groupId: group.id, balances: balanceData.balances };
        } catch {
          return { groupId: group.id, balances: [] };
        }
      });

      const allBalances = await Promise.all(balancePromises);

      let totalOwed = 0;
      let totalGets = 0;

      allBalances.forEach(({ balances }) => {
        const userBalance = balances.find((b) => b.userId === user.id);
        if (userBalance) {
          if (userBalance.netBalance < 0) {
            totalOwed += Math.abs(userBalance.netBalance);
          } else if (userBalance.netBalance > 0) {
            totalGets += userBalance.netBalance;
          }
        }
      });

      setGroupBalanceSummary({
        youOwe: totalOwed,
        youAreOwed: totalGets,
        netBalance: totalGets - totalOwed,
      });
    } catch (err) {
      setGroupBalanceError(getErrorMessage(err));
    } finally {
      setGroupBalanceLoading(false);
    }
  }, [user]);

  // Load recent group expenses
  const loadRecentGroupExpenses = useCallback(async (groupList: GroupResponse[]) => {
    if (groupList.length === 0) {
      setRecentGroupExpenses([]);
      return;
    }

    setGroupExpensesLoading(true);
    setGroupExpensesError(null);

    try {
      const mostRecentGroup = [...groupList].sort(
        (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      )[0];

      setRecentGroupId(mostRecentGroup.id);
      setRecentGroupName(mostRecentGroup.name);

      const expenses = await getGroupExpenses(mostRecentGroup.id);

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
        setGroupMemberNames(names);
      } catch {
        setGroupMemberNames({});
      }

      setRecentGroupExpenses(expenses.slice(0, 5));
    } catch (err) {
      setGroupExpensesError(getErrorMessage(err));
    } finally {
      setGroupExpensesLoading(false);
    }
  }, [user]);

  // Load all initial independent data in parallel
  const loadInitialData = useCallback(async () => {
    setGroupsLoading(true);
    setPersonalLoading(true);
    setPersonalExpensesLoading(true);

    setGroupsError(null);
    setPersonalError(null);
    setPersonalExpensesError(null);

    try {
      const [groupsRes, personalBalRes, personalExpRes, friendsRes] = await Promise.allSettled([
        listMyGroups(),
        getPersonalBalances(),
        getPersonalExpenses(),
        getFriends(),
      ]);

      // Groups
      if (groupsRes.status === 'fulfilled') {
        setGroups(groupsRes.value);
        void loadGroupBalances(groupsRes.value);
        void loadRecentGroupExpenses(groupsRes.value);
      } else {
        setGroupsError(getErrorMessage(groupsRes.reason));
      }

      // Personal balances
      if (personalBalRes.status === 'fulfilled') {
        setPersonalSummary(personalBalRes.value);
      } else {
        setPersonalError(getErrorMessage(personalBalRes.reason));
      }

      // Personal expenses
      if (personalExpRes.status === 'fulfilled') {
        setPersonalExpenses(personalExpRes.value);
      } else {
        setPersonalExpensesError(getErrorMessage(personalExpRes.reason));
      }

      // Friends
      if (friendsRes.status === 'fulfilled') {
        setFriends(friendsRes.value);
      }
    } finally {
      setGroupsLoading(false);
      setPersonalLoading(false);
      setPersonalExpensesLoading(false);
    }
  }, [loadGroupBalances, loadRecentGroupExpenses]);

  useEffect(() => {
    queueMicrotask(() => {
      void loadInitialData();
    });
  }, [loadInitialData]);

  // Personal 3-stat summary derived authoritatively from current user's balance entry
  const { personalYouOwe, personalYouAreOwed, personalNet } = useMemo(() => {
    const myEntry = personalSummary?.balances.find((b) => b.userId === user?.id);
    const myNet = myEntry?.netBalance ?? 0;

    return {
      personalYouOwe: myNet < 0 ? Math.abs(myNet) : 0,
      personalYouAreOwed: myNet > 0 ? myNet : 0,
      personalNet: myNet,
    };
  }, [personalSummary, user?.id]);

  // Outstanding friend balances (excluding current user and settled friends)
  const outstandingFriendBalances = useMemo(() => {
    if (!personalSummary?.balances) return [];
    return personalSummary.balances
      .filter((b) => b.userId !== user?.id && b.netBalance !== 0)
      .sort((a, b) => Math.abs(b.netBalance) - Math.abs(a.netBalance))
      .slice(0, 5);
  }, [personalSummary, user?.id]);

  return (
    <div className="dashboard-page">
      {/* Welcome Banner */}
      <header className="dashboard-welcome">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h2 className="dashboard-welcome__heading">
              Welcome back{user?.name ? `, ${user.name}` : ''}!
            </h2>
            <p className="dashboard-welcome__sub">
              You're signed in as <strong>{user?.email}</strong>.
            </p>
          </div>

          {/* Quick Actions Bar */}
          <div className="dashboard-quick-actions">
            <Link to="/expenses" className="btn btn--primary btn--sm">
              + Personal Expense
            </Link>
            <Link to="/groups" className="btn btn--secondary btn--sm">
              + Group
            </Link>
            <Link to="/friends" className="btn btn--ghost btn--sm">
              Friends
            </Link>
            <Link to="/balances" className="btn btn--ghost btn--sm">
              Balances
            </Link>
          </div>
        </div>
      </header>

      {/* ══════════════════════════════════════════════════════════════
          SECTION 1 — PERSONAL FINANCE (1-on-1 with Friends)
          ══════════════════════════════════════════════════════════════ */}
      <section className="dashboard-section" aria-label="Personal Finance">
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h3 className="page-header__heading page-header__heading--sm">Personal Finance</h3>
              <span className="badge badge--primary">1-on-1</span>
            </div>
            <p className="page-header__sub">Direct expense tracking & balances with friends</p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Link to="/reports/personal" className="btn btn--ghost btn--sm">
              Reports
            </Link>
            <Link to="/expenses" className="btn btn--ghost btn--sm">
              All Expenses
            </Link>
            <Link to="/balances" className="btn btn--secondary btn--sm">
              Balances & Simplification
            </Link>
          </div>
        </div>

        {/* 3-Stat Personal Balance Summary */}
        <BalanceSummaryCards
          youOwe={personalYouOwe}
          youAreOwed={personalYouAreOwed}
          netBalance={personalNet}
          isLoading={personalLoading}
          hasError={!!personalError}
        />
        {personalError && (
          <p className="dashboard-section__error">
            Could not load personal balances: {personalError}
          </p>
        )}

        {/* Personal Activity & Outstanding Friend Balances Grid */}
        <div className="dashboard-two-col" style={{ marginTop: '1.25rem' }}>
          {/* Left: Recent Personal Expenses */}
          <div>
            <h4 style={{ fontSize: 'var(--font-size-sm)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--color-text-muted)', marginBottom: '0.75rem' }}>
              Recent Personal Expenses
            </h4>
            <RecentPersonalExpensesList
              expenses={personalExpenses}
              friends={friends}
              currentUserId={user?.id ?? null}
              isLoading={personalExpensesLoading}
              hasError={!!personalExpensesError}
              errorMessage={personalExpensesError}
              onRetry={() => void loadInitialData()}
            />
          </div>

          {/* Right: Outstanding Friend Balances */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <h4 style={{ fontSize: 'var(--font-size-sm)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--color-text-muted)', margin: 0 }}>
                Outstanding with Friends
              </h4>
              <Link to="/balances" className="dashboard-expenses__view-all" style={{ fontSize: 'var(--font-size-xs)' }}>
                View all →
              </Link>
            </div>

            <div className="dashboard-friends-card">
              {personalLoading ? (
                <div style={{ padding: 'var(--space-4)', textAlign: 'center', color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
                  Loading friend balances…
                </div>
              ) : outstandingFriendBalances.length === 0 ? (
                <div style={{ padding: 'var(--space-5)', textAlign: 'center', color: 'var(--color-text-muted)' }}>
                  <span style={{ fontSize: '1.5rem', display: 'block', marginBottom: '0.25rem' }}>🎉</span>
                  <p style={{ margin: 0, fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>You're all settled up!</p>
                  <p style={{ margin: '0.25rem 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-light)' }}>
                    No outstanding debts with friends.
                  </p>
                </div>
              ) : (
                <div className="dashboard-friend-list">
                  {outstandingFriendBalances.map((item) => {
                    const friendName = resolveFriendName(item.userId, user?.id, friends);
                    const initial = (friendName.charAt(0) || '?').toUpperCase();
                    const userOwesFriend = item.netBalance > 0;

                    return (
                      <div key={item.userId} className="dashboard-friend-item">
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', minWidth: 0 }}>
                          <div className="friend-balance-card__avatar" style={{ width: '2.25rem', height: '2.25rem', fontSize: 'var(--font-size-sm)' }}>
                            {initial}
                          </div>
                          <div style={{ minWidth: 0 }}>
                            <Link
                              to={`/friends/${item.userId}`}
                              style={{ fontWeight: 600, color: 'var(--color-text)', textDecoration: 'none', display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                            >
                              {friendName}
                            </Link>
                            <span style={{ fontSize: 'var(--font-size-xs)', color: userOwesFriend ? 'var(--color-owes)' : 'var(--color-gets)', fontWeight: 600 }}>
                              {userOwesFriend
                                ? `You owe ₹${formatAmount(item.netBalance)}`
                                : `Owes you ₹${formatAmount(item.netBalance)}`}
                            </span>
                          </div>
                        </div>

                        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexShrink: 0 }}>
                          {userOwesFriend ? (
                            <Link to="/balances" className="btn btn--primary btn--xs" style={{ fontSize: '0.75rem', padding: '0.2rem 0.6rem' }}>
                              Settle Up
                            </Link>
                          ) : (
                            <Link to={`/friends/${item.userId}`} className="btn btn--ghost btn--xs" style={{ fontSize: '0.75rem', padding: '0.2rem 0.6rem' }}>
                              View
                            </Link>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              <div style={{ padding: 'var(--space-3) var(--space-4)', borderTop: '1px solid var(--color-border)', background: 'var(--color-bg)', textAlign: 'right' }}>
                <Link to="/balances" className="btn btn--ghost btn--sm" style={{ fontSize: 'var(--font-size-xs)' }}>
                  View full breakdown & simplification →
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ══════════════════════════════════════════════════════════════
          SECTION 2 — GROUP FINANCE (Shared Group Expenses)
          ══════════════════════════════════════════════════════════════ */}
      <section className="dashboard-section" aria-label="Group Finance">
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h3 className="page-header__heading page-header__heading--sm">Group Finance</h3>
              <span className="badge badge--secondary">Groups</span>
            </div>
            <p className="page-header__sub">Across all your shared groups</p>
          </div>
          <Link to="/groups" className="btn btn--ghost btn--sm">
            View All Groups
          </Link>
        </div>

        {/* 3-Stat Group Balance Summary */}
        <BalanceSummaryCards
          youOwe={groupBalanceSummary.youOwe}
          youAreOwed={groupBalanceSummary.youAreOwed}
          netBalance={groupBalanceSummary.netBalance}
          isLoading={groupBalanceLoading}
          hasError={!!groupBalanceError}
        />
        {groupBalanceError && (
          <p className="dashboard-section__error">
            Some group balance data could not be loaded: {groupBalanceError}
          </p>
        )}

        {/* My Groups Section or Compact Empty Card */}
        {groupsLoading ? (
          <div style={{ marginTop: '1.25rem' }}>
            <LoadingState label="Loading groups..." height="sm" />
          </div>
        ) : groupsError ? (
          <div className="alert alert--error" role="alert" style={{ marginTop: '1.25rem' }}>
            {groupsError}
          </div>
        ) : groups.length === 0 ? (
          <div className="dashboard-group-empty-card" style={{ marginTop: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
              <div>
                <p style={{ margin: 0, fontWeight: 600, fontSize: 'var(--font-size-base)', color: 'var(--color-text)' }}>
                  No groups yet
                </p>
                <p style={{ margin: '0.25rem 0 0', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)' }}>
                  Create your first group to start sharing expenses for trips, roommates, or projects.
                </p>
              </div>
              <Link to="/groups" className="btn btn--primary btn--sm">
                Create First Group
              </Link>
            </div>
          </div>
        ) : (
          <div style={{ marginTop: '1.25rem' }}>
            <GroupsList groups={groups} currentUser={user} />
          </div>
        )}

        {/* Recent Group Expenses Section */}
        {groups.length > 0 && (
          <div style={{ marginTop: '1.5rem' }}>
            <div className="page-header page-header--section">
              <h4 style={{ fontSize: 'var(--font-size-sm)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--color-text-muted)', margin: 0 }}>
                Recent Group Expenses
              </h4>
              <p className="page-header__sub">Latest activity from your most recent group</p>
            </div>
            <RecentExpensesList
              expenses={recentGroupExpenses}
              groupId={recentGroupId}
              groupName={recentGroupName}
              memberNames={groupMemberNames}
              isLoading={groupExpensesLoading}
              isEmpty={!groupExpensesLoading && groups.length > 0 && recentGroupExpenses.length === 0}
            />
            {groupExpensesError && (
              <p className="dashboard-section__error">
                Could not load recent group expenses: {groupExpensesError}
              </p>
            )}
          </div>
        )}
      </section>

      {/* User ID Panel for invitations */}
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
            Share this ID with a friend or group owner so they can add you.
          </p>
        </div>
      )}
    </div>
  );
}

