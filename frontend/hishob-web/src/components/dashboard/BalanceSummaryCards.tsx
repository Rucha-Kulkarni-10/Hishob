/**
 * BalanceSummaryCards — Dashboard balance summary
 * 
 * Displays three cards showing user's financial status across all groups:
 * - You Owe (total negative balances)
 * - You Are Owed (total positive balances)
 * - Net Balance (difference)
 * 
 * Amounts formatted in INR.
 */

interface BalanceSummaryCardsProps {
  youOwe: number;        // Total amount user owes (always positive for display)
  youAreOwed: number;    // Total amount owed to user (always positive)
  netBalance: number;    // youAreOwed - youOwe (can be negative)
  isLoading?: boolean;
  hasError?: boolean;
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function BalanceSummaryCards({
  youOwe,
  youAreOwed,
  netBalance,
  isLoading = false,
  hasError = false,
}: BalanceSummaryCardsProps) {
  if (isLoading) {
    return (
      <div className="dashboard-summary-cards">
        <div className="dashboard-stat-card">
          <div className="dashboard-stat-card__loading">Loading...</div>
        </div>
        <div className="dashboard-stat-card">
          <div className="dashboard-stat-card__loading">Loading...</div>
        </div>
        <div className="dashboard-stat-card">
          <div className="dashboard-stat-card__loading">Loading...</div>
        </div>
      </div>
    );
  }

  if (hasError) {
    return (
      <div className="dashboard-summary-cards">
        <div className="dashboard-stat-card dashboard-stat-card--error">
          <p className="dashboard-stat-card__error-text">
            Unable to load balance summary. Some data may be unavailable.
          </p>
        </div>
      </div>
    );
  }

  // All settled check
  const allSettled = youOwe === 0 && youAreOwed === 0 && netBalance === 0;

  if (allSettled) {
    return (
      <div className="dashboard-summary-cards">
        <div className="dashboard-stat-card dashboard-stat-card--settled">
          <div className="dashboard-stat-card__icon">🎉</div>
          <div className="dashboard-stat-card__label">All Settled</div>
          <div className="dashboard-stat-card__value">₹0.00</div>
          <div className="dashboard-stat-card__subtitle">
            You're all caught up across all groups
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-summary-cards">
      {/* You Owe Card */}
      <div className="dashboard-stat-card dashboard-stat-card--owe">
        <div className="dashboard-stat-card__label">You Owe</div>
        <div className="dashboard-stat-card__value">
          ₹{formatAmount(youOwe)}
        </div>
        {youOwe > 0 && (
          <div className="dashboard-stat-card__subtitle">
            Total debt across groups
          </div>
        )}
      </div>

      {/* You Are Owed Card */}
      <div className="dashboard-stat-card dashboard-stat-card--owed">
        <div className="dashboard-stat-card__label">You Are Owed</div>
        <div className="dashboard-stat-card__value">
          ₹{formatAmount(youAreOwed)}
        </div>
        {youAreOwed > 0 && (
          <div className="dashboard-stat-card__subtitle">
            Total to receive
          </div>
        )}
      </div>

      {/* Net Balance Card */}
      <div
        className={`dashboard-stat-card dashboard-stat-card--net ${
          netBalance > 0
            ? 'dashboard-stat-card--net-positive'
            : netBalance < 0
            ? 'dashboard-stat-card--net-negative'
            : 'dashboard-stat-card--net-neutral'
        }`}
      >
        <div className="dashboard-stat-card__label">Net Balance</div>
        <div className="dashboard-stat-card__value">
          {netBalance >= 0 ? '+' : '-'}₹{formatAmount(netBalance)}
        </div>
        <div className="dashboard-stat-card__subtitle">
          {netBalance > 0
            ? 'You are in positive'
            : netBalance < 0
            ? 'You are in negative'
            : 'Balanced'}
        </div>
      </div>
    </div>
  );
}
