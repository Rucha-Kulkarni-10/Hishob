/**
 * CategorySpendingBreakdown — native CSS visualization for category spending breakdown.
 * Displays category name, icon, total amount, expense count, and percentage share.
 * Zero external charting library dependencies.
 */

import type { CategorySpendingSummary } from '@/types/analytics';
import { getCategoryMeta } from '@/types/expenses';

interface CategorySpendingBreakdownProps {
  categorySpending?: CategorySpendingSummary[];
}

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

export default function CategorySpendingBreakdown({
  categorySpending = [],
}: CategorySpendingBreakdownProps) {
  if (!categorySpending || categorySpending.length === 0) {
    return <p className="report-empty-sub">No categorized expenses yet.</p>;
  }

  return (
    <div className="category-spending-list" role="region" aria-label="Category spending breakdown">
      {categorySpending.map((item) => {
        const meta = getCategoryMeta(item.category);
        const amount = Number(item.totalAmount);
        const count = item.expenseCount;
        const percentage = Number(item.percentageOfTotal) || 0;

        return (
          <div key={item.category} className="category-spending-item">
            <div className="category-spending-item__header">
              <div className="category-spending-item__info">
                <span className="category-spending-item__icon" aria-hidden="true">
                  {meta.icon}
                </span>
                <span className="category-spending-item__name">{meta.label}</span>
                <span className="category-spending-item__count">
                  ({count} expense{count !== 1 ? 's' : ''})
                </span>
              </div>
              <div className="category-spending-item__stats">
                <span className="category-spending-item__amount">{formatINR(amount)}</span>
                <span className="category-spending-item__percentage">{percentage.toFixed(1)}%</span>
              </div>
            </div>
            <div className="category-spending-item__bar-bg" aria-hidden="true">
              <div
                className="category-spending-item__bar-fill"
                style={{ width: `${Math.min(100, Math.max(0, percentage))}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
