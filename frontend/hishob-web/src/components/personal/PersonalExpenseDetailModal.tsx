/**
 * PersonalExpenseDetailModal — read-only view with edit/delete actions for creator.
 *
 * Shows:
 *   - Description, total amount, split type
 *   - Clear role summary (who paid, your share)
 *   - Per-participant breakdown: name, amount, percentage
 *   - Created / updated timestamps
 *   - Edit & Delete buttons if current user is creator
 */

import Modal from '@/components/common/Modal';
import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import { getCategoryMeta } from '@/types/expenses';

interface PersonalExpenseDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: PersonalExpenseResponse;
  memberNames: Record<string, string>;
  currentUserId: string | null;
  onEdit?: (expense: PersonalExpenseResponse) => void;
  onDelete?: (expense: PersonalExpenseResponse) => void;
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
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
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

export default function PersonalExpenseDetailModal({
  isOpen,
  onClose,
  expense,
  memberNames,
  currentUserId,
  onEdit,
  onDelete,
}: PersonalExpenseDetailModalProps) {
  const isCreator = currentUserId === expense.createdBy;
  const isCurrentUserPayer = Boolean(currentUserId && expense.paidBy === currentUserId);
  const payerName = isCurrentUserPayer
    ? 'You'
    : resolveMemberName(expense.paidBy, memberNames, currentUserId);
  const creatorName = isCreator
    ? 'You'
    : resolveMemberName(expense.createdBy, memberNames, currentUserId);
  const mySplit = currentUserId ? expense.splits.find((s) => s.userId === currentUserId) : undefined;
  const catMeta = getCategoryMeta(expense.category);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Personal Expense Details"
      widthClass="modal__dialog--lg"
    >
      {/* Summary */}
      <div className="expense-detail__summary">
        <div className="expense-detail__summary-top">
          <h4 className="expense-detail__title">{expense.description}</h4>
          <span className="expense-detail__amount">
            ₹{formatAmount(expense.totalAmount)}
          </span>
        </div>
        <div className="expense-detail__summary-meta">
          <span>
            {isCurrentUserPayer ? 'You paid' : `${payerName} paid`} <strong>₹{formatAmount(expense.totalAmount)}</strong>
          </span>
          <span>·</span>
          <span className="badge badge--neutral">
            {catMeta.icon} {catMeta.label}
          </span>
          {mySplit && (
            <>
              <span>·</span>
              <span>
                Your share: <strong>₹{formatAmount(mySplit.amount)}</strong>
              </span>
            </>
          )}
          <span>·</span>
          <span
            className={`badge badge--${expense.splitType === 'EQUAL' ? 'primary' : 'muted'}`}
          >
            {SPLIT_LABELS[expense.splitType] ?? expense.splitType} split
          </span>
        </div>
      </div>

      {/* Splits table */}
      <div className="expense-detail__splits">
        <h5 className="expense-detail__splits-heading">Participants</h5>
        <table className="members-table">
          <thead>
            <tr>
              <th>Member</th>
              <th>Share Amount</th>
              {expense.splitType === 'PERCENTAGE' && <th>%</th>}
            </tr>
          </thead>
          <tbody>
            {expense.splits.map((s) => (
              <tr key={s.userId}>
                <td>{resolveMemberName(s.userId, memberNames, currentUserId)}</td>
                <td>₹{formatAmount(s.amount)}</td>
                {expense.splitType === 'PERCENTAGE' && (
                  <td>
                    {s.percentage != null ? `${Number(s.percentage).toFixed(2)}%` : '—'}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Footer meta */}
      <div className="expense-detail__footer">
        <span>Created by <strong>{creatorName}</strong></span>
        <span>Recorded on {formatDate(expense.createdAt)}</span>
        {expense.updatedAt !== expense.createdAt && (
          <span>Updated {formatDate(expense.updatedAt)}</span>
        )}
      </div>

      {/* Actions */}
      <div className="modal__actions" style={{ justifyContent: 'space-between' }}>
        <div>
          {isCreator && (
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              {onEdit && (
                <button
                  type="button"
                  className="btn btn--secondary btn--sm"
                  onClick={() => {
                    onClose();
                    onEdit(expense);
                  }}
                >
                  Edit
                </button>
              )}
              {onDelete && (
                <button
                  type="button"
                  className="btn btn--danger btn--sm"
                  onClick={() => {
                    onClose();
                    onDelete(expense);
                  }}
                >
                  Delete
                </button>
              )}
            </div>
          )}
        </div>
        <button type="button" className="btn btn--ghost" onClick={onClose}>
          Close
        </button>
      </div>
    </Modal>
  );
}
