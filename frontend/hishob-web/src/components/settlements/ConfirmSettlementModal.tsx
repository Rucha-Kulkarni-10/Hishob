/**
 * ConfirmSettlementModal — dialog for recording a partial or full settlement.
 *
 * Displays:
 *   - Sender name (fromUserName)
 *   - Receiver name (toUserName)
 *   - Outstanding balance
 *   - Editable settlement amount (default: full outstanding balance)
 *   - Live remaining balance calculation
 *   - Optional notes textarea
 *   - Frontend validation (amount > 0, amount <= outstanding, decimal limits, self-settlement protection)
 *   - Dynamic submit button ("Settle ₹400", etc.)
 */

import { useState, useMemo, type FormEvent } from 'react';
import Modal from '@/components/common/Modal';
import type { SettlementSuggestion } from '@/types/balances';

interface ConfirmSettlementModalProps {
  suggestion: SettlementSuggestion;
  fromUserName: string;
  toUserName: string;
  onConfirm: (amount: number, notes?: string) => void;
  onCancel: () => void;
  isSubmitting: boolean;
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

export default function ConfirmSettlementModal({
  suggestion,
  fromUserName,
  toUserName,
  onConfirm,
  onCancel,
  isSubmitting,
}: ConfirmSettlementModalProps) {
  const [amountStr, setAmountStr] = useState(String(suggestion.amount));
  const [notes, setNotes] = useState('');
  const [touched, setTouched] = useState(false);

  const outstanding = suggestion.amount;
  const isSelfSettlement = suggestion.fromUserId === suggestion.toUserId;

  // Validation & Live Remaining Calculation
  const { parsedAmount, isValid, validationError, remaining } = useMemo(() => {
    if (isSelfSettlement) {
      return {
        parsedAmount: 0,
        isValid: false,
        validationError: 'Cannot settle with yourself. Payer and recipient cannot be the same user.',
        remaining: null,
      };
    }

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

    // Two decimal places check
    const decimalParts = trimmed.split('.');
    if (decimalParts.length > 2 || (decimalParts[1] && decimalParts[1].length > 2)) {
      return {
        parsedAmount: num,
        isValid: false,
        validationError: 'Amount can have at most 2 decimal places.',
        remaining: null,
      };
    }

    // Check exceeding outstanding amount (tolerance 0.0001 for floats)
    if (num > outstanding + 0.0001) {
      return {
        parsedAmount: num,
        isValid: false,
        validationError: `Amount cannot exceed the outstanding balance of ₹${formatAmount(outstanding)}.`,
        remaining: null,
      };
    }

    // Accurate calculation avoiding floating point artifacts
    const rem = Math.max(0, Math.round((outstanding - num) * 100) / 100);

    return {
      parsedAmount: num,
      isValid: true,
      validationError: null,
      remaining: rem,
    };
  }, [amountStr, outstanding, isSelfSettlement]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!isValid || isSubmitting || isSelfSettlement) {
      return;
    }
    // Submit exact entered amount rounded to 2 decimals
    const finalAmount = Math.round(parsedAmount * 100) / 100;
    onConfirm(finalAmount, notes.trim() || undefined);
  };

  const buttonLabel = isSubmitting
    ? 'Recording…'
    : isValid
    ? `Settle ₹${formatButtonAmount(parsedAmount)}`
    : 'Settle Up';

  return (
    <Modal
      isOpen={true}
      onClose={isSubmitting ? () => {} : onCancel}
      title="Settle Up"
      widthClass="modal__dialog--md"
    >
      <form onSubmit={handleSubmit} noValidate>
        {isSelfSettlement && (
          <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
            Cannot settle with yourself. Payer and recipient cannot be the same user.
          </div>
        )}

        <div className="settlement-confirm">
          {/* Parties */}
          <div className="settlement-confirm__parties">
            <div className="settlement-confirm__party">
              <span className="settlement-confirm__label">From:</span>
              <span className="settlement-confirm__value">{fromUserName}</span>
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
            <label htmlFor="settle-amount" className="form-field__label">
              Amount you're paying <span className="form-field__required">*</span>
            </label>
            <div className="settlement-input-wrapper">
              <span className="settlement-input-prefix" aria-hidden="true">₹</span>
              <input
                id="settle-amount"
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
                disabled={isSubmitting || isSelfSettlement}
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
            <label htmlFor="settlement-notes" className="form-field__label">
              Notes <span style={{ color: 'var(--color-text-muted)', fontWeight: 400 }}>(optional)</span>
            </label>
            <textarea
              id="settlement-notes"
              className="form-field__input"
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Cash payment, Google Pay, UPI..."
              disabled={isSubmitting || isSelfSettlement}
              maxLength={500}
            />
          </div>
        </div>

        {/* Modal actions */}
        <div className="modal__actions" style={{ marginTop: '1.5rem' }}>
          <button
            type="button"
            className="btn btn--ghost"
            onClick={onCancel}
            disabled={isSubmitting}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn--primary"
            disabled={isSubmitting || !isValid || isSelfSettlement}
            aria-busy={isSubmitting}
          >
            {buttonLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
