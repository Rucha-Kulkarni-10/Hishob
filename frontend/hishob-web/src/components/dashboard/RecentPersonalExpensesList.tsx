/**
 * RecentPersonalExpensesList — Dashboard recent personal expenses display.
 *
 * Shows the latest 5 personal 1-on-1 expenses involving the current user.
 *
 * Each expense shows:
 * - Description
 * - Total Amount (INR)
 * - Payer & User role ("You paid" / "{Name} paid")
 * - User's share (if applicable)
 * - Split participant count
 * - Date
 * - "Personal" context badge
 */

import { Link } from 'react-router-dom';
import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import type { Friend } from '@/types/friends';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function resolveMemberName(
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

    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;

    return date.toLocaleDateString('en-IN', {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
    });
  } catch {
    return '';
  }
}

interface RecentPersonalExpensesListProps {
  expenses: PersonalExpenseResponse[];
  friends: Friend[];
  currentUserId: string | null;
  isLoading?: boolean;
  hasError?: boolean;
  errorMessage?: string | null;
  onRetry?: () => void;
}

export default function RecentPersonalExpensesList({
  expenses,
  friends,
  currentUserId,
  isLoading = false,
  hasError = false,
  errorMessage = null,
  onRetry,
}: RecentPersonalExpensesListProps) {
  if (isLoading) {
    return (
      <div className="dashboard-expenses">
        <div className="dashboard-expenses__loading">Loading recent personal expenses…</div>
      </div>
    );
  }

  if (hasError) {
    return (
      <div className="dashboard-expenses">
        <div className="alert alert--error" role="alert" style={{ margin: '1rem' }}>
          <span>{errorMessage || 'Unable to load personal expenses.'}</span>
          {onRetry && (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={onRetry}
              style={{ marginLeft: '1rem' }}
            >
              Retry
            </button>
          )}
        </div>
      </div>
    );
  }

  if (expenses.length === 0) {
    return (
      <div className="dashboard-expenses">
        <div className="dashboard-expenses__empty">
          <span className="dashboard-expenses__empty-icon">🤝</span>
          <p className="dashboard-expenses__empty-text">No personal expenses yet.</p>
          <p className="dashboard-expenses__empty-hint">
            Track 1-on-1 expenses directly with friends.
          </p>
          <div style={{ marginTop: '0.75rem' }}>
            <Link to="/expenses" className="btn btn--primary btn--sm">
              + Personal Expense
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const sortedExpenses = [...expenses]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);

  return (
    <div className="dashboard-expenses">
      <div className="dashboard-expenses__header">
        <span className="dashboard-expenses__subtitle">
          Direct 1-on-1 activity with friends
        </span>
        <Link to="/expenses" className="dashboard-expenses__view-all">
          View all →
        </Link>
      </div>

      <div className="dashboard-expenses__list">
        {sortedExpenses.map((expense) => {
          const isMe = currentUserId ? expense.paidBy === currentUserId : false;
          const payerName = isMe
            ? 'You'
            : resolveMemberName(expense.paidBy, currentUserId, friends);

          const mySplit = currentUserId
            ? expense.splits.find((s) => s.userId === currentUserId)
            : null;

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
                  <span className="badge badge--primary badge--xs" style={{ fontSize: '0.65rem' }}>
                    Personal
                  </span>
                  <span className="dashboard-expense-item__separator">•</span>
                  <span className="dashboard-expense-item__payer">
                    {isMe ? (
                      <strong>You paid</strong>
                    ) : (
                      <>
                        Paid by <strong>{payerName}</strong>
                      </>
                    )}
                  </span>
                  {mySplit && !isMe && (
                    <>
                      <span className="dashboard-expense-item__separator">•</span>
                      <span className="dashboard-expense-item__share" style={{ color: 'var(--color-owes)' }}>
                        Your share: ₹{formatAmount(mySplit.amount)}
                      </span>
                    </>
                  )}
                  {mySplit && isMe && expense.splits.length > 1 && (
                    <>
                      <span className="dashboard-expense-item__separator">•</span>
                      <span className="dashboard-expense-item__share" style={{ color: 'var(--color-gets)' }}>
                        Your share: ₹{formatAmount(mySplit.amount)}
                      </span>
                    </>
                  )}
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

      <div className="dashboard-expenses__footer">
        <Link to="/expenses" className="btn btn--ghost btn--sm">
          View all personal expenses →
        </Link>
      </div>
    </div>
  );
}
