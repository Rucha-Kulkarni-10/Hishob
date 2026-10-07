/**
 * AddExpenseModal — form to create a new expense.
 *
 * Fields: description, totalAmount, paidBy, splitType, participants.
 *
 * Split behaviour:
 *   EQUAL      — select participants only, backend calculates amounts.
 *   EXACT      — amount input per participant, live running total.
 *   PERCENTAGE — percentage input per participant, live sum, must equal 100.
 *
 * Validation (all client-side; backend re-validates):
 *   - Description required, max 300 chars
 *   - Amount >= 0.01
 *   - Payer required and must be in selected participants (or at least a member)
 *   - At least one participant
 *   - EXACT: entered amounts must sum to totalAmount
 *   - PERCENTAGE: percentages must sum to 100
 *   - No duplicate participants (enforced by checkbox UI)
 */

import { type FormEvent, useCallback, useMemo, useState } from 'react';
import Modal from '@/components/common/Modal';
import { createExpense } from '@/services/expenseService';
import type { CreateExpenseRequest, ExpenseResponse, SplitType } from '@/types/expenses';
import type { MemberResponse } from '@/types/groups';
import { getErrorMessage } from '@/utils/errorUtils';

interface AddExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  groupId: string;
  members: MemberResponse[];
  memberNames: Record<string, string>;
  onCreated: (expense: ExpenseResponse) => void;
}

interface ParticipantEntry {
  userId: string;
  amount: string;       // string for controlled input
  percentage: string;   // string for controlled input
}

const INITIAL_SPLIT: SplitType = 'EQUAL';

function buildInitialParticipants(): ParticipantEntry[] {
  return [];
}

export default function AddExpenseModal({
  isOpen,
  onClose,
  groupId,
  members,
  memberNames,
  onCreated,
}: AddExpenseModalProps) {
  const [description, setDescription] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [paidBy, setPaidBy] = useState('');
  const [splitType, setSplitType] = useState<SplitType>(INITIAL_SPLIT);
  const [participants, setParticipants] = useState<ParticipantEntry[]>(buildInitialParticipants());
  const [apiError, setApiError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  const reset = useCallback(() => {
    setDescription('');
    setTotalAmount('');
    setPaidBy('');
    setSplitType(INITIAL_SPLIT);
    setParticipants([]);
    setApiError('');
    setFieldErrors({});
    setIsLoading(false);
  }, []);

  const handleClose = () => {
    reset();
    onClose();
  };

  // Pre-select the first member as payer whenever the form is open and no payer is chosen
  if (isOpen && members.length > 0 && !paidBy) {
    setPaidBy(members[0].userId);
  }

  // ── Participant selection helpers ───────────────────────────────────────────
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

  const updateParticipantAmount = (userId: string, value: string) => {
    setParticipants((prev) =>
      prev.map((p) => (p.userId === userId ? { ...p, amount: value } : p))
    );
  };

  const updateParticipantPercentage = (userId: string, value: string) => {
    setParticipants((prev) =>
      prev.map((p) => (p.userId === userId ? { ...p, percentage: value } : p))
    );
  };

  // ── Live totals ─────────────────────────────────────────────────────────────
  const parsedTotal = parseFloat(totalAmount) || 0;

  const exactSum = useMemo(() => {
    return participants.reduce((acc, p) => acc + (parseFloat(p.amount) || 0), 0);
  }, [participants]);

  const percentageSum = useMemo(() => {
    return participants.reduce((acc, p) => acc + (parseFloat(p.percentage) || 0), 0);
  }, [participants]);

  const exactValid = useMemo(() => {
    if (splitType !== 'EXACT') return true;
    return Math.abs(exactSum - parsedTotal) < 0.001;
  }, [splitType, exactSum, parsedTotal]);

  const percentageValid = useMemo(() => {
    if (splitType !== 'PERCENTAGE') return true;
    return Math.abs(percentageSum - 100) < 0.001;
  }, [splitType, percentageSum]);

  // ── Validation ──────────────────────────────────────────────────────────────
  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    if (!description.trim()) errors.description = 'Description is required.';
    if (description.trim().length > 300) errors.description = 'Description must be 300 characters or fewer.';

    const amount = parseFloat(totalAmount);
    if (!totalAmount || isNaN(amount) || amount < 0.01) {
      errors.totalAmount = 'Amount must be at least 0.01.';
    }

    if (!paidBy) errors.paidBy = 'Payer is required.';

    if (participants.length === 0) {
      errors.participants = 'Select at least one participant.';
    }

    if (splitType === 'EXACT') {
      participants.forEach((p) => {
        const a = parseFloat(p.amount);
        if (!p.amount || isNaN(a) || a <= 0) {
          errors.participants = 'All participants must have a positive amount.';
        }
      });
      if (!errors.participants && !exactValid) {
        errors.participants = `Amounts total ${exactSum.toFixed(2)} but expense is ${parsedTotal.toFixed(2)}.`;
      }
    }

    if (splitType === 'PERCENTAGE') {
      participants.forEach((p) => {
        const pct = parseFloat(p.percentage);
        if (!p.percentage || isNaN(pct) || pct <= 0 || pct > 100) {
          errors.participants = 'All participants must have a percentage between 0 and 100.';
        }
      });
      if (!errors.participants && !percentageValid) {
        errors.participants = `Percentages total ${percentageSum.toFixed(2)}% but must equal 100%.`;
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // ── Submit ──────────────────────────────────────────────────────────────────
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError('');
    if (!validate()) return;

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
      splits,
    };

    setIsLoading(true);
    try {
      const created = await createExpense(payload);
      onCreated(created);
      reset();
      onClose();
    } catch (err) {
      setApiError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  };

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Add expense"
      widthClass="modal__dialog--lg"
    >
      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}

        {/* Description */}
        <div className="form-field">
          <label htmlFor="exp-description" className="form-field__label">
            Description <span className="form-field__required">*</span>
          </label>
          <input
            id="exp-description"
            type="text"
            className={`form-field__input${fieldErrors.description ? ' form-field__input--error' : ''}`}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Dinner at restaurant"
            maxLength={300}
            disabled={isLoading}
            autoComplete="off"
          />
          {fieldErrors.description && (
            <span className="form-field__error">{fieldErrors.description}</span>
          )}
        </div>

        {/* Total Amount */}
        <div className="form-field">
          <label htmlFor="exp-amount" className="form-field__label">
            Total Amount <span className="form-field__required">*</span>
          </label>
          <input
            id="exp-amount"
            type="number"
            step="0.01"
            min="0.01"
            className={`form-field__input${fieldErrors.totalAmount ? ' form-field__input--error' : ''}`}
            value={totalAmount}
            onChange={(e) => setTotalAmount(e.target.value)}
            placeholder="0.00"
            disabled={isLoading}
          />
          {fieldErrors.totalAmount && (
            <span className="form-field__error">{fieldErrors.totalAmount}</span>
          )}
        </div>

        {/* Paid By */}
        <div className="form-field">
          <label htmlFor="exp-paidby" className="form-field__label">
            Paid by <span className="form-field__required">*</span>
          </label>
          <select
            id="exp-paidby"
            className={`form-field__input${fieldErrors.paidBy ? ' form-field__input--error' : ''}`}
            value={paidBy}
            onChange={(e) => setPaidBy(e.target.value)}
            disabled={isLoading}
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
          <label htmlFor="exp-splittype" className="form-field__label">
            Split type <span className="form-field__required">*</span>
          </label>
          <select
            id="exp-splittype"
            className="form-field__input"
            value={splitType}
            onChange={(e) => setSplitType(e.target.value as SplitType)}
            disabled={isLoading}
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
          {members.length === 0 ? (
            <p className="expense-form__no-members">No members found.</p>
          ) : (
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
                        disabled={isLoading}
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
                        disabled={isLoading}
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
                        placeholder="% "
                        value={entry?.percentage ?? ''}
                        onChange={(e) => updateParticipantPercentage(m.userId, e.target.value)}
                        disabled={isLoading}
                        aria-label={`Percentage for ${name}`}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* Live totals */}
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
            disabled={isLoading}
            aria-busy={isLoading}
          >
            {isLoading ? 'Adding…' : 'Add expense'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
