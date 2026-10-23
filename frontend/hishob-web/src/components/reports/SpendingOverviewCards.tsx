/**
 * SpendingOverviewCards — displays the top-level financial KPI cards
 * for the group reports page.
 */

interface SpendingOverviewCardsProps {
  totalSpending: number;
  expenseCount: number;
  averageExpense: number;
  totalSettledVolume: number;
  isLoading?: boolean;
}

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

export default function SpendingOverviewCards({
  totalSpending,
  expenseCount,
  averageExpense,
  totalSettledVolume,
  isLoading = false,
}: SpendingOverviewCardsProps) {
  return (
    <div className="report-kpi-grid">
      {/* Total Group Spend */}
      <div className="report-kpi-card report-kpi-card--primary">
        <div className="report-kpi-card__label">Total Group Spend</div>
        <div className="report-kpi-card__value">
          {isLoading ? '…' : formatINR(totalSpending)}
        </div>
        <div className="report-kpi-card__hint">Combined expense total</div>
      </div>

      {/* Expense Count */}
      <div className="report-kpi-card">
        <div className="report-kpi-card__label">Total Expenses</div>
        <div className="report-kpi-card__value">
          {isLoading ? '…' : expenseCount}
        </div>
        <div className="report-kpi-card__hint">Transactions recorded</div>
      </div>

      {/* Average Expense */}
      <div className="report-kpi-card">
        <div className="report-kpi-card__label">Average Expense</div>
        <div className="report-kpi-card__value">
          {isLoading ? '…' : formatINR(averageExpense)}
        </div>
        <div className="report-kpi-card__hint">Per transaction average</div>
      </div>

      {/* Total Completed Settlement Volume */}
      <div className="report-kpi-card report-kpi-card--accent">
        <div className="report-kpi-card__label">Settled Volume</div>
        <div className="report-kpi-card__value">
          {isLoading ? '…' : formatINR(totalSettledVolume)}
        </div>
        {/* Settlements carry no date in the internal contract, so this one KPI is all-time and
            deliberately does not respond to the filter bar. */}
        <div className="report-kpi-card__hint">All-time, ignores filters</div>
      </div>
    </div>
  );
}
