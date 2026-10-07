/**
 * PersonalExpenseCard — displays a single personal expense in lists.
 *
 * Shows:
 *   - Description
 *   - Total amount
 *   - Paid by (name resolved from friends/user, never raw UUID)
 *   - Split type badge
 *   - Date
 *   - Participant count
 *   - Edit/Delete actions only when currentUserId === expense.createdBy
 */

import type { PersonalExpenseResponse } from '@/types/personalExpenses';

interface PersonalExpenseCardProps {
  expense: PersonalExpenseResponse;
  /** Map of userId → display name */
  memberNames: Record<string, string>;
  currentUserId: string | null;
  onView: (expense: PersonalExpenseResponse) => void;
  onEdit: (expense: PersonalExpenseResponse) => void;
  onDelete: (expense: PersonalExpenseResponse) => void;
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return '';
  }
}

function formatAmount(amount: number): string {
  return amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

const SPLIT_LABELS: Record<string, string> = {
  EQUAL: 'Equal',
  EXACT: 'Exact',
  PERCENTAGE: 'Percentage',
};

export default function PersonalExpenseCard({
  expense,
  memberNames,
  currentUserId,
  onView,
  onEdit,
  onDelete,
}: PersonalExpenseCardProps) {
  const isCreator = currentUserId === expense.createdBy;
  const payerName = memberNames[expense.paidBy] || 'Unknown member';
  const participantCount = expense.splits.length;

  return (
    <div className="expense-card" role="article">
      <button
        className="expense-card__main"
        type="button"
        onClick={() => onView(expense)}
        aria-label={`View details for ${expense.description}`}
      >
        <div className="expense-card__top">
          <span className="expense-card__description">{expense.description}</span>
          <span className="expense-card__amount">₹{formatAmount(expense.totalAmount)}</span>
        </div>
        <div className="expense-card__meta-row">
          <span className="expense-card__meta">
            Paid by <strong>{payerName}</strong>
          </span>
          <span className="expense-card__meta">
            {participantCount} participant{participantCount !== 1 ? 's' : ''}
          </span>
          <span
            className={`badge badge--${expense.splitType === 'EQUAL' ? 'primary' : 'muted'} expense-card__split-badge`}
          >
            {SPLIT_LABELS[expense.splitType] ?? expense.splitType}
          </span>
          <span className="expense-card__date">{formatDate(expense.createdAt)}</span>
        </div>
      </button>

      {isCreator && (
        <div className="expense-card__actions">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => onEdit(expense)}
            aria-label={`Edit expense: ${expense.description}`}
          >
            Edit
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm expense-card__delete-btn"
            onClick={() => onDelete(expense)}
            aria-label={`Delete expense: ${expense.description}`}
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
