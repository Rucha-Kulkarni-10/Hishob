/**
 * SettlementSuggestions — displays simplified debt transaction cards.
 *
 * Filter out self-settlements (fromUserId === toUserId).
 *
 * Card structure:
 * - When current user owes:
 *   "You owe"
 *   [Recipient Name]
 *   ₹[Amount]
 *   [ Settle Up ] button
 *
 * - When someone owes current user (reverse case):
 *   "[Debtor Name] owes you"
 *   ₹[Amount]
 *   [ Record Payment ] button (informational / disabled)
 *
 * - When neither (third-party):
 *   "[Debtor Name] owes"
 *   [Recipient Name]
 *   ₹[Amount]
 */

import { useMemo } from 'react';
import type { SettlementSuggestion } from '@/types/balances';

interface SettlementSuggestionsProps {
  suggestions: SettlementSuggestion[];
  memberNames: Record<string, string>;
  currentUserId: string | null;
  onSettleUp?: (suggestion: SettlementSuggestion) => void;
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function SettlementSuggestions({
  suggestions,
  memberNames,
  currentUserId,
  onSettleUp,
}: SettlementSuggestionsProps) {
  // Self-settlement protection: filter out any invalid or self-settlement suggestions
  const validSuggestions = useMemo(() => {
    return suggestions.filter(
      (s) => s.fromUserId && s.toUserId && s.fromUserId !== s.toUserId && s.amount > 0
    );
  }, [suggestions]);

  if (validSuggestions.length === 0) {
    return (
      <div className="settlement-empty">
        <span className="settlement-empty__icon">🎉</span>
        <p className="settlement-empty__text">Everyone is settled up.</p>
      </div>
    );
  }

  return (
    <div className="settlement-suggestions-wrapper">
      <div className="settlement-cards-grid">
        {validSuggestions.map((s, i) => {
          const fromIsMe = currentUserId ? s.fromUserId === currentUserId : false;
          const toIsMe = currentUserId ? s.toUserId === currentUserId : false;

          const fromName = memberNames[s.fromUserId] || 'Unknown member';
          const toName = memberNames[s.toUserId] || 'Unknown member';

          return (
            <div
              key={`${s.fromUserId}-${s.toUserId}-${i}`}
              className={`settlement-card${
                fromIsMe
                  ? ' settlement-card--i-owe'
                  : toIsMe
                  ? ' settlement-card--owes-me'
                  : ' settlement-card--neutral'
              }`}
            >
              {fromIsMe ? (
                <>
                  <div className="settlement-card__header">
                    <span className="settlement-card__pretitle">You pay</span>
                    <h4 className="settlement-card__name">{toName}</h4>
                  </div>
                  <div className="settlement-card__amount">
                    ₹{formatAmount(s.amount)}
                  </div>
                  {onSettleUp && (
                    <div className="settlement-card__actions">
                      <button
                        type="button"
                        className="btn btn--primary btn--sm settlement-card__btn"
                        onClick={() => onSettleUp(s)}
                        aria-label={`Settle up ₹${formatAmount(s.amount)} with ${toName}`}
                      >
                        Settle Up
                      </button>
                    </div>
                  )}
                </>
              ) : toIsMe ? (
                <>
                  <div className="settlement-card__header">
                    <span className="settlement-card__pretitle">{fromName} pays you</span>
                  </div>
                  <div className="settlement-card__amount">
                    ₹{formatAmount(s.amount)}
                  </div>
                  <div className="settlement-card__actions">
                    <button
                      type="button"
                      className="btn btn--secondary btn--sm settlement-card__btn"
                      disabled
                      title="Only the payer who owes can initiate settlement payment"
                    >
                      Record Payment
                    </button>
                  </div>
                </>
              ) : (
                <>
                  <div className="settlement-card__header">
                    <span className="settlement-card__pretitle">{fromName} pays</span>
                    <h4 className="settlement-card__name">{toName}</h4>
                  </div>
                  <div className="settlement-card__amount">
                    ₹{formatAmount(s.amount)}
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>

      <p className="settlement-suggestions__note">
        These are suggested transactions to settle all debts. Click "Settle Up" to record a payment.
      </p>
    </div>
  );
}
