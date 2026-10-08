/**
 * EditPersonalExpenseModal — pre-filled form to update an existing personal expense.
 *
 * Only enabled when the current user is the expense creator.
 * Submits PUT /api/personal-expenses/{expenseId}.
 *
 * Features:
 *   - Searchable participant selection
 *   - Select all / Clear selection actions
 *   - Clean payer selection
 *   - Error handling with retry on friend fetch failure
 *   - Avoid duplicate friend requests if initialFriends is provided
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
import {
  type ExpenseCategory,
  EXPENSE_CATEGORIES,
  CATEGORY_METADATA,
} from '@/types/expenses';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';

interface EditPersonalExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: PersonalExpenseResponse;
  currentUserId: string | null;
  onUpdated: (expense: PersonalExpenseResponse) => void;
  initialFriends?: Friend[];
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

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function safeDisplayName(name: string | null | undefined, email: string | null | undefined, isCurrentUser: boolean): string {
  if (isCurrentUser) {
    return name?.trim() ? `${name.trim()} (You)` : 'You';
  }
  if (name && name.trim() && !UUID_REGEX.test(name.trim())) {
    return name.trim();
  }
  if (email && email.trim() && !UUID_REGEX.test(email.trim())) {
    return email.trim();
  }
  return 'Unknown member';
}

export default function EditPersonalExpenseModal({
  isOpen,
  onClose,
  expense,
  currentUserId,
  onUpdated,
  initialFriends,
}: EditPersonalExpenseModalProps) {
  const isCreator = currentUserId === expense.createdBy;

  const [fetchedFriends, setFetchedFriends] = useState<Friend[]>([]);
  const [friendsLoading, setFriendsLoading] = useState(false);
  const [friendsError, setFriendsError] = useState<string | null>(null);

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
  const [participantSearch, setParticipantSearch] = useState('');
  const [apiError, setApiError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  const friends = useMemo(() => {
    if (initialFriends && initialFriends.length > 0 && !friendsError) {
      return initialFriends;
    }
    return fetchedFriends;
  }, [initialFriends, fetchedFriends, friendsError]);

  const loadFriends = useCallback(async () => {
    setFriendsLoading(true);
    setFriendsError(null);
    try {
      const data = await getFriends();
      setFetchedFriends(data);
    } catch (err) {
      setFriendsError(getErrorMessage(err));
    } finally {
      setFriendsLoading(false);
    }
  }, []);

  // Load friends if not provided via initialFriends
  useEffect(() => {
    if (!isOpen) return;

    if (initialFriends && initialFriends.length > 0) {
      return;
    }

    queueMicrotask(() => {
      void loadFriends();
    });
  }, [isOpen, initialFriends, loadFriends]);

  // Re-sync when opened or expense changes
  useEffect(() => {
    if (isOpen) {
      queueMicrotask(() => {
        setDescription(expense.description);
        setTotalAmount(String(expense.totalAmount));
        setPaidBy(expense.paidBy);
        setCategory(expense.category || 'OTHER');
        setSplitType(expense.splitType);
        setParticipants(buildParticipants());
        setParticipantSearch('');
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
          name: safeDisplayName(f.name, f.email, false),
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
          name: 'Unknown member',
          isCurrentUser: false,
        });
      }
    });

    return list;
  }, [currentUserId, friends, expense.splits]);

  // Filtered people by participant search query (case-insensitive on name or email)
  const filteredPeople = useMemo(() => {
    const q = participantSearch.trim().toLowerCase();
    if (!q) return availablePeople;
    return availablePeople.filter((p) => {
      return (
        p.name.toLowerCase().includes(q) ||
        (p.email && p.email.toLowerCase().includes(q))
      );
    });
  }, [availablePeople, participantSearch]);

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

  const handleSelectAll = () => {
    const all = availablePeople.map((p) => {
      const existing = participants.find((item) => item.userId === p.userId);
      return existing ?? { userId: p.userId, amount: '', percentage: '' };
    });
    setParticipants(all);
  };

  const handleClearParticipants = () => {
    setParticipants([]);
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
      category,
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

        {/* Category */}
        <div className="form-field">
          <label htmlFor="edit-p-exp-category" className="form-field__label">
            Category <span className="form-field__required">*</span>
          </label>
          <select
            id="edit-p-exp-category"
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
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.25rem' }}>
            <span className="form-field__label" style={{ marginBottom: 0 }}>
              Participants <span className="form-field__required">* ({participants.length} selected, min 2)</span>
            </span>
            {isCreator && (
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={handleSelectAll}
                  disabled={isLoading}
                  style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
                >
                  Select all
                </button>
                {participants.length > 0 && (
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    onClick={handleClearParticipants}
                    disabled={isLoading}
                    style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
                  >
                    Clear selection
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Friend loading error */}
          {friendsError && (
            <div className="alert alert--error" role="alert" style={{ marginBottom: '0.75rem' }}>
              <span>Failed to load friends: {friendsError}</span>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => void loadFriends()}
                style={{ marginLeft: '1rem' }}
              >
                Retry
              </button>
            </div>
          )}

          {friendsLoading ? (
            <p className="expense-form__no-members">Loading participants...</p>
          ) : (
            <>
              {/* Participant search filter when multiple people exist */}
              {availablePeople.length > 2 && (
                <div style={{ marginBottom: '0.5rem' }}>
                  <input
                    type="search"
                    className="form-field__input"
                    placeholder="Search people by name or email…"
                    value={participantSearch}
                    onChange={(e) => setParticipantSearch(e.target.value)}
                    disabled={isLoading || !isCreator}
                    style={{ fontSize: '0.875rem', padding: '0.4rem 0.75rem' }}
                  />
                </div>
              )}

              <div className="expense-form__participants">
                {filteredPeople.length === 0 ? (
                  <p className="expense-form__no-members">No people match "{participantSearch}".</p>
                ) : (
                  filteredPeople.map((p) => {
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
                  })
                )}
              </div>
            </>
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
