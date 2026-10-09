/**
 * ExpenseDetailModal — read-only view of a full expense.
 *
 * Shows:
 *   - Description, total amount, split type
 *   - Who paid
 *   - Per-participant breakdown: name, amount, percentage (if available)
 *   - Created / updated timestamps
 */

import Modal from '@/components/common/Modal';
import { type ExpenseResponse, getCategoryMeta } from '@/types/expenses';
import type { MemberResponse } from '@/types/groups';
import { useAuth } from '@/hooks/useAuth';
import ExpenseCommentsSection from '@/components/comments/ExpenseCommentsSection';

interface ExpenseDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: ExpenseResponse;
  members: MemberResponse[];
  memberNames: Record<string, string>;
  currentUserId?: string | null;
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

export default function ExpenseDetailModal({
  isOpen,
  onClose,
  expense,
  memberNames,
  currentUserId,
}: ExpenseDetailModalProps) {
  const { user } = useAuth();
  const effectiveUserId = currentUserId ?? user?.id ?? null;
  const payerName = memberNames[expense.paidBy] || 'Unknown member';
  const creatorName = memberNames[expense.createdBy] || 'Unknown member';
  const catMeta = getCategoryMeta(expense.category);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Expense details"
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
            Paid by <strong>{payerName}</strong>
          </span>
          <span>·</span>
          <span className="badge badge--neutral">
            {catMeta.icon} {catMeta.label}
          </span>
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
              <th>Amount</th>
              {expense.splitType === 'PERCENTAGE' && <th>%</th>}
            </tr>
          </thead>
          <tbody>
            {expense.splits.map((s) => (
              <tr key={s.userId}>
                <td>{memberNames[s.userId] || 'Unknown member'}</td>
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

      {/* Comments section */}
      <ExpenseCommentsSection
        expenseId={expense.id}
        expenseCreatorId={expense.createdBy}
        memberNames={memberNames}
        currentUserId={effectiveUserId}
      />

      {/* Footer meta */}
      <div className="expense-detail__footer">
        <span>Created by <strong>{creatorName}</strong></span>
        <span>Added {formatDate(expense.createdAt)}</span>
        {expense.updatedAt !== expense.createdAt && (
          <span>Updated {formatDate(expense.updatedAt)}</span>
        )}
      </div>

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onClose}>
          Close
        </button>
      </div>
    </Modal>
  );
}
