/**
 * ConfirmPersonalSettlementModal — dialog for recording a personal settlement.
 *
 * Displays:
 *   - Payer (You)
 *   - Receiver (Friend name)
 *   - Outstanding balance
 *   - Editable settlement amount (default: full outstanding balance, cannot exceed)
 *   - Live remaining balance calculation
 *   - Optional notes textarea
 *   - Calls POST /api/personal-settlements ({ toUserId, amount, notes })
 */

import { useState, useMemo, type FormEvent } from 'react';
import Modal from '@/components/common/Modal';
import { createPersonalSettlement } from '@/services/personalSettlementService';
import type { PersonalDebtSimplificationSuggestion } from '@/types/personalBalances';
import { getErrorMessage } from '@/utils/errorUtils';

interface ConfirmPersonalSettlementModalProps {
  isOpen: boolean;
  onClose: () => void;
  suggestion: PersonalDebtSimplificationSuggestion;
  toUserName: string;
  onSuccess: () => void;
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatButtonAmount(amount: number): string {
  return amount % 1 === 0
    ? amount.toLocaleString('en-IN')
    : amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function ConfirmPersonalSettlementModal({
  isOpen,
  onClose,
  suggestion,
  toUserName,
  onSuccess,
}: ConfirmPersonalSettlementModalProps) {
  const [amountStr, setAmountStr] = useState(String(suggestion.amount));
  const [notes, setNotes] = useState('');
  const [touched, setTouched] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const outstanding = suggestion.amount;

  const { parsedAmount, isValid, validationError, remaining } = useMemo(() => {
    const trimmed = amountStr.trim();
    if (!trimmed) {
      return {
        parsedAmount: 0,
        isValid: false,
        validationError: 'Amount is required.',
        remaining: null,
      };
    }

    const num = parseFloat(trimmed);
    if (isNaN(num) || !isFinite(num)) {
      return {
        parsedAmount: 0,
        isValid: false,
        validationError: 'Please enter a valid numeric amount.',
        remaining: null,
      };
    }

    if (num <= 0) {
      return {
        parsedAmount: num,
        isValid: false,
        validationError: 'Amount must be greater than 0.',
        remaining: null,
      };
    }

    const decimalParts = trimmed.split('.');
    if (decimalParts.length > 2 || (decimalParts[1] && decimalParts[1].length > 2)) {
      return {
        parsedAmount: num,
        isValid: false,
        validationError: 'Amount can have at most 2 decimal places.',
        remaining: null,
      };
    }

    if (num > outstanding + 0.0001) {
      return {
        parsedAmount: num,
        isValid: false,
        validationError: `Amount cannot exceed the outstanding balance of ₹${formatAmount(outstanding)}.`,
        remaining: null,
      };
    }

    const rem = Math.max(0, Math.round((outstanding - num) * 100) / 100);

    return {
      parsedAmount: num,
      isValid: true,
      validationError: null,
      remaining: rem,
    };
  }, [amountStr, outstanding]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!isValid || isSubmitting) return;

    setApiError(null);
    setIsSubmitting(true);

    try {
      const finalAmount = Math.round(parsedAmount * 100) / 100;
      await createPersonalSettlement({
        toUserId: suggestion.toUserId,
        amount: finalAmount,
        notes: notes.trim() || undefined,
      });

      onSuccess();
      onClose();
    } catch (err) {
      setApiError(getErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  const buttonLabel = isSubmitting
    ? 'Recording…'
    : isValid
    ? `Settle ₹${formatButtonAmount(parsedAmount)}`
    : 'Settle Up';

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSubmitting ? () => {} : onClose}
      title="Settle Up"
      widthClass="modal__dialog--md"
    >
      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
            {apiError}
          </div>
        )}

        <div className="settlement-confirm">
          {/* Parties */}
          <div className="settlement-confirm__parties">
            <div className="settlement-confirm__party">
              <span className="settlement-confirm__label">From:</span>
              <span className="settlement-confirm__value">You</span>
            </div>
            <div className="settlement-confirm__party">
              <span className="settlement-confirm__label">To:</span>
              <span className="settlement-confirm__value">{toUserName}</span>
            </div>
          </div>

          {/* Outstanding amount */}
          <div className="settlement-confirm__row settlement-confirm__row--highlight">
            <span className="settlement-confirm__label">Outstanding balance:</span>
            <span className="settlement-confirm__value settlement-confirm__value--outstanding">
              ₹{formatAmount(outstanding)}
            </span>
          </div>

          {/* Amount input */}
          <div className="form-field" style={{ marginTop: '1rem', marginBottom: '0.75rem' }}>
            <label htmlFor="p-settle-amount" className="form-field__label">
              Amount you're paying <span className="form-field__required">*</span>
            </label>
            <div className="settlement-input-wrapper">
              <span className="settlement-input-prefix" aria-hidden="true">₹</span>
              <input
                id="p-settle-amount"
                type="number"
                step="0.01"
                min="0.01"
                max={outstanding}
                className={`form-field__input settlement-input${(touched || amountStr) && validationError ? ' form-field__input--error' : ''}`}
                value={amountStr}
                onChange={(e) => {
                  setAmountStr(e.target.value);
                  setTouched(true);
                }}
                disabled={isSubmitting}
                placeholder="0.00"
                autoComplete="off"
                required
              />
            </div>
            {(touched || amountStr) && validationError && (
              <span className="form-field__error" role="alert" style={{ marginTop: '0.25rem', display: 'block' }}>
                {validationError}
              </span>
            )}
          </div>

          {/* Remaining after payment */}
          {remaining !== null && (
            <div className="settlement-confirm__row settlement-confirm__remaining">
              <span className="settlement-confirm__label">Remaining after payment:</span>
              <span className={`settlement-confirm__value ${remaining === 0 ? 'settlement-confirm__value--settled' : ''}`}>
                ₹{formatAmount(remaining)}
                {remaining === 0 && (
                  <span className="badge badge--success" style={{ marginLeft: '0.5rem' }}>
                    Fully settled
                  </span>
                )}
              </span>
            </div>
          )}

          {/* Notes */}
          <div className="form-field" style={{ marginTop: '1rem' }}>
            <label htmlFor="p-settlement-notes" className="form-field__label">
              Notes <span style={{ color: 'var(--color-text-muted)', fontWeight: 400 }}>(optional)</span>
            </label>
            <textarea
              id="p-settlement-notes"
              className="form-field__input"
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Cash payment, Google Pay, UPI..."
              disabled={isSubmitting}
              maxLength={500}
            />
          </div>
        </div>

        {/* Modal actions */}
        <div className="modal__actions" style={{ marginTop: '1.5rem' }}>
          <button
            type="button"
            className="btn btn--ghost"
            onClick={onClose}
            disabled={isSubmitting}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn--primary"
            disabled={isSubmitting || !isValid}
            aria-busy={isSubmitting}
          >
            {buttonLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
