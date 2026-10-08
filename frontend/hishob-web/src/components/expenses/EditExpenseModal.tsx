/**
 * EditExpenseModal — pre-filled form to update an existing expense.
 *
 * Reuses the same form logic as AddExpenseModal.
 * Only shown / enabled when the current user is the expense creator.
 * Submits PUT /api/expenses/{expenseId}.
 */

import { type FormEvent, useCallback, useMemo, useState } from 'react';
import Modal from '@/components/common/Modal';
import { updateExpense } from '@/services/expenseService';
import {
  type CreateExpenseRequest,
  type ExpenseCategory,
  EXPENSE_CATEGORIES,
  CATEGORY_METADATA,
  type ExpenseResponse,
  type SplitType,
} from '@/types/expenses';
import type { MemberResponse } from '@/types/groups';
import { getErrorMessage } from '@/utils/errorUtils';

interface EditExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: ExpenseResponse;
  groupId: string;
  members: MemberResponse[];
  memberNames: Record<string, string>;
  currentUserId: string | null;
  onUpdated: (expense: ExpenseResponse) => void;
}

interface ParticipantEntry {
  userId: string;
  amount: string;
  percentage: string;
}

export default function EditExpenseModal({
  isOpen,
  onClose,
  expense,
  groupId,
  members,
  memberNames,
  currentUserId,
  onUpdated,
}: EditExpenseModalProps) {
  const isCreator = currentUserId === expense.createdBy;

  const buildParticipants = useCallback((): ParticipantEntry[] => {
    return expense.splits.map((s) => ({
      userId: s.userId,
      amount: s.amount != null ? String(s.amount) : '',
      percentage: s.percentage != null ? String(s.percentage) : '',
    }));
  }, [expense]);

  const [description, setDescription] = useState(expense.description);
  const [totalAmount, setTotalAmount] = useState(String(expense.totalAmount));
  const [paidBy, setPaidBy] = useState(expense.paidBy);
  const [category, setCategory] = useState<ExpenseCategory>(expense.category || 'OTHER');
  const [splitType, setSplitType] = useState<SplitType>(expense.splitType);
  const [participants, setParticipants] = useState<ParticipantEntry[]>(buildParticipants);
  const [apiError, setApiError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  // Track previous trigger values to re-init form when modal opens or target expense changes
  const [prevReconcileKey, setPrevReconcileKey] = useState('');
  const reconcileKey = `${isOpen ? 'open' : 'closed'}-${expense.id}`;
  if (isOpen && reconcileKey !== prevReconcileKey) {
    setDescription(expense.description);
    setTotalAmount(String(expense.totalAmount));
    setPaidBy(expense.paidBy);
    setCategory(expense.category || 'OTHER');
    setSplitType(expense.splitType);
    setParticipants(buildParticipants());
    setApiError('');
    setFieldErrors({});
  }
  if (reconcileKey !== prevReconcileKey) {
    setPrevReconcileKey(reconcileKey);
  }

  const handleClose = () => {
    setApiError('');
    setFieldErrors({});
    onClose();
  };

  const selectedUserIds = useMemo(
    () => new Set(participants.map((p) => p.userId)),
    [participants]
  );

  const toggleParticipant = (userId: string) => {
    setParticipants((prev) => {
      if (prev.some((p) => p.userId === userId)) {
        return prev.filter((p) => p.userId !== userId);
      }
      return [...prev, { userId, amount: '', percentage: '' }];
    });
  };

  const updateParticipantAmount = (userId: string, value: string) =>
    setParticipants((prev) =>
      prev.map((p) => (p.userId === userId ? { ...p, amount: value } : p))
    );

  const updateParticipantPercentage = (userId: string, value: string) =>
    setParticipants((prev) =>
      prev.map((p) => (p.userId === userId ? { ...p, percentage: value } : p))
    );

  const parsedTotal = parseFloat(totalAmount) || 0;

  const exactSum = useMemo(
    () => participants.reduce((acc, p) => acc + (parseFloat(p.amount) || 0), 0),
    [participants]
  );

  const percentageSum = useMemo(
    () => participants.reduce((acc, p) => acc + (parseFloat(p.percentage) || 0), 0),
    [participants]
  );

  const exactValid = splitType !== 'EXACT' || Math.abs(exactSum - parsedTotal) < 0.001;
  const percentageValid = splitType !== 'PERCENTAGE' || Math.abs(percentageSum - 100) < 0.001;

  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    if (!description.trim()) errors.description = 'Description is required.';
    if (description.trim().length > 300) errors.description = 'Description must be 300 characters or fewer.';

    const amount = parseFloat(totalAmount);
    if (!totalAmount || isNaN(amount) || amount < 0.01)
      errors.totalAmount = 'Amount must be at least 0.01.';

    if (!paidBy) errors.paidBy = 'Payer is required.';

    if (participants.length === 0)
      errors.participants = 'Select at least one participant.';

    if (splitType === 'EXACT') {
      participants.forEach((p) => {
        const a = parseFloat(p.amount);
        if (!p.amount || isNaN(a) || a <= 0)
          errors.participants = 'All participants must have a positive amount.';
      });
      if (!errors.participants && !exactValid)
        errors.participants = `Amounts total ${exactSum.toFixed(2)} but expense is ${parsedTotal.toFixed(2)}.`;
    }

    if (splitType === 'PERCENTAGE') {
      participants.forEach((p) => {
        const pct = parseFloat(p.percentage);
        if (!p.percentage || isNaN(pct) || pct <= 0 || pct > 100)
          errors.participants = 'All participants must have a percentage between 0 and 100.';
      });
      if (!errors.participants && !percentageValid)
        errors.participants = `Percentages total ${percentageSum.toFixed(2)}% but must equal 100%.`;
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError('');
    if (!isCreator || !validate()) return;

    const splits = participants.map((p) => ({
      userId: p.userId,
      ...(splitType === 'EXACT' ? { amount: parseFloat(p.amount) } : {}),
      ...(splitType === 'PERCENTAGE' ? { percentage: parseFloat(p.percentage) } : {}),
    }));

    const payload: CreateExpenseRequest = {
      groupId,
      description: description.trim(),
      totalAmount: parseFloat(totalAmount),
      paidBy,
      splitType,
      category,
      splits,
    };

    setIsLoading(true);
    try {
      const updated = await updateExpense(expense.id, payload);
      onUpdated(updated);
      onClose();
    } catch (err) {
      setApiError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Edit expense"
      widthClass="modal__dialog--lg"
    >
      {!isCreator && (
        <div className="alert alert--error" role="alert">
          Only the expense creator can edit this expense.
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}

        {/* Description */}
        <div className="form-field">
          <label htmlFor="edit-exp-description" className="form-field__label">
            Description <span className="form-field__required">*</span>
          </label>
          <input
            id="edit-exp-description"
            type="text"
            className={`form-field__input${fieldErrors.description ? ' form-field__input--error' : ''}`}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={300}
            disabled={isLoading || !isCreator}
          />
          {fieldErrors.description && (
            <span className="form-field__error">{fieldErrors.description}</span>
          )}
        </div>

        {/* Category */}
        <div className="form-field">
          <label htmlFor="edit-exp-category" className="form-field__label">
            Category <span className="form-field__required">*</span>
          </label>
          <select
            id="edit-exp-category"
            className="form-field__input"
            value={category}
            onChange={(e) => setCategory(e.target.value as ExpenseCategory)}
            disabled={isLoading || !isCreator}
          >
            {EXPENSE_CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {CATEGORY_METADATA[cat].icon} {CATEGORY_METADATA[cat].label}
              </option>
            ))}
          </select>
        </div>

        {/* Total Amount */}
        <div className="form-field">
          <label htmlFor="edit-exp-amount" className="form-field__label">
            Total Amount <span className="form-field__required">*</span>
          </label>
          <input
            id="edit-exp-amount"
            type="number"
            step="0.01"
            min="0.01"
            className={`form-field__input${fieldErrors.totalAmount ? ' form-field__input--error' : ''}`}
            value={totalAmount}
            onChange={(e) => setTotalAmount(e.target.value)}
            disabled={isLoading || !isCreator}
          />
          {fieldErrors.totalAmount && (
            <span className="form-field__error">{fieldErrors.totalAmount}</span>
          )}
        </div>

        {/* Paid By */}
        <div className="form-field">
          <label htmlFor="edit-exp-paidby" className="form-field__label">
            Paid by <span className="form-field__required">*</span>
          </label>
          <select
            id="edit-exp-paidby"
            className={`form-field__input${fieldErrors.paidBy ? ' form-field__input--error' : ''}`}
            value={paidBy}
            onChange={(e) => setPaidBy(e.target.value)}
            disabled={isLoading || !isCreator}
          >
            <option value="">— Select payer —</option>
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>
                {memberNames[m.userId] || m.name || 'Unknown member'}
              </option>
            ))}
          </select>
          {fieldErrors.paidBy && (
            <span className="form-field__error">{fieldErrors.paidBy}</span>
          )}
        </div>

        {/* Split Type */}
        <div className="form-field">
          <label htmlFor="edit-exp-splittype" className="form-field__label">
            Split type <span className="form-field__required">*</span>
          </label>
          <select
            id="edit-exp-splittype"
            className="form-field__input"
            value={splitType}
            onChange={(e) => setSplitType(e.target.value as SplitType)}
            disabled={isLoading || !isCreator}
          >
            <option value="EQUAL">Equal — split evenly</option>
            <option value="EXACT">Exact — custom amounts</option>
            <option value="PERCENTAGE">Percentage — custom %</option>
          </select>
        </div>

        {/* Participants */}
        <div className="form-field">
          <span className="form-field__label">
            Participants <span className="form-field__required">*</span>
          </span>
          <div className="expense-form__participants">
            {members.map((m) => {
              const checked = selectedUserIds.has(m.userId);
              const entry = participants.find((p) => p.userId === m.userId);
              const name = memberNames[m.userId] || m.name || 'Unknown member';
              return (
                <div key={m.userId} className="expense-form__participant-row">
                  <label className="expense-form__participant-check">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleParticipant(m.userId)}
                      disabled={isLoading || !isCreator}
                    />
                    <span className="expense-form__participant-name">{name}</span>
                  </label>

                  {checked && splitType === 'EXACT' && (
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      className="form-field__input expense-form__participant-input"
                      placeholder="Amount"
                      value={entry?.amount ?? ''}
                      onChange={(e) => updateParticipantAmount(m.userId, e.target.value)}
                      disabled={isLoading || !isCreator}
                      aria-label={`Amount for ${name}`}
                    />
                  )}

                  {checked && splitType === 'PERCENTAGE' && (
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      max="100"
                      className="form-field__input expense-form__participant-input"
                      placeholder="%"
                      value={entry?.percentage ?? ''}
                      onChange={(e) => updateParticipantPercentage(m.userId, e.target.value)}
                      disabled={isLoading || !isCreator}
                      aria-label={`Percentage for ${name}`}
                    />
                  )}
                </div>
              );
            })}
          </div>

          {splitType === 'EXACT' && participants.length > 0 && (
            <p className={`expense-form__live-total${exactValid ? ' expense-form__live-total--ok' : ' expense-form__live-total--warn'}`}>
              Entered: {exactSum.toFixed(2)} / {parsedTotal > 0 ? parsedTotal.toFixed(2) : '?'}
              {exactValid ? ' ✓' : ''}
            </p>
          )}

          {splitType === 'PERCENTAGE' && participants.length > 0 && (
            <p className={`expense-form__live-total${percentageValid ? ' expense-form__live-total--ok' : ' expense-form__live-total--warn'}`}>
              Total %: {percentageSum.toFixed(2)} / 100
              {percentageValid ? ' ✓' : ''}
            </p>
          )}

          {fieldErrors.participants && (
            <span className="form-field__error">{fieldErrors.participants}</span>
          )}
        </div>

        <div className="modal__actions">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={handleClose}
            disabled={isLoading}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn--primary"
            disabled={isLoading || !isCreator}
            aria-busy={isLoading}
          >
            {isLoading ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
