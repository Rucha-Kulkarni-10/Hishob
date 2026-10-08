/**
 * PersonalExpensesPage — dedicated page for personal (non-group) expenses.
 *
 * Route: /expenses
 *
 * Features:
 *   - Summary stats: Total personal expense count and total volume
 *   - Search by description (immediate, case-insensitive)
 *   - Friend filter (involved as payer or participant, using loaded friends)
 *   - Split type filter (All, Equal, Exact, Percentage)
 *   - Record date filter (All time, Today, This week, This month, Custom range)
 *   - Sort (Newest first, Oldest first, Highest amount, Lowest amount)
 *   - Filter reset and active count
 *   - Filtered empty state vs True empty state
 *   - "+ Add Expense" button
 *   - View detail modal (PersonalExpenseDetailModal)
 *   - Edit modal (EditPersonalExpenseModal) — creator only
 *   - Delete confirmation — creator only
 *   - Name resolution with "Unknown member" fallback (no raw UUIDs)
 *   - Pass loaded friends to Add/Edit modals to avoid duplicate requests
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { deletePersonalExpense, getPersonalExpenses } from '@/services/personalExpenseService';

import { getFriends } from '@/services/friendService';
import type { PersonalExpenseResponse, SplitType } from '@/types/personalExpenses';
import {
  EXPENSE_CATEGORIES,
  CATEGORY_METADATA,
} from '@/types/expenses';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import PersonalExpenseCard from '@/components/personal/PersonalExpenseCard';
import AddPersonalExpenseModal from '@/components/personal/AddPersonalExpenseModal';
import EditPersonalExpenseModal from '@/components/personal/EditPersonalExpenseModal';
import PersonalExpenseDetailModal from '@/components/personal/PersonalExpenseDetailModal';

type DateFilterOption = 'ALL' | 'TODAY' | 'THIS_WEEK' | 'THIS_MONTH' | 'CUSTOM';
type SortOption = 'NEWEST' | 'OLDEST' | 'HIGHEST_AMOUNT' | 'LOWEST_AMOUNT';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function safeDisplayName(name: string | null | undefined, email: string | null | undefined, isCurrentUser: boolean): string {
  if (isCurrentUser) {
    return name?.trim() ? `${name.trim()} (You)` : 'You';
  }
  if (name && name.trim() && !UUID_REGEX.test(name.trim())) {
    return name.trim();
  }
  if (email && email.trim() && !UUID_REGEX.test(email.trim())) {
    return email.trim();
  }
  return 'Friend';
}

function formatAmount(amount: number): string {
  return amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function matchesDateFilter(
  createdAtIso: string,
  filter: DateFilterOption,
  customStart: string,
  customEnd: string
): boolean {
  if (filter === 'ALL') return true;

  const date = new Date(createdAtIso);
  if (isNaN(date.getTime())) return true;

  const now = new Date();

  if (filter === 'TODAY') {
    return (
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate()
    );
  }

  if (filter === 'THIS_WEEK') {
    const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay(), 0, 0, 0, 0);
    const endOfWeek = new Date(startOfWeek.getTime() + 7 * 24 * 60 * 60 * 1000);
    return date >= startOfWeek && date <= endOfWeek;
  }

  if (filter === 'THIS_MONTH') {
    return (
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth()
    );
  }

  if (filter === 'CUSTOM') {
    if (customStart) {
      const start = new Date(customStart + 'T00:00:00');
      if (date < start) return false;
    }
    if (customEnd) {
      const end = new Date(customEnd + 'T23:59:59.999');
      if (date > end) return false;
    }
    return true;
  }

  return true;
}

export default function PersonalExpensesPage() {
  const { user } = useAuth();

  const [expenses, setExpenses] = useState<PersonalExpenseResponse[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Client-side Filter & Sort state
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFriendId, setSelectedFriendId] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [splitTypeFilter, setSplitTypeFilter] = useState<SplitType | 'ALL'>('ALL');
  const [dateFilter, setDateFilter] = useState<DateFilterOption>('ALL');
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');
  const [sortOption, setSortOption] = useState<SortOption>('NEWEST');

  // Modals state
  const [addOpen, setAddOpen] = useState(false);
  const [viewExpense, setViewExpense] = useState<PersonalExpenseResponse | null>(null);
  const [editExpense, setEditExpense] = useState<PersonalExpenseResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PersonalExpenseResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Build memberNames map from currentUser and friends
  const memberNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    if (user) {
      map[user.id] = safeDisplayName(user.name, user.email, true);
    }
    friends.forEach((f) => {
      map[f.userId] = safeDisplayName(f.name, f.email, false);
    });
    return map;
  }, [user, friends]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [expensesData, friendsData] = await Promise.all([
        getPersonalExpenses(),
        getFriends().catch(() => [] as Friend[]),
      ]);
      setExpenses(expensesData);
      setFriends(friendsData);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void loadData();
    });
  }, [loadData]);

  const handleCreated = (created: PersonalExpenseResponse) => {
    setExpenses((prev) => [created, ...prev]);
  };

  const handleUpdated = (updated: PersonalExpenseResponse) => {
    setExpenses((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
    if (viewExpense && viewExpense.id === updated.id) {
      setViewExpense(updated);
    }
    setEditExpense(null);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await deletePersonalExpense(deleteTarget.id);
      setExpenses((prev) => prev.filter((e) => e.id !== deleteTarget.id));
      if (viewExpense && viewExpense.id === deleteTarget.id) {
        setViewExpense(null);
      }
      setDeleteTarget(null);
    } catch (err) {
      setDeleteError(getErrorMessage(err));
    } finally {
      setIsDeleting(false);
    }
  };

  const totalSpent = useMemo(() => {
    return expenses.reduce((sum, e) => sum + (e.totalAmount || 0), 0);
  }, [expenses]);

  // Check if any filter is active
  const isFilterActive = useMemo(() => {
    return (
      searchQuery.trim().length > 0 ||
      selectedFriendId !== '' ||
      categoryFilter !== 'ALL' ||
      splitTypeFilter !== 'ALL' ||
      dateFilter !== 'ALL' ||
      customStartDate !== '' ||
      customEndDate !== '' ||
      sortOption !== 'NEWEST'
    );
  }, [
    searchQuery,
    selectedFriendId,
    categoryFilter,
    splitTypeFilter,
    dateFilter,
    customStartDate,
    customEndDate,
    sortOption,
  ]);

  const handleResetFilters = () => {
    setSearchQuery('');
    setSelectedFriendId('');
    setCategoryFilter('ALL');
    setSplitTypeFilter('ALL');
    setDateFilter('ALL');
    setCustomStartDate('');
    setCustomEndDate('');
    setSortOption('NEWEST');
  };

  // Filter and sort expenses client-side
  const filteredExpenses = useMemo(() => {
    const trimmedQuery = searchQuery.trim().toLowerCase();

    const filtered = expenses.filter((expense) => {
      // 1. Description search
      if (trimmedQuery && !expense.description.toLowerCase().includes(trimmedQuery)) {
        return false;
      }

      // 2. Friend filter (involved as payer or participant)
      if (selectedFriendId) {
        const isInvolved =
          expense.paidBy === selectedFriendId ||
          expense.splits.some((s) => s.userId === selectedFriendId);
        if (!isInvolved) return false;
      }

      // 3. Category filter
      if (categoryFilter !== 'ALL' && (expense.category || 'OTHER') !== categoryFilter) {
        return false;
      }

      // 4. Split type filter
      if (splitTypeFilter !== 'ALL' && expense.splitType !== splitTypeFilter) {
        return false;
      }

      // 5. Date filter (using createdAt)
      if (!matchesDateFilter(expense.createdAt, dateFilter, customStartDate, customEndDate)) {
        return false;
      }

      return true;
    });

    // 6. Sorting
    return [...filtered].sort((a, b) => {
      switch (sortOption) {
        case 'OLDEST':
          return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        case 'HIGHEST_AMOUNT':
          return (b.totalAmount || 0) - (a.totalAmount || 0);
        case 'LOWEST_AMOUNT':
          return (a.totalAmount || 0) - (b.totalAmount || 0);
        case 'NEWEST':
        default:
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      }
    });
  }, [
    expenses,
    searchQuery,
    selectedFriendId,
    categoryFilter,
    splitTypeFilter,
    dateFilter,
    customStartDate,
    customEndDate,
    sortOption,
  ]);

  return (
    <div className="group-details-page">
      {/* Page Header */}
      <header className="page-header page-header--details">
        <div className="page-header__title">
          <div className="group-details-title-row">
            <h2 className="page-header__heading">Personal Expenses</h2>
            <span className="badge badge--primary">Personal</span>
          </div>
          <p className="page-header__sub">
            Track and split expenses directly with individual friends outside of groups.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Link to="/reports/personal" className="btn btn--ghost">
            📊 View Reports
          </Link>
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setAddOpen(true)}
          >
            + Add Personal Expense
          </button>
        </div>
      </header>

      {/* Delete confirmation banner */}
      {deleteTarget && (
        <div className="expense-delete-confirm" role="alert" style={{ marginBottom: '1.5rem' }}>
          <p>
            Delete <strong>"{deleteTarget.description}"</strong>? This will remove the personal expense.
          </p>
          {deleteError && (
            <span className="form-field__error">{deleteError}</span>
          )}
          <div className="expense-delete-confirm__actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => {
                setDeleteTarget(null);
                setDeleteError(null);
              }}
              disabled={isDeleting}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn--danger btn--sm"
              onClick={handleDeleteConfirm}
              disabled={isDeleting}
              aria-busy={isDeleting}
            >
              {isDeleting ? 'Deleting…' : 'Yes, delete'}
            </button>
          </div>
        </div>
      )}

      {/* Summary stats */}
      {!isLoading && !error && expenses.length > 0 && (
        <section className="dashboard-section" aria-label="Personal expense summary" style={{ marginBottom: '1.5rem' }}>
          <div className="balance-cards-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
            <div className="balance-card">
              <span className="balance-card__label">Total Expenses</span>
              <span className="balance-card__amount">{expenses.length}</span>
            </div>
            <div className="balance-card">
              <span className="balance-card__label">Total Volume</span>
              <span className="balance-card__amount">₹{formatAmount(totalSpent)}</span>
            </div>
          </div>
        </section>
      )}

      {/* Filter and Search Bar Controls (when expenses exist) */}
      {!isLoading && !error && expenses.length > 0 && (
        <section
          className="report-filter-bar"
          aria-label="Personal expense filters"
          style={{ marginBottom: '1.5rem' }}
        >
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'center' }}>
            {/* Search Input */}
            <div style={{ flex: '1 1 220px' }}>
              <input
                id="personal-expense-search"
                type="search"
                className="form-field__input"
                placeholder="Search by description…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Search expenses by description"
              />
            </div>

            {/* Friend Filter */}
            <div style={{ flex: '1 1 150px' }}>
              <select
                id="personal-expense-friend-filter"
                className="form-field__input"
                value={selectedFriendId}
                onChange={(e) => setSelectedFriendId(e.target.value)}
                aria-label="Filter by friend"
              >
                <option value="">All people</option>
                {friends.map((f) => (
                  <option key={f.userId} value={f.userId}>
                    {memberNames[f.userId] || f.name || f.email || 'Friend'}
                  </option>
                ))}
              </select>
            </div>

            {/* Category Filter */}
            <div style={{ flex: '1 1 150px' }}>
              <select
                id="personal-expense-category-filter"
                className="form-field__input"
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
                aria-label="Filter by category"
              >
                <option value="ALL">All Categories</option>
                {EXPENSE_CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>
                    {CATEGORY_METADATA[cat].icon} {CATEGORY_METADATA[cat].label}
                  </option>
                ))}
              </select>
            </div>

            {/* Split Type Filter */}
            <div style={{ flex: '1 1 130px' }}>
              <select
                id="personal-expense-split-filter"
                className="form-field__input"
                value={splitTypeFilter}
                onChange={(e) => setSplitTypeFilter(e.target.value as SplitType | 'ALL')}
                aria-label="Filter by split type"
              >
                <option value="ALL">All split types</option>
                <option value="EQUAL">Equal</option>
                <option value="EXACT">Exact</option>
                <option value="PERCENTAGE">Percentage</option>
              </select>
            </div>

            {/* Date Filter */}
            <div style={{ flex: '1 1 130px' }}>
              <select
                id="personal-expense-date-filter"
                className="form-field__input"
                value={dateFilter}
                onChange={(e) => setDateFilter(e.target.value as DateFilterOption)}
                aria-label="Filter by record date"
              >
                <option value="ALL">All time</option>
                <option value="TODAY">Today</option>
                <option value="THIS_WEEK">This week</option>
                <option value="THIS_MONTH">This month</option>
                <option value="CUSTOM">Custom range</option>
              </select>
            </div>

            {/* Sort Order */}
            <div style={{ flex: '1 1 140px' }}>
              <select
                id="personal-expense-sort"
                className="form-field__input"
                value={sortOption}
                onChange={(e) => setSortOption(e.target.value as SortOption)}
                aria-label="Sort expenses"
              >
                <option value="NEWEST">Newest first</option>
                <option value="OLDEST">Oldest first</option>
                <option value="HIGHEST_AMOUNT">Highest amount</option>
                <option value="LOWEST_AMOUNT">Lowest amount</option>
              </select>
            </div>
          </div>

          {/* Custom Date Range pickers if CUSTOM selected */}
          {dateFilter === 'CUSTOM' && (
            <div
              className="report-filter-field-group"
              style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap', paddingTop: '0.25rem' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <label htmlFor="p-custom-start" style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                  From:
                </label>
                <input
                  id="p-custom-start"
                  type="date"
                  className="form-field__input"
                  value={customStartDate}
                  onChange={(e) => setCustomStartDate(e.target.value)}
                  style={{ width: 'auto', padding: '0.35rem 0.6rem' }}
                />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <label htmlFor="p-custom-end" style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                  To:
                </label>
                <input
                  id="p-custom-end"
                  type="date"
                  className="form-field__input"
                  value={customEndDate}
                  onChange={(e) => setCustomEndDate(e.target.value)}
                  style={{ width: 'auto', padding: '0.35rem 0.6rem' }}
                />
              </div>
            </div>
          )}

          {/* Results count & Clear filters action */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontSize: '0.875rem',
              color: 'var(--color-text-muted)',
              borderTop: '1px solid var(--color-border)',
              paddingTop: '0.5rem',
              marginTop: '0.25rem',
            }}
          >
            <span>
              {isFilterActive
                ? `${filteredExpenses.length} of ${expenses.length} expense${expenses.length !== 1 ? 's' : ''}`
                : `${expenses.length} expense${expenses.length !== 1 ? 's' : ''}`}
            </span>
            {isFilterActive && (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={handleResetFilters}
                style={{ fontSize: '0.8rem', padding: '0.2rem 0.6rem' }}
              >
                Clear filters
              </button>
            )}
          </div>
        </section>
      )}

      {/* Loading state */}
      {isLoading && (
        <LoadingState label="Loading personal expenses…" height="lg" />
      )}

      {/* Error state */}
      {!isLoading && error && (
        <div className="alert alert--error" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void loadData()}
            style={{ marginLeft: '1rem' }}
          >
            Retry
          </button>
        </div>
      )}

      {/* True Empty state: user has zero personal expenses */}
      {!isLoading && !error && expenses.length === 0 && (
        <EmptyState
          icon="💸"
          heading="No personal expenses yet"
          description="You don't have any personal expenses yet. Split dinner, rent, or utilities directly with your friends."
          action={
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => setAddOpen(true)}
            >
              Add Your First Expense
            </button>
          }
        />
      )}

      {/* Filtered Empty state: expenses exist but current filters return zero */}
      {!isLoading && !error && expenses.length > 0 && filteredExpenses.length === 0 && (
        <EmptyState
          icon="🔍"
          heading="No expenses match your filters"
          description="Try changing your search keywords, friend selection, split type, or date range to find what you're looking for."
          action={
            <button
              type="button"
              className="btn btn--secondary"
              onClick={handleResetFilters}
            >
              Clear filters
            </button>
          }
        />
      )}

      {/* Filtered Expenses list */}
      {!isLoading && !error && filteredExpenses.length > 0 && (
        <section className="group-details-section" aria-label="Personal expenses list">
          <div className="expense-list">
            {filteredExpenses.map((expense) => (
              <PersonalExpenseCard
                key={expense.id}
                expense={expense}
                memberNames={memberNames}
                currentUserId={user?.id ?? null}
                onView={setViewExpense}
                onEdit={setEditExpense}
                onDelete={setDeleteTarget}
              />
            ))}
          </div>
        </section>
      )}

      {/* Add Modal */}
      {addOpen && (
        <AddPersonalExpenseModal
          isOpen={addOpen}
          onClose={() => setAddOpen(false)}
          onCreated={handleCreated}
          initialFriends={friends}
        />
      )}

      {/* Detail Modal */}
      {viewExpense && (
        <PersonalExpenseDetailModal
          isOpen={!!viewExpense}
          onClose={() => setViewExpense(null)}
          expense={viewExpense}
          memberNames={memberNames}
          currentUserId={user?.id ?? null}
          onEdit={(exp) => {
            setViewExpense(null);
            setEditExpense(exp);
          }}
          onDelete={(exp) => {
            setViewExpense(null);
            setDeleteTarget(exp);
          }}
        />
      )}

      {/* Edit Modal */}
      {editExpense && (
        <EditPersonalExpenseModal
          isOpen={!!editExpense}
          onClose={() => setEditExpense(null)}
          expense={editExpense}
          currentUserId={user?.id ?? null}
          onUpdated={handleUpdated}
          initialFriends={friends}
        />
      )}
    </div>
  );
}
