/**
 * ExpenseOverviewPage — the "View Details" target for the dashboard's expense total.
 *
 * Route: /expense-overview
 *
 * Group expenses and individual (1-on-1) expenses are two separate systems, keyed by
 * expenses.groupId being NOT NULL vs NULL. This page keeps them in two distinct sections
 * with their own labelled totals and their own navigation; the overall figure is the
 * disjoint sum of the two, so an expense is never counted twice — not when several of
 * the user's groups are involved, and not by membership alone.
 */

import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { getDashboardOverview } from '@/services/expenseService';
import { listMyGroups } from '@/services/groupService';
import { getPersonalExpenses } from '@/services/personalExpenseService';
import { getFriends } from '@/services/friendService';
import type { DashboardOverviewResponse, ScopeTotals } from '@/types/dashboard';
import type { GroupResponse } from '@/types/groups';
import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import RecentPersonalExpensesList from '@/components/dashboard/RecentPersonalExpensesList';

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function ScopeTotalsRow({ scope }: { scope: ScopeTotals }) {
  return (
    <div
      className="balance-cards-grid"
      style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))' }}
    >
      <div className="balance-card">
        <span className="balance-card__label">Expenses recorded</span>
        <span className="balance-card__amount">{scope.expenseCount}</span>
      </div>
      <div className="balance-card">
        <span className="balance-card__label">Total recorded spend</span>
        <span className="balance-card__amount">₹{formatAmount(scope.recordedSpend)}</span>
      </div>
      <div className="balance-card">
        <span className="balance-card__label">Your share (from splits)</span>
        <span className="balance-card__amount">₹{formatAmount(scope.yourShare)}</span>
      </div>
      <div className="balance-card">
        <span className="balance-card__label">You paid</span>
        <span className="balance-card__amount">₹{formatAmount(scope.youPaid)}</span>
      </div>
    </div>
  );
}

export default function ExpenseOverviewPage() {
  const { user } = useAuth();

  const [overview, setOverview] = useState<DashboardOverviewResponse | null>(null);
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [personalExpenses, setPersonalExpenses] = useState<PersonalExpenseResponse[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    const results = await Promise.allSettled([
      getDashboardOverview(),
      listMyGroups(),
      getPersonalExpenses(),
      getFriends(),
    ]);
    if (results[0].status === 'fulfilled') {
      setOverview(results[0].value);
    }
    if (results[1].status === 'fulfilled') {
      setGroups(results[1].value);
    }
    if (results[2].status === 'fulfilled') {
      setPersonalExpenses(results[2].value);
    }
    if (results[3].status === 'fulfilled') {
      setFriends(results[3].value);
    }
    const failed = results.find((r) => r.status === 'rejected');
    if (failed) {
      setError(getErrorMessage((failed as PromiseRejectedResult).reason));
    }
    setIsLoading(false);
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void load();
    });
  }, [load]);

  if (isLoading && !overview) {
    return <LoadingState label="Loading your expense overview…" height="lg" />;
  }

  return (
    <div className="group-details-page">
      <header className="page-header page-header--details">
        <div className="page-header__title">
          <h2 className="page-header__heading">Expense Overview</h2>
          <p className="page-header__sub">
            Everything you have recorded, split into group expenses and 1-on-1 expenses.
            Each expense belongs to exactly one of them and is counted once.
          </p>
        </div>
        <Link to="/friends" className="btn btn--ghost">
          ← Friends
        </Link>
      </header>

      {error && (
        <div className="alert alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <span>Some figures could not be loaded: {error}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void load()}
            style={{ marginLeft: '1rem' }}
          >
            Retry
          </button>
        </div>
      )}

      {/* Overall total — the disjoint sum of the two scopes below. */}
      <section className="dashboard-section" aria-label="Overall expense total">
        <div className="dashboard-summary-cards">
          <div className="dashboard-stat-card">
            <div className="dashboard-stat-card__label">Total recorded expenses</div>
            <div className="dashboard-stat-card__value">
              ₹{overview ? formatAmount(overview.combined.recordedSpend) : '—'}
            </div>
            <div className="dashboard-stat-card__subtitle">
              {overview
                ? `${overview.combined.expenseCount} expenses — ₹${formatAmount(overview.group.recordedSpend)} in groups + ₹${formatAmount(overview.personal.recordedSpend)} individually`
                : 'Totals unavailable'}
            </div>
          </div>
        </div>
        <p className="page-header__sub" style={{ marginTop: '0.5rem' }}>
          Recorded spend is the full amount of every expense you can see. Your share and the
          amount you paid are separate metrics and are never added to this total.
        </p>
      </section>

      {/* ── GROUP EXPENSES ── */}
      <section className="dashboard-section" aria-label="Group expenses">
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h3 className="page-header__heading page-header__heading--sm">Group Expenses</h3>
              <span className="badge badge--primary">Group</span>
            </div>
            <p className="page-header__sub">
              Totals across the {groups.length} {groups.length === 1 ? 'group' : 'groups'} you
              are a member of. Being in a group does not create a debt — your share comes from
              split rules only.
            </p>
          </div>
          <Link to="/groups" className="btn btn--secondary btn--sm">
            All Groups
          </Link>
        </div>

        {overview && <ScopeTotalsRow scope={overview.group} />}

        {groups.length === 0 ? (
          <EmptyState
            icon="👥"
            heading="No groups yet"
            description="Create a group to record shared expenses with housemates, teammates or friends on a trip."
            action={
              <Link to="/groups" className="btn btn--primary">
                + Create Group
              </Link>
            }
          />
        ) : (
          <div className="dashboard-friend-list" style={{ marginTop: '1rem' }}>
            {groups.map((g) => (
              <div
                key={g.id}
                className="dashboard-expense-item"
                style={{ alignItems: 'center', justifyContent: 'space-between' }}
              >
                <div className="dashboard-expense-item__main">
                  <div className="dashboard-expense-item__top">
                    <Link to={`/groups/${g.id}`} className="dashboard-expense-item__description">
                      {g.name}
                    </Link>
                    {user && g.createdBy === user.id && (
                      <span className="badge badge--primary badge--xs">Owner</span>
                    )}
                  </div>
                  <p className="dashboard-expense-item__meta">
                    {g.description || 'Group expenses, balances and reports are shown here only.'}
                  </p>
                </div>
                <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                  <Link to={`/groups/${g.id}/expenses`} className="btn btn--ghost btn--sm">
                    Expenses
                  </Link>
                  <Link to={`/groups/${g.id}/balances`} className="btn btn--ghost btn--sm">
                    Balances
                  </Link>
                  <Link to={`/groups/${g.id}/reports`} className="btn btn--ghost btn--sm">
                    Reports
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* ── INDIVIDUAL EXPENSES ── */}
      <section className="dashboard-section" aria-label="Individual expenses">
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h3 className="page-header__heading page-header__heading--sm">Individual Expenses</h3>
              <span className="badge badge--primary">1-on-1</span>
            </div>
            <p className="page-header__sub">
              Expenses you record directly with a friend. These have no group and never appear
              in a group total.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Link to="/expenses" className="btn btn--secondary btn--sm">
              All Individual Expenses
            </Link>
            <Link to="/balances" className="btn btn--ghost btn--sm">
              Friend Balances
            </Link>
            <Link to="/reports/personal" className="btn btn--ghost btn--sm">
              Reports
            </Link>
          </div>
        </div>

        {overview && <ScopeTotalsRow scope={overview.personal} />}

        <div style={{ marginTop: '1rem' }}>
          <RecentPersonalExpensesList
            expenses={personalExpenses}
            friends={friends}
            currentUserId={user?.id ?? null}
            hasError={!!error && personalExpenses.length === 0}
            errorMessage={error}
            onRetry={() => void load()}
          />
        </div>
      </section>
    </div>
  );
}
