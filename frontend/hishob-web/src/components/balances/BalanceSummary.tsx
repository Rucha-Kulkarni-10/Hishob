/**
 * BalanceSummary — current user's own balance displayed prominently.
 *
 * Finds the current user's entry in the balances array by userId === currentUserId.
 * Never assumes positional order.
 *
 * Displays:
 *   - Primary headline: "You are owed", "You owe", or "You're settled up"
 *   - Net balance amount (always shown as absolute value with sign determined by label)
 *   - Supporting detail: Total paid / Your share / Net balance
 */

import type { MemberBalance } from '@/types/balances';

interface BalanceSummaryProps {
  balances: MemberBalance[];
  currentUserId: string | null;
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function BalanceSummary({ balances, currentUserId }: BalanceSummaryProps) {
  const mine = currentUserId
    ? balances.find((b) => b.userId === currentUserId)
    : null;

  if (!mine) {
    return (
      <div className="balance-summary balance-summary--settled">
        <p className="balance-summary__headline">Your balance</p>
        <p className="balance-summary__amount">—</p>
        <p className="balance-summary__hint">Your balance could not be determined.</p>
      </div>
    );
  }

  const isGets = mine.status === 'GETS';
  const isOwes = mine.status === 'OWES';

  const modifier = isGets ? 'gets' : isOwes ? 'owes' : 'settled';
  const headline = isGets ? 'You are owed' : isOwes ? 'You owe' : "You're settled up";

  return (
    <div className={`balance-summary balance-summary--${modifier}`}>
      <p className="balance-summary__headline">{headline}</p>
      <p className="balance-summary__amount">
        {isGets || isOwes ? `₹${formatAmount(mine.netBalance)}` : '₹0.00'}
      </p>

      <div className="balance-summary__detail">
        <div className="balance-summary__detail-item">
          <span className="balance-summary__detail-label">Total paid</span>
          <span className="balance-summary__detail-value">₹{formatAmount(mine.totalPaid)}</span>
        </div>
        <div className="balance-summary__detail-item">
          <span className="balance-summary__detail-label">Your share</span>
          <span className="balance-summary__detail-value">₹{formatAmount(mine.totalShare)}</span>
        </div>
        <div className="balance-summary__detail-item">
          <span className="balance-summary__detail-label">Net balance</span>
          <span
            className={`balance-summary__detail-value balance-summary__detail-value--${modifier}`}
          >
            {mine.netBalance >= 0 ? '+' : ''}₹{mine.netBalance.toLocaleString('en-IN', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}
          </span>
        </div>
      </div>
    </div>
  );
}
