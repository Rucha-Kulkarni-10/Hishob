/**
 * EditPersonalExpenseModal — pre-filled form to update an existing personal expense.
 *
 * Only enabled when the current user is the expense creator.
 * Submits PUT /api/personal-expenses/{expenseId}.
 */

import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Modal from '@/components/common/Modal';
import { updatePersonalExpense } from '@/services/personalExpenseService';
import { getFriends } from '@/services/friendService';
import type {
  PersonalExpenseResponse,
  SplitType,
  UpdatePersonalExpenseRequest,
} from '@/types/personalExpenses';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';

interface EditPersonalExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: PersonalExpenseResponse;
  currentUserId: string | null;
  onUpdated: (expense: PersonalExpenseResponse) => void;
}

interface ParticipantOption {
  userId: string;
  name: string;
  email?: string;
  isCurrentUser: boolean;
}

interface ParticipantEntry {
  userId: string;
  amount: string;
  percentage: string;
}

export default function EditPersonalExpenseModal({
  isOpen,
  onClose,
  expense,
  currentUserId,
  onUpdated,
}: EditPersonalExpenseModalProps) {
  const isCreator = currentUserId === expense.createdBy;

  const [friends, setFriends] = useState<Friend[]>([]);
  const [friendsLoading, setFriendsLoading] = useState(false);

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
  const [splitType, setSplitType] = useState<SplitType>(expense.splitType);
  const [participants, setParticipants] = useState<ParticipantEntry[]>(buildParticipants);
  const [apiError, setApiError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  // Load friends
  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    queueMicrotask(() => {
      setFriendsLoading(true);

      getFriends()
        .then((data) => {
          if (isMounted) {
            setFriends(data);
          }
        })
        .catch((err) => {
          console.warn('Failed to load friends for edit modal:', err);
        })
        .finally(() => {
          if (isMounted) {
            setFriendsLoading(false);
          }
        });
    });

    return () => {
      isMounted = false;
    };
  }, [isOpen]);

  // Re-sync when opened
  useEffect(() => {
    if (isOpen) {
      queueMicrotask(() => {
        setDescription(expense.description);
        setTotalAmount(String(expense.totalAmount));
        setPaidBy(expense.paidBy);
        setSplitType(expense.splitType);
        setParticipants(buildParticipants());
        setApiError('');
        setFieldErrors({});
      });
    }
  }, [isOpen, expense, buildParticipants]);

  const availablePeople = useMemo<ParticipantOption[]>(() => {
    const list: ParticipantOption[] = [];

    if (currentUserId) {
      list.push({
        userId: currentUserId,
        name: 'You',
        isCurrentUser: true,
      });
    }

    // Add friends
    friends.forEach((f) => {
      if (!list.some((item) => item.userId === f.userId)) {
        list.push({
          userId: f.userId,
          name: f.name || f.email || 'Friend',
          email: f.email,
          isCurrentUser: false,
        });
      }
    });

    // Also include any participant from existing expense if not in friends list
    expense.splits.forEach((s) => {
      if (!list.some((item) => item.userId === s.userId)) {
        list.push({
          userId: s.userId,
          name: 'Participant',
          isCurrentUser: false,
        });
      }
    });

    return list;
  }, [currentUserId, friends, expense.splits]);

  const personNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    availablePeople.forEach((p) => {
      map[p.userId] = p.name;
    });
    return map;
  }, [availablePeople]);

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
    if (!totalAmount || isNaN(amount) || amount < 0.01) {
      errors.totalAmount = 'Amount must be at least 0.01.';
    }

    if (!paidBy) errors.paidBy = 'Payer is required.';

    if (participants.length < 2) {
      errors.participants = 'Select at least 2 participants for a personal expense.';
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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError('');
    if (!isCreator || !validate()) return;

    const splits = participants.map((p) => ({
      userId: p.userId,
      ...(splitType === 'EXACT' ? { amount: parseFloat(p.amount) } : {}),
      ...(splitType === 'PERCENTAGE' ? { percentage: parseFloat(p.percentage) } : {}),
    }));

    const payload: UpdatePersonalExpenseRequest = {
      description: description.trim(),
      totalAmount: parseFloat(totalAmount),
      paidBy,
      splitType,
      splits,
    };

    setIsLoading(true);
    try {
      const updated = await updatePersonalExpense(expense.id, payload);
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
      title="Edit Personal Expense"
      widthClass="modal__dialog--lg"
    >
      {!isCreator && (
        <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
          Only the expense creator can edit this expense.
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
            {apiError}
          </div>
        )}

        {/* Description */}
        <div className="form-field">
          <label htmlFor="edit-p-exp-description" className="form-field__label">
            Description <span className="form-field__required">*</span>
          </label>
          <input
            id="edit-p-exp-description"
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

        {/* Total Amount */}
        <div className="form-field">
          <label htmlFor="edit-p-exp-amount" className="form-field__label">
            Total Amount (₹) <span className="form-field__required">*</span>
          </label>
          <input
            id="edit-p-exp-amount"
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
          <label htmlFor="edit-p-exp-paidby" className="form-field__label">
            Paid by <span className="form-field__required">*</span>
          </label>
          <select
            id="edit-p-exp-paidby"
            className={`form-field__input${fieldErrors.paidBy ? ' form-field__input--error' : ''}`}
            value={paidBy}
            onChange={(e) => setPaidBy(e.target.value)}
            disabled={isLoading || !isCreator}
          >
            <option value="">— Select payer —</option>
            {availablePeople.map((p) => (
              <option key={p.userId} value={p.userId}>
                {personNames[p.userId] || p.name}
              </option>
            ))}
          </select>
          {fieldErrors.paidBy && (
            <span className="form-field__error">{fieldErrors.paidBy}</span>
          )}
        </div>

        {/* Split Type */}
        <div className="form-field">
          <label htmlFor="edit-p-exp-splittype" className="form-field__label">
            Split type <span className="form-field__required">*</span>
          </label>
          <select
            id="edit-p-exp-splittype"
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
            Participants <span className="form-field__required">* (select at least 2)</span>
          </span>
          {friendsLoading ? (
            <p className="expense-form__no-members">Loading participants...</p>
          ) : (
            <div className="expense-form__participants">
              {availablePeople.map((p) => {
                const checked = selectedUserIds.has(p.userId);
                const entry = participants.find((item) => item.userId === p.userId);
                return (
                  <div key={p.userId} className="expense-form__participant-row">
                    <label className="expense-form__participant-check">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleParticipant(p.userId)}
                        disabled={isLoading || !isCreator}
                      />
                      <span className="expense-form__participant-name">
                        {p.name}
                        {p.email && <small style={{ color: 'var(--color-text-muted)', marginLeft: '0.4rem' }}>({p.email})</small>}
                      </span>
                    </label>

                    {checked && splitType === 'EXACT' && (
                      <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        className="form-field__input expense-form__participant-input"
                        placeholder="Amount"
                        value={entry?.amount ?? ''}
                        onChange={(e) => updateParticipantAmount(p.userId, e.target.value)}
                        disabled={isLoading || !isCreator}
                        aria-label={`Amount for ${personNames[p.userId] || p.name}`}
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
                        onChange={(e) => updateParticipantPercentage(p.userId, e.target.value)}
                        disabled={isLoading || !isCreator}
                        aria-label={`Percentage for ${personNames[p.userId] || p.name}`}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {splitType === 'EXACT' && participants.length > 0 && (
            <p
              className={`expense-form__live-total${
                exactValid ? ' expense-form__live-total--ok' : ' expense-form__live-total--warn'
              }`}
            >
              Entered: ₹{exactSum.toFixed(2)} / ₹{parsedTotal > 0 ? parsedTotal.toFixed(2) : '0.00'}
              {exactValid ? ' ✓' : ''}
            </p>
          )}

          {splitType === 'PERCENTAGE' && participants.length > 0 && (
            <p
              className={`expense-form__live-total${
                percentageValid ? ' expense-form__live-total--ok' : ' expense-form__live-total--warn'
              }`}
            >
              Total %: {percentageSum.toFixed(2)}% / 100%
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
