/**
 * AddPersonalExpenseModal — form to create a new personal expense.
 *
 * Fields: description, totalAmount, paidBy, splitType, participants.
 *
 * Split types:
 *   EQUAL      — select participants, backend calculates amounts.
 *   EXACT      — amount input per participant, live sum validation.
 *   PERCENTAGE — percentage input per participant, live sum = 100% validation.
 *
 * Validation:
 *   - Description required, max 300 chars
 *   - Amount >= 0.01
 *   - Payer selected and must be among participants
 *   - At least 2 participants required
 *   - EXACT: sum of split amounts must equal totalAmount
 *   - PERCENTAGE: sum of percentages must equal 100
 */

import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Modal from '@/components/common/Modal';
import { createPersonalExpense } from '@/services/personalExpenseService';
import { getFriends } from '@/services/friendService';
import type { CreatePersonalExpenseRequest, PersonalExpenseResponse, SplitType } from '@/types/personalExpenses';
import type { Friend } from '@/types/friends';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage } from '@/utils/errorUtils';

interface AddPersonalExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (expense: PersonalExpenseResponse) => void;
  preselectedFriendId?: string;
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

const INITIAL_SPLIT: SplitType = 'EQUAL';

export default function AddPersonalExpenseModal({
  isOpen,
  onClose,
  onCreated,
  preselectedFriendId,
}: AddPersonalExpenseModalProps) {
  const { user } = useAuth();

  const [friends, setFriends] = useState<Friend[]>([]);
  const [friendsLoading, setFriendsLoading] = useState(false);

  const [description, setDescription] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [paidBy, setPaidBy] = useState('');
  const [splitType, setSplitType] = useState<SplitType>(INITIAL_SPLIT);
  const [participants, setParticipants] = useState<ParticipantEntry[]>([]);
  const [apiError, setApiError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  // Load friends list when modal opens
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
          console.warn('Failed to load friends for expense form:', err);
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

  // Build list of selectable people: Current User + Accepted Friends
  const availablePeople = useMemo<ParticipantOption[]>(() => {
    const list: ParticipantOption[] = [];

    if (user) {
      list.push({
        userId: user.id,
        name: user.name ? `${user.name} (You)` : 'You',
        email: user.email,
        isCurrentUser: true,
      });
    }

    friends.forEach((f) => {
      list.push({
        userId: f.userId,
        name: f.name || f.email || 'Friend',
        email: f.email,
        isCurrentUser: false,
      });
    });

    return list;
  }, [user, friends]);

  // Map of userId -> display name
  const personNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    availablePeople.forEach((p) => {
      map[p.userId] = p.name;
    });
    return map;
  }, [availablePeople]);

  // Set default participants (Current User + preselected friend or first friend)
  useEffect(() => {
    if (!isOpen) return;

    queueMicrotask(() => {
      const initial: ParticipantEntry[] = [];
      if (user) {
        initial.push({ userId: user.id, amount: '', percentage: '' });
        if (!paidBy) {
          setPaidBy(user.id);
        }
      }

      if (preselectedFriendId && preselectedFriendId !== user?.id) {
        if (!initial.some((p) => p.userId === preselectedFriendId)) {
          initial.push({ userId: preselectedFriendId, amount: '', percentage: '' });
        }
      } else if (friends.length > 0 && initial.length === 1) {
        initial.push({ userId: friends[0].userId, amount: '', percentage: '' });
      }

      setParticipants(initial);
    });
  }, [isOpen, user, preselectedFriendId, friends, paidBy]);

  const reset = useCallback(() => {
    setDescription('');
    setTotalAmount('');
    setPaidBy(user?.id || '');
    setSplitType(INITIAL_SPLIT);
    setParticipants([]);
    setApiError('');
    setFieldErrors({});
    setIsLoading(false);
  }, [user?.id]);

  const handleClose = () => {
    reset();
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

  // Live calculation checks
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

  // Validation
  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    if (!description.trim()) errors.description = 'Description is required.';
    if (description.trim().length > 300) errors.description = 'Description must be 300 characters or fewer.';

    const amount = parseFloat(totalAmount);
    if (!totalAmount || isNaN(amount) || amount < 0.01) {
      errors.totalAmount = 'Amount must be at least 0.01.';
    }

    if (!paidBy) {
      errors.paidBy = 'Payer is required.';
    }

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
    if (!validate()) return;

    const splits = participants.map((p) => ({
      userId: p.userId,
      ...(splitType === 'EXACT' ? { amount: parseFloat(p.amount) } : {}),
      ...(splitType === 'PERCENTAGE' ? { percentage: parseFloat(p.percentage) } : {}),
    }));

    const payload: CreatePersonalExpenseRequest = {
      description: description.trim(),
      totalAmount: parseFloat(totalAmount),
      paidBy,
      splitType,
      splits,
    };

    setIsLoading(true);
    try {
      const created = await createPersonalExpense(payload);
      onCreated(created);
      reset();
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
      title="Add Personal Expense"
      widthClass="modal__dialog--lg"
    >
      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
            {apiError}
          </div>
        )}

        {/* Description */}
        <div className="form-field">
          <label htmlFor="p-exp-description" className="form-field__label">
            Description <span className="form-field__required">*</span>
          </label>
          <input
            id="p-exp-description"
            type="text"
            className={`form-field__input${fieldErrors.description ? ' form-field__input--error' : ''}`}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Movie tickets, Dinner, Groceries"
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
          <label htmlFor="p-exp-amount" className="form-field__label">
            Total Amount (₹) <span className="form-field__required">*</span>
          </label>
          <input
            id="p-exp-amount"
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
          <label htmlFor="p-exp-paidby" className="form-field__label">
            Paid by <span className="form-field__required">*</span>
          </label>
          <select
            id="p-exp-paidby"
            className={`form-field__input${fieldErrors.paidBy ? ' form-field__input--error' : ''}`}
            value={paidBy}
            onChange={(e) => setPaidBy(e.target.value)}
            disabled={isLoading}
          >
            <option value="">— Select payer —</option>
            {availablePeople.map((p) => (
              <option key={p.userId} value={p.userId}>
                {p.name}
              </option>
            ))}
          </select>
          {fieldErrors.paidBy && (
            <span className="form-field__error">{fieldErrors.paidBy}</span>
          )}
        </div>

        {/* Split Type */}
        <div className="form-field">
          <label htmlFor="p-exp-splittype" className="form-field__label">
            Split type <span className="form-field__required">*</span>
          </label>
          <select
            id="p-exp-splittype"
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
            Participants <span className="form-field__required">* (select at least 2)</span>
          </span>

          {friendsLoading ? (
            <p className="expense-form__no-members">Loading friends...</p>
          ) : availablePeople.length <= 1 ? (
            <p className="expense-form__no-members">
              You need to have at least one friend to add personal expenses. Add friends from the Friends page first.
            </p>
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
                        disabled={isLoading}
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
                        disabled={isLoading}
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
                        disabled={isLoading}
                        aria-label={`Percentage for ${personNames[p.userId] || p.name}`}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* Live totals */}
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
            disabled={isLoading || availablePeople.length <= 1}
            aria-busy={isLoading}
          >
            {isLoading ? 'Adding…' : 'Add expense'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
