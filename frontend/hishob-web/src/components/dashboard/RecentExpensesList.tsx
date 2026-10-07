/**
 * RecentExpensesList — Dashboard recent expenses display
 * 
 * Shows the latest 5 expenses from the most recently updated group.
 * Simplified version of ExpenseCard for dashboard context.
 * 
 * Each expense shows:
 * - Description
 * - Amount (INR)
 * - Split type badge
 * - Date (relative or formatted)
 * - Payer name (if available)
 */

import { Link } from 'react-router-dom';
import type { ExpenseResponse } from '@/types/expenses';

interface RecentExpensesListProps {
  expenses: ExpenseResponse[];
  groupId: string | null;
  groupName: string | null;
  memberNames: Record<string, string>;
  isLoading?: boolean;
  isEmpty?: boolean;
}

function formatAmount(amount: number): string {
  return amount.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatDate(iso: string): string {
  try {
    const date = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    // Recent dates
    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;

    // Older dates
    return date.toLocaleDateString('en-IN', {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
    });
  } catch {
    return '';
  }
}

const SPLIT_LABELS: Record<string, string> = {
  EQUAL: 'Equal',
  EXACT: 'Exact',
  PERCENTAGE: '%',
};

export default function RecentExpensesList({
  expenses,
  groupId,
  groupName,
  memberNames,
  isLoading = false,
  isEmpty = false,
}: RecentExpensesListProps) {
  if (isLoading) {
    return (
      <div className="dashboard-expenses">
        <div className="dashboard-expenses__loading">Loading recent expenses...</div>
      </div>
    );
  }

  if (isEmpty) {
    return (
      <div className="dashboard-expenses">
        <div className="dashboard-expenses__empty">
          <span className="dashboard-expenses__empty-icon">📝</span>
          <p className="dashboard-expenses__empty-text">No expenses yet.</p>
          <p className="dashboard-expenses__empty-hint">
            Create a group and add expenses to get started.
          </p>
        </div>
      </div>
    );
  }

  if (expenses.length === 0) {
    return (
      <div className="dashboard-expenses">
        <div className="dashboard-expenses__empty">
          <span className="dashboard-expenses__empty-icon">📝</span>
          <p className="dashboard-expenses__empty-text">No recent expenses.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-expenses">
      {groupName && groupId && (
        <div className="dashboard-expenses__header">
          <span className="dashboard-expenses__subtitle">
            From group:{' '}
            <Link to={`/groups/${groupId}`} className="dashboard-expenses__group-link">
              {groupName}
            </Link>
          </span>
          <Link to={`/groups/${groupId}/expenses`} className="dashboard-expenses__view-all">
            View all →
          </Link>
        </div>
      )}

      <div className="dashboard-expenses__list">
        {expenses.slice(0, 5).map((expense) => {
          const payerName = memberNames[expense.paidBy] || 'Unknown member';
          const splitLabel = SPLIT_LABELS[expense.splitType] ?? expense.splitType;

          return (
            <div key={expense.id} className="dashboard-expense-item">
              <div className="dashboard-expense-item__main">
                <div className="dashboard-expense-item__top">
                  <span className="dashboard-expense-item__description">
                    {expense.description}
                  </span>
                  <span className="dashboard-expense-item__amount">
                    ₹{formatAmount(expense.totalAmount)}
                  </span>
                </div>
                <div className="dashboard-expense-item__meta">
                  <span className="dashboard-expense-item__payer">
                    Paid by <strong>{payerName}</strong>
                  </span>
                  <span className="dashboard-expense-item__separator">•</span>
                  <span className="dashboard-expense-item__badge">{splitLabel}</span>
                  <span className="dashboard-expense-item__separator">•</span>
                  <span className="dashboard-expense-item__date">
                    {formatDate(expense.createdAt)}
                  </span>
                  <span className="dashboard-expense-item__separator">•</span>
                  <span className="dashboard-expense-item__splits">
                    {expense.splits.length} {expense.splits.length === 1 ? 'person' : 'people'}
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {groupId && (
        <div className="dashboard-expenses__footer">
          <Link
            to={`/groups/${groupId}/expenses`}
            className="btn btn--ghost btn--sm"
          >
            View all expenses in {groupName || 'group'} →
          </Link>
        </div>
      )}
    </div>
  );
}
