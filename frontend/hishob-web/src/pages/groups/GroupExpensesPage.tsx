/**
 * GroupExpensesPage — /groups/:id/expenses
 *
 * Loads in parallel:
 *   - GET /api/groups/:id          (group detail + name)
 *   - GET /api/groups/:id/members  (members for name mapping + payer selector)
 *   - GET /api/groups/:id/expenses (expense list)
 *
 * Features:
 *   - Breadcrumb back to group
 *   - "+ Add expense" button
 *   - Expense list with loading/empty/error states
 *   - View detail modal (ExpenseDetailModal)
 *   - Edit modal (EditExpenseModal) — creator only
 *   - Delete with confirmation — creator only
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroupDetail, listMembers } from '@/services/groupService';
import { deleteExpense, getGroupExpenses } from '@/services/expenseService';
import type { GroupResponse, MemberResponse } from '@/types/groups';
import type { ExpenseResponse } from '@/types/expenses';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import ExpenseList from '@/components/expenses/ExpenseList';
import AddExpenseModal from '@/components/expenses/AddExpenseModal';
import EditExpenseModal from '@/components/expenses/EditExpenseModal';
import ExpenseDetailModal from '@/components/expenses/ExpenseDetailModal';

export default function GroupExpensesPage() {
  const { id: groupId } = useParams<{ id: string }>();
  const { user } = useAuth();

  // ── State ──────────────────────────────────────────────────────────────────
  const [group, setGroup] = useState<GroupResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [expenses, setExpenses] = useState<ExpenseResponse[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [is404, setIs404] = useState(false);
  const [is403, setIs403] = useState(false);

  const [expenseLoading, setExpenseLoading] = useState(false);
  const [expenseError, setExpenseError] = useState<string | null>(null);

  const [addOpen, setAddOpen] = useState(false);

  const [viewExpense, setViewExpense] = useState<ExpenseResponse | null>(null);
  const [editExpense, setEditExpense] = useState<ExpenseResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ExpenseResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // ── Name mapping ───────────────────────────────────────────────────────────
  const memberNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const m of members) {
      const name = m.name || 'Unknown member';
      if (user && m.userId === user.id) {
        map[m.userId] = `${name} (You)`;
      } else {
        map[m.userId] = name;
      }
    }
    return map;
  }, [members, user]);

  // ── Data loading ───────────────────────────────────────────────────────────
  const loadPage = useCallback(async (gId: string) => {
    await Promise.resolve();
    setIsLoading(true);
    setPageError(null);
    setIs404(false);
    setIs403(false);
    try {
      const [g, ms] = await Promise.all([getGroupDetail(gId), listMembers(gId)]);
      setGroup(g);
      setMembers(ms);
    } catch (err: unknown) {
      const { default: axios } = await import('axios');
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        if (status === 404) setIs404(true);
        else if (status === 403) setIs403(true);
        else setPageError(getErrorMessage(err));
      } else {
        setPageError(getErrorMessage(err));
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadExpenses = useCallback(async (gId: string) => {
    await Promise.resolve();
    setExpenseLoading(true);
    setExpenseError(null);
    try {
      const data = await getGroupExpenses(gId);
      setExpenses(data);
    } catch (err) {
      setExpenseError(getErrorMessage(err));
    } finally {
      setExpenseLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!groupId) return;
    queueMicrotask(() => {
      void loadPage(groupId);
    });
  }, [groupId, loadPage]);

  useEffect(() => {
    if (!groupId || isLoading || is404 || is403 || pageError) return;
    queueMicrotask(() => {
      void loadExpenses(groupId);
    });
  }, [groupId, isLoading, is404, is403, pageError, loadExpenses]);

  // ── Expense event handlers ─────────────────────────────────────────────────
  const handleCreated = (expense: ExpenseResponse) => {
    setExpenses((prev) => [expense, ...prev]);
  };

  const handleUpdated = (updated: ExpenseResponse) => {
    setExpenses((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
    setEditExpense(null);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await deleteExpense(deleteTarget.id);
      setExpenses((prev) => prev.filter((e) => e.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch (err) {
      setDeleteError(getErrorMessage(err));
    } finally {
      setIsDeleting(false);
    }
  };

  // ── Render guards ──────────────────────────────────────────────────────────
  if (isLoading) {
    return <LoadingState label="Loading group…" height="lg" />;
  }

  if (is404) {
    return (
      <EmptyState
        icon="🔍"
        heading="Group not found"
        description="This group may have been deleted or you followed a bad link."
        action={<Link to="/groups" className="btn btn--primary">Back to My Groups</Link>}
      />
    );
  }

  if (is403) {
    return (
      <EmptyState
        icon="🔒"
        heading="You can't view this group"
        description="You must be a member of this group to see its expenses."
        action={<Link to="/groups" className="btn btn--primary">Back to My Groups</Link>}
      />
    );
  }

  if (pageError) {
    return (
      <div className="alert alert--error groups-page__alert" role="alert">
        <span>{pageError}</span>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => groupId && loadPage(groupId)}
        >
          Retry
        </button>
      </div>
    );
  }

  if (!group) return null;

  // ── Main render ────────────────────────────────────────────────────────────
  return (
    <div className="group-details-page">
      {/* Header */}
      <header className="page-header page-header--details">
        <div className="page-header__title">
          <nav aria-label="Breadcrumb" className="group-details-breadcrumb">
            <Link to="/groups">My Groups</Link>
            <span aria-hidden="true"> / </span>
            <Link to={`/groups/${groupId}`}>{group.name}</Link>
            <span aria-hidden="true"> / </span>
            <span>Expenses</span>
          </nav>
          <div className="group-details-title-row">
            <h2 className="page-header__heading">Expenses</h2>
          </div>
          <p className="group-details-meta">
            {group.name} · {expenses.length} expense{expenses.length !== 1 ? 's' : ''}
          </p>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => setAddOpen(true)}
        >
          + Add expense
        </button>
      </header>

      {/* Delete confirmation banner */}
      {deleteTarget && (
        <div className="expense-delete-confirm" role="alert">
          <p>
            Delete <strong>"{deleteTarget.description}"</strong>? This cannot be undone.
          </p>
          {deleteError && (
            <span className="form-field__error">{deleteError}</span>
          )}
          <div className="expense-delete-confirm__actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => { setDeleteTarget(null); setDeleteError(null); }}
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

      {/* Expense list section */}
      <section className="group-details-section" aria-label="Expense list">
        <ExpenseList
          expenses={expenses}
          members={members}
          memberNames={memberNames}
          currentUserId={user?.id ?? null}
          isLoading={expenseLoading}
          error={expenseError}
          onRetry={() => groupId && loadExpenses(groupId)}
          onView={setViewExpense}
          onEdit={setEditExpense}
          onDelete={setDeleteTarget}
        />
      </section>

      {/* Add Expense Modal */}
      {groupId && (
        <AddExpenseModal
          isOpen={addOpen}
          onClose={() => setAddOpen(false)}
          groupId={groupId}
          members={members}
          memberNames={memberNames}
          onCreated={handleCreated}
        />
      )}

      {/* View Detail Modal */}
      {viewExpense && (
        <ExpenseDetailModal
          isOpen={!!viewExpense}
          onClose={() => setViewExpense(null)}
          expense={viewExpense}
          members={members}
          memberNames={memberNames}
        />
      )}

      {/* Edit Modal */}
      {editExpense && groupId && (
        <EditExpenseModal
          isOpen={!!editExpense}
          onClose={() => setEditExpense(null)}
          expense={editExpense}
          groupId={groupId}
          members={members}
          memberNames={memberNames}
          currentUserId={user?.id ?? null}
          onUpdated={handleUpdated}
        />
      )}
    </div>
  );
}
