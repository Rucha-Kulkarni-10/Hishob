/**
 * ExpenseList — renders all expenses for a group.
 *
 * Handles loading / empty / error states.
 * Delegates rendering of individual cards to ExpenseCard.
 */

import type { ExpenseResponse } from '@/types/expenses';
import type { MemberResponse } from '@/types/groups';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import ExpenseCard from './ExpenseCard';

interface ExpenseListProps {
  expenses: ExpenseResponse[];
  members: MemberResponse[];
  memberNames: Record<string, string>;
  currentUserId: string | null;
  isLoading: boolean;
  error: string | null;
  onRetry: () => void;
  onView: (expense: ExpenseResponse) => void;
  onEdit: (expense: ExpenseResponse) => void;
  onDelete: (expense: ExpenseResponse) => void;
}

export default function ExpenseList({
  expenses,
  members,
  memberNames,
  currentUserId,
  isLoading,
  error,
  onRetry,
  onView,
  onEdit,
  onDelete,
}: ExpenseListProps) {
  if (isLoading) {
    return <LoadingState label="Loading expenses…" height="md" />;
  }

  if (error) {
    return (
      <div className="alert alert--error groups-page__alert" role="alert">
        <span>{error}</span>
        <button type="button" className="btn btn--ghost btn--sm" onClick={onRetry}>
          Retry
        </button>
      </div>
    );
  }

  if (expenses.length === 0) {
    return (
      <EmptyState
        icon="💸"
        heading="No expenses yet"
        description="Add the first expense for this group using the button above."
      />
    );
  }

  return (
    <div className="expense-list">
      {expenses.map((expense) => (
        <ExpenseCard
          key={expense.id}
          expense={expense}
          members={members}
          memberNames={memberNames}
          currentUserId={currentUserId}
          onView={onView}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
}
