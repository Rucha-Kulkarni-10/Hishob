/**
 * PersonalExpenseCard — displays a single personal expense in lists.
 *
 * Shows:
 *   - Description
 *   - Total amount
 *   - Paid by (resolved name, never raw UUID)
 *   - Your share when current user is a participant
 *   - Split type badge
 *   - Date
 *   - Participant count
 *   - Edit/Delete actions only when currentUserId === expense.createdBy
 */

import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import { getCategoryMeta } from '@/types/expenses';

interface PersonalExpenseCardProps {
  expense: PersonalExpenseResponse;
  /** Map of userId → display name */
  memberNames: Record<string, string>;
  currentUserId: string | null;
  onView: (expense: PersonalExpenseResponse) => void;
  onEdit: (expense: PersonalExpenseResponse) => void;
  onDelete: (expense: PersonalExpenseResponse) => void;
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function resolveMemberName(
  userId: string,
  memberNames: Record<string, string>,
  currentUserId: string | null
): string {
  if (currentUserId && userId === currentUserId) {
    return 'You';
  }
  const resolved = memberNames[userId];
  if (!resolved || UUID_REGEX.test(resolved.trim())) {
    return 'Unknown member';
  }
  return resolved.trim();
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
  const isCurrentUserPayer = Boolean(currentUserId && expense.paidBy === currentUserId);
  const payerName = isCurrentUserPayer
    ? 'You'
    : resolveMemberName(expense.paidBy, memberNames, currentUserId);
  const mySplit = currentUserId ? expense.splits.find((s) => s.userId === currentUserId) : undefined;
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
          <span className="expense-card__meta expense-card__payer">
            {isCurrentUserPayer ? 'You paid' : `${payerName} paid`} <strong>₹{formatAmount(expense.totalAmount)}</strong>
          </span>
          <span className="badge badge--neutral expense-card__category-badge">
            {catMeta.icon} {catMeta.label}
          </span>
          {mySplit && (
            <span className="expense-card__meta expense-card__your-share">
              Your share: <strong>₹{formatAmount(mySplit.amount)}</strong>
            </span>
          )}
          <span className="expense-card__meta">
            {participantCount} participant{participantCount !== 1 ? 's' : ''}
          </span>
          <span
            className={`badge badge--${expense.splitType === 'EQUAL' ? 'primary' : 'muted'} expense-card__split-badge`}
          >
            {SPLIT_LABELS[expense.splitType] ?? expense.splitType}
          </span>
          <span className="expense-card__date" title={`Recorded on ${formatDate(expense.createdAt)}`}>
            {formatDate(expense.createdAt)}
          </span>
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
