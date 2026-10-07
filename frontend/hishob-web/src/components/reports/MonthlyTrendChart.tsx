/**
 * MonthlyTrendChart — native CSS/SVG visualization for monthly spending trends.
 * Groups strictly by Expense.createdAt.
 * Zero external charting library dependencies.
 */

import type { MonthlySpendingSummary } from '@/types/analytics';

interface MonthlyTrendChartProps {
  monthlyTrends: MonthlySpendingSummary[];
}

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

export default function MonthlyTrendChart({ monthlyTrends }: MonthlyTrendChartProps) {
  if (monthlyTrends.length === 0) {
    return <p className="report-empty-sub">No monthly spending data in this range.</p>;
  }

  const maxSpend = Math.max(...monthlyTrends.map((m) => Number(m.totalAmount)), 1);

  return (
    <div className="report-chart-container">
      <div className="report-chart-bars" role="region" aria-label="Monthly spending chart">
        {monthlyTrends.map((trend) => {
          const amount = Number(trend.totalAmount);
          const heightPercent = Math.max(10, (amount / maxSpend) * 100);

          return (
            <div key={trend.yearMonth} className="report-chart-col">
              <div className="report-chart-tooltip">
                <span className="report-chart-tooltip__amount">{formatINR(amount)}</span>
                <span className="report-chart-tooltip__count">
                  {trend.expenseCount} expense{trend.expenseCount !== 1 ? 's' : ''}
                </span>
              </div>

              <div className="report-chart-bar-wrap">
                <div
                  className="report-chart-bar"
                  style={{ height: `${heightPercent}%` }}
                  title={`${trend.label}: ${formatINR(amount)} (${trend.expenseCount} expenses)`}
                />
              </div>

              <div className="report-chart-col__label" title={trend.label}>
                {trend.label}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
