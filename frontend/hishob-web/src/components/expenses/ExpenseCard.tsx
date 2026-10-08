/**
 * ExpenseCard — displays a single expense summary in the expense list.
 *
 * Shows:
 *   - Description
 *   - Total amount
 *   - Paid by (name if available, UUID fallback)
 *   - Split type badge
 *   - Date
 *   - Participant count
 *   - Edit/Delete actions only when currentUserId === expense.createdBy
 */

import { type ExpenseResponse, getCategoryMeta } from '@/types/expenses';
import type { MemberResponse } from '@/types/groups';

interface ExpenseCardProps {
  expense: ExpenseResponse;
  members: MemberResponse[];
  /** Map of userId → display name, built by parent */
  memberNames: Record<string, string>;
  currentUserId: string | null;
  onView: (expense: ExpenseResponse) => void;
  onEdit: (expense: ExpenseResponse) => void;
  onDelete: (expense: ExpenseResponse) => void;
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

export default function ExpenseCard({
  expense,
  memberNames,
  currentUserId,
  onView,
  onEdit,
  onDelete,
}: ExpenseCardProps) {
  const isCreator = currentUserId === expense.createdBy;
  const payerName = memberNames[expense.paidBy] || 'Unknown member';
  const participantCount = expense.splits.length;
  const catMeta = getCategoryMeta(expense.category);

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
          <span className="badge badge--neutral expense-card__category-badge">
            {catMeta.icon} {catMeta.label}
          </span>
          <span className="expense-card__meta">
            {participantCount} participant{participantCount !== 1 ? 's' : ''}
          </span>
          <span className={`badge badge--${expense.splitType === 'EQUAL' ? 'primary' : 'muted'} expense-card__split-badge`}>
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
