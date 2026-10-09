/**
 * InteractiveDonutChart — accessible, responsive SVG donut chart for category spending breakdown.
 *
 * Features:
 * - Pure SVG rendering with calculated arc segments (zero external chart libraries)
 * - Dynamic center summary (Total spend by default, active/hovered slice details on interaction)
 * - Interactive dual-linked legend (hovering slice highlights legend and vice-versa)
 * - Accessible keyboard navigation (Tab through legend items, Enter/Space to select, Escape to clear)
 * - Handles empty data, 0-total data, and 100% single-slice scenarios without SVG glitches
 */

import { useId, useMemo, useState } from 'react';
import type { CategorySpendingSummary } from '@/types/analytics';
import { type ExpenseCategory, getCategoryMeta } from '@/types/expenses';

export interface InteractiveDonutChartProps {
  categorySpending?: CategorySpendingSummary[];
  totalAmount?: number;
  title?: string;
  emptyMessage?: string;
}

const CATEGORY_COLORS: Record<ExpenseCategory, string> = {
  FOOD: '#f97316',          // Warm Amber/Orange
  GROCERIES: '#10b981',     // Fresh Emerald
  TRAVEL: '#06b6d4',        // Cyan/Sky
  SHOPPING: '#ec4899',       // Pink/Rose
  ENTERTAINMENT: '#8b5cf6',  // Purple/Violet
  BILLS: '#eab308',          // Gold/Yellow
  HEALTH: '#ef4444',         // Crimson Red
  EDUCATION: '#3b82f6',      // Cobalt Blue
  OTHER: '#64748b',          // Slate Grey
};

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

interface ArcSegment {
  category: ExpenseCategory;
  amount: number;
  percentage: number;
  expenseCount: number;
  startAngle: number;
  endAngle: number;
  color: string;
  pathData: string;
}

/** Converts polar coordinates (angle in degrees, radius) to Cartesian coordinates (x, y) */
function polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180.0;
  return {
    x: centerX + radius * Math.cos(angleInRadians),
    y: centerY + radius * Math.sin(angleInRadians),
  };
}

/** Generates SVG path data for an annular ring slice */
function describeDonutSegment(
  x: number,
  y: number,
  rOuter: number,
  rInner: number,
  startAngle: number,
  endAngle: number
): string {
  // Clamp angle delta to slightly less than 360 to prevent SVG arc loop collapse
  const angleDelta = endAngle - startAngle;
  const isFullCircle = angleDelta >= 359.99;
  const safeEndAngle = isFullCircle ? startAngle + 359.99 : endAngle;

  const startOuter = polarToCartesian(x, y, rOuter, startAngle);
  const endOuter = polarToCartesian(x, y, rOuter, safeEndAngle);
  const startInner = polarToCartesian(x, y, rInner, safeEndAngle);
  const endInner = polarToCartesian(x, y, rInner, startAngle);

  const largeArcFlag = angleDelta > 180 ? 1 : 0;

  return [
    `M ${startOuter.x} ${startOuter.y}`,
    `A ${rOuter} ${rOuter} 0 ${largeArcFlag} 1 ${endOuter.x} ${endOuter.y}`,
    `L ${startInner.x} ${startInner.y}`,
    `A ${rInner} ${rInner} 0 ${largeArcFlag} 0 ${endInner.x} ${endInner.y}`,
    'Z',
  ].join(' ');
}

export default function InteractiveDonutChart({
  categorySpending = [],
  totalAmount,
  title = 'Spending by Category',
  emptyMessage = 'No category spending data available.',
}: InteractiveDonutChartProps) {
  const chartId = useId();
  const [activeCategory, setActiveCategory] = useState<ExpenseCategory | null>(null);

  // Filter valid entries with positive amounts
  const validItems = useMemo(() => {
    return categorySpending.filter((item) => Number(item.totalAmount) > 0);
  }, [categorySpending]);

  const computedTotal = useMemo(() => {
    if (totalAmount != null && totalAmount > 0) return totalAmount;
    return validItems.reduce((acc, item) => acc + Number(item.totalAmount), 0);
  }, [totalAmount, validItems]);

  // Compute geometry arcs
  const segments: ArcSegment[] = useMemo(() => {
    if (validItems.length === 0 || computedTotal <= 0) return [];

    let currentAngle = 0;
    const CX = 120;
    const CY = 120;
    const R_OUTER = 92;
    const R_INNER = 62;

    return validItems.map((item) => {
      const amount = Number(item.totalAmount);
      const percentage = (amount / computedTotal) * 100;
      const angleSweep = (percentage / 100) * 360;
      const startAngle = currentAngle;
      const endAngle = currentAngle + angleSweep;
      currentAngle = endAngle;

      const color = CATEGORY_COLORS[item.category] || CATEGORY_COLORS.OTHER;
      const pathData = describeDonutSegment(CX, CY, R_OUTER, R_INNER, startAngle, endAngle);

      return {
        category: item.category,
        amount,
        percentage,
        expenseCount: item.expenseCount,
        startAngle,
        endAngle,
        color,
        pathData,
      };
    });
  }, [validItems, computedTotal]);

  // Selected or hovered category details
  const activeSegment = useMemo(() => {
    if (!activeCategory) return null;
    return segments.find((s) => s.category === activeCategory) || null;
  }, [activeCategory, segments]);

  if (segments.length === 0) {
    return (
      <div className="interactive-donut-chart interactive-donut-chart--empty" role="region" aria-label={title}>
        <div className="interactive-donut-chart__header">
          <h4 className="interactive-donut-chart__title">{title}</h4>
        </div>
        <div className="interactive-donut-chart__empty-wrap">
          <svg
            className="interactive-donut-chart__svg interactive-donut-chart__svg--empty"
            viewBox="0 0 240 240"
            aria-hidden="true"
          >
            <circle cx="120" cy="120" r="77" fill="none" stroke="var(--color-border)" strokeWidth="30" opacity="0.4" />
          </svg>
          <p className="interactive-donut-chart__empty-text">{emptyMessage}</p>
        </div>
      </div>
    );
  }

  return (
    <div
      className="interactive-donut-chart"
      role="region"
      aria-label={`${title}. Total spending: ${formatINR(computedTotal)} across ${segments.length} categories.`}
    >
      <div className="interactive-donut-chart__header">
        <h4 className="interactive-donut-chart__title">{title}</h4>
        <span className="interactive-donut-chart__count-badge badge badge--neutral">
          {segments.length} categor{segments.length !== 1 ? 'ies' : 'y'}
        </span>
      </div>

      <div className="interactive-donut-chart__body">
        {/* SVG Donut Visual */}
        <div className="interactive-donut-chart__graphic-wrap">
          <svg
            className="interactive-donut-chart__svg"
            viewBox="0 0 240 240"
            role="img"
            aria-labelledby={`${chartId}-desc`}
          >
            <title id={`${chartId}-desc`}>
              {activeSegment
                ? `${getCategoryMeta(activeSegment.category).label}: ${formatINR(activeSegment.amount)} (${activeSegment.percentage.toFixed(1)}%)`
                : `Total: ${formatINR(computedTotal)}`}
            </title>

            <g className="interactive-donut-chart__segments">
              {segments.map((seg) => {
                const isFocused = activeCategory === seg.category;
                const isMuted = activeCategory !== null && !isFocused;
                const meta = getCategoryMeta(seg.category);

                return (
                  <path
                    key={seg.category}
                    d={seg.pathData}
                    fill={seg.color}
                    className={`interactive-donut-chart__segment ${
                      isFocused ? 'interactive-donut-chart__segment--active' : ''
                    } ${isMuted ? 'interactive-donut-chart__segment--muted' : ''}`}
                    onMouseEnter={() => setActiveCategory(seg.category)}
                    onMouseLeave={() => setActiveCategory(null)}
                    tabIndex={-1}
                    aria-hidden="true"
                  >
                    <title>{`${meta.label}: ${formatINR(seg.amount)} (${seg.percentage.toFixed(1)}%)`}</title>
                  </path>
                );
              })}
            </g>

            {/* Center Summary Labels */}
            <g className="interactive-donut-chart__center" textAnchor="middle">
              {activeSegment ? (
                <>
                  <text x="120" y="105" className="interactive-donut-chart__center-icon">
                    {getCategoryMeta(activeSegment.category).icon}
                  </text>
                  <text x="120" y="125" className="interactive-donut-chart__center-sub">
                    {getCategoryMeta(activeSegment.category).label}
                  </text>
                  <text x="120" y="145" className="interactive-donut-chart__center-val">
                    {formatINR(activeSegment.amount)}
                  </text>
                  <text x="120" y="160" className="interactive-donut-chart__center-pct">
                    {activeSegment.percentage.toFixed(1)}% ({activeSegment.expenseCount} exp)
                  </text>
                </>
              ) : (
                <>
                  <text x="120" y="115" className="interactive-donut-chart__center-label">
                    Total
                  </text>
                  <text x="120" y="138" className="interactive-donut-chart__center-total">
                    {formatINR(computedTotal)}
                  </text>
                  <text x="120" y="156" className="interactive-donut-chart__center-hint">
                    Hover for details
                  </text>
                </>
              )}
            </g>
          </svg>
        </div>

        {/* Interactive Accessible Legend */}
        <div
          className="interactive-donut-chart__legend"
          role="list"
          aria-label="Categories legend"
        >
          {segments.map((seg) => {
            const meta = getCategoryMeta(seg.category);
            const isSelected = activeCategory === seg.category;

            return (
              <button
                type="button"
                key={seg.category}
                className={`interactive-donut-chart__legend-item ${
                  isSelected ? 'interactive-donut-chart__legend-item--selected' : ''
                }`}
                onMouseEnter={() => setActiveCategory(seg.category)}
                onMouseLeave={() => setActiveCategory(null)}
                onFocus={() => setActiveCategory(seg.category)}
                onBlur={() => setActiveCategory(null)}
                onClick={() => setActiveCategory((prev) => (prev === seg.category ? null : seg.category))}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') setActiveCategory(null);
                }}
                aria-pressed={isSelected}
                aria-label={`${meta.label}: ${formatINR(seg.amount)}, ${seg.percentage.toFixed(1)}% of total, ${seg.expenseCount} expenses`}
              >
                <span
                  className="interactive-donut-chart__legend-color"
                  style={{ backgroundColor: seg.color }}
                  aria-hidden="true"
                />
                <span className="interactive-donut-chart__legend-icon" aria-hidden="true">
                  {meta.icon}
                </span>
                <span className="interactive-donut-chart__legend-name">{meta.label}</span>
                <span className="interactive-donut-chart__legend-amount">{formatINR(seg.amount)}</span>
                <span className="interactive-donut-chart__legend-pct">{seg.percentage.toFixed(1)}%</span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
