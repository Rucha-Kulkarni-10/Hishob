/**
 * PersonalExpensesPage — dedicated page for personal (non-group) expenses.
 *
 * Route: /expenses
 *
 * Features:
 *   - Summary stats: Total personal expense count and total spending
 *   - "+ Add Expense" button
 *   - Personal expenses list (newest first)
 *   - View detail modal (PersonalExpenseDetailModal)
 *   - Edit modal (EditPersonalExpenseModal) — creator only
 *   - Delete confirmation — creator only
 *   - Name resolution using user & friends data
 *   - Loading, Empty, Error, and Retry states
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { deletePersonalExpense, getPersonalExpenses } from '@/services/personalExpenseService';
import { getFriends } from '@/services/friendService';
import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import PersonalExpenseCard from '@/components/personal/PersonalExpenseCard';
import AddPersonalExpenseModal from '@/components/personal/AddPersonalExpenseModal';
import EditPersonalExpenseModal from '@/components/personal/EditPersonalExpenseModal';
import PersonalExpenseDetailModal from '@/components/personal/PersonalExpenseDetailModal';

function formatAmount(amount: number): string {
  return amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function PersonalExpensesPage() {
  const { user } = useAuth();

  const [expenses, setExpenses] = useState<PersonalExpenseResponse[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
      map[user.id] = user.name ? `${user.name} (You)` : 'You';
    }
    friends.forEach((f) => {
      map[f.userId] = f.name || f.email || 'Friend';
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
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => setAddOpen(true)}
        >
          + Add Personal Expense
        </button>
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

      {/* Empty state */}
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

      {/* Expenses list */}
      {!isLoading && !error && expenses.length > 0 && (
        <section className="group-details-section" aria-label="Personal expenses list">
          <div className="expense-list">
            {expenses.map((expense) => (
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
        />
      )}
    </div>
  );
}
