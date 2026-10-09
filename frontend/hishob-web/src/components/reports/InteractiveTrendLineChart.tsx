/**
 * InteractiveTrendLineChart — responsive, accessible SVG trend chart for monthly spending.
 *
 * Features:
 * - Pure SVG area + line visualization with coordinate interpolation (zero chart dependencies)
 * - Interactive hover cursor, touch support, and keyboard navigation (ArrowLeft / ArrowRight)
 * - Rich tooltip & detail panel (Total spend, expense count, and Month-over-Month change indicator)
 * - Handles 0 points, 1 point, all-zero values, and multi-year ranges without NaN/Infinity SVG errors
 * - Accessible SVG structure with role="region" and aria labels
 */

import { useId, useMemo, useState } from 'react';
import type { MonthlySpendingSummary } from '@/types/analytics';

export interface InteractiveTrendLineChartProps {
  monthlyTrends?: MonthlySpendingSummary[];
  title?: string;
  emptyMessage?: string;
}

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

function formatCompactINR(val: number): string {
  if (val >= 100000) return `₹${(val / 100000).toFixed(1)}L`;
  if (val >= 1000) return `₹${(val / 1000).toFixed(1)}k`;
  return `₹${val.toFixed(0)}`;
}

interface ProcessedPoint {
  index: number;
  yearMonth: string;
  label: string;
  amount: number;
  expenseCount: number;
  x: number;
  y: number;
  momChangePercent: number | null;
}

export default function InteractiveTrendLineChart({
  monthlyTrends = [],
  title = 'Monthly Spending Trends',
  emptyMessage = 'No monthly spending data in this range.',
}: InteractiveTrendLineChartProps) {
  const chartId = useId();
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<'LINE' | 'BAR'>('LINE');

  // Chart dimensions & margins
  const SVG_WIDTH = 640;
  const SVG_HEIGHT = 280;
  const MARGIN = { top: 25, right: 35, bottom: 45, left: 65 };
  const INNER_WIDTH = SVG_WIDTH - MARGIN.left - MARGIN.right;
  const INNER_HEIGHT = SVG_HEIGHT - MARGIN.top - MARGIN.bottom;

  // Process data points and scale calculations
  const { points, yTicks, areaPath, linePath } = useMemo(() => {
    if (!monthlyTrends || monthlyTrends.length === 0) {
      return { points: [], maxAmount: 0, yTicks: [], areaPath: '', linePath: '' };
    }

    const rawAmounts = monthlyTrends.map((m) => Number(m.totalAmount) || 0);
    const highestVal = Math.max(...rawAmounts, 0);
    // Nice round upper bound for Y-axis
    const maxVal = highestVal > 0 ? highestVal * 1.15 : 1000;

    // Y ticks (4 horizontal grid levels)
    const tickCount = 4;
    const ticks: { val: number; y: number }[] = [];
    for (let i = 0; i <= tickCount; i++) {
      const val = (maxVal / tickCount) * i;
      const y = MARGIN.top + INNER_HEIGHT - (val / maxVal) * INNER_HEIGHT;
      ticks.push({ val, y });
    }

    const totalCount = monthlyTrends.length;
    const stepX = totalCount > 1 ? INNER_WIDTH / (totalCount - 1) : INNER_WIDTH / 2;

    const computedPoints: ProcessedPoint[] = monthlyTrends.map((m, idx) => {
      const amount = Number(m.totalAmount) || 0;
      const x = totalCount > 1 ? MARGIN.left + idx * stepX : MARGIN.left + INNER_WIDTH / 2;
      const y = MARGIN.top + INNER_HEIGHT - (amount / maxVal) * INNER_HEIGHT;

      // Calculate Month-over-Month change
      let momChangePercent: number | null = null;
      if (idx > 0) {
        const prevAmount = Number(monthlyTrends[idx - 1].totalAmount) || 0;
        if (prevAmount > 0) {
          momChangePercent = ((amount - prevAmount) / prevAmount) * 100;
        } else if (amount > 0) {
          momChangePercent = 100;
        }
      }

      return {
        index: idx,
        yearMonth: m.yearMonth,
        label: m.label,
        amount,
        expenseCount: m.expenseCount,
        x,
        y,
        momChangePercent,
      };
    });

    // Build SVG Path strings
    let lPath = '';
    let aPath = '';

    if (computedPoints.length === 1) {
      const p = computedPoints[0];
      lPath = `M ${MARGIN.left} ${p.y} L ${MARGIN.left + INNER_WIDTH} ${p.y}`;
      aPath = `M ${MARGIN.left} ${MARGIN.top + INNER_HEIGHT} L ${MARGIN.left} ${p.y} L ${MARGIN.left + INNER_WIDTH} ${p.y} L ${MARGIN.left + INNER_WIDTH} ${MARGIN.top + INNER_HEIGHT} Z`;
    } else if (computedPoints.length > 1) {
      const first = computedPoints[0];
      lPath = `M ${first.x} ${first.y}`;
      for (let i = 1; i < computedPoints.length; i++) {
        lPath += ` L ${computedPoints[i].x} ${computedPoints[i].y}`;
      }
      const last = computedPoints[computedPoints.length - 1];
      aPath = `${lPath} L ${last.x} ${MARGIN.top + INNER_HEIGHT} L ${first.x} ${MARGIN.top + INNER_HEIGHT} Z`;
    }

    return {
      points: computedPoints,
      yTicks: ticks,
      areaPath: aPath,
      linePath: lPath,
    };
  }, [monthlyTrends, INNER_WIDTH, INNER_HEIGHT, MARGIN.left, MARGIN.top]);

  // Selected or default active point
  const activePoint = useMemo(() => {
    if (points.length === 0) return null;
    if (selectedIndex !== null && points[selectedIndex]) {
      return points[selectedIndex];
    }
    // Default to the most recent month
    return points[points.length - 1];
  }, [selectedIndex, points]);

  // Keyboard navigation handler
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (points.length === 0) return;
    const currentIdx = selectedIndex !== null ? selectedIndex : points.length - 1;

    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault();
      const nextIdx = Math.min(points.length - 1, currentIdx + 1);
      setSelectedIndex(nextIdx);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault();
      const prevIdx = Math.max(0, currentIdx - 1);
      setSelectedIndex(prevIdx);
    } else if (e.key === 'Escape') {
      setSelectedIndex(null);
    }
  };

  if (points.length === 0) {
    return (
      <div className="interactive-trend-chart interactive-trend-chart--empty" role="region" aria-label={title}>
        <div className="interactive-trend-chart__header">
          <h4 className="interactive-trend-chart__title">{title}</h4>
        </div>
        <p className="interactive-trend-chart__empty-text">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div
      className="interactive-trend-chart"
      role="region"
      aria-label={`${title}. Displaying ${points.length} months of trend data.`}
      onKeyDown={handleKeyDown}
      tabIndex={0}
    >
      <div className="interactive-trend-chart__header">
        <div>
          <h4 className="interactive-trend-chart__title">{title}</h4>
          <p className="interactive-trend-chart__sub">
            Track your spending trajectory across months. Use arrow keys to explore.
          </p>
        </div>

        <div className="interactive-trend-chart__controls">
          <div className="btn-group" role="group" aria-label="Chart view mode">
            <button
              type="button"
              className={`btn btn--sm ${viewMode === 'LINE' ? 'btn--primary' : 'btn--secondary'}`}
              onClick={() => setViewMode('LINE')}
              aria-pressed={viewMode === 'LINE'}
            >
              Line
            </button>
            <button
              type="button"
              className={`btn btn--sm ${viewMode === 'BAR' ? 'btn--primary' : 'btn--secondary'}`}
              onClick={() => setViewMode('BAR')}
              aria-pressed={viewMode === 'BAR'}
            >
              Bars
            </button>
          </div>
        </div>
      </div>

      {/* Active Month Detail Badge / Banner */}
      {activePoint && (
        <div className="interactive-trend-chart__detail-banner" aria-live="polite">
          <div className="interactive-trend-chart__detail-col">
            <span className="interactive-trend-chart__detail-label">Month</span>
            <strong className="interactive-trend-chart__detail-val">{activePoint.label}</strong>
          </div>

          <div className="interactive-trend-chart__detail-col">
            <span className="interactive-trend-chart__detail-label">Total Spent</span>
            <strong className="interactive-trend-chart__detail-amount">{formatINR(activePoint.amount)}</strong>
          </div>

          <div className="interactive-trend-chart__detail-col">
            <span className="interactive-trend-chart__detail-label">Expenses</span>
            <strong className="interactive-trend-chart__detail-val">
              {activePoint.expenseCount} {activePoint.expenseCount === 1 ? 'expense' : 'expenses'}
            </strong>
          </div>

          {activePoint.momChangePercent !== null && (
            <div className="interactive-trend-chart__detail-col">
              <span className="interactive-trend-chart__detail-label">vs Prior Month</span>
              <span
                className={`badge ${
                  activePoint.momChangePercent > 0
                    ? 'badge--danger'
                    : activePoint.momChangePercent < 0
                    ? 'badge--success'
                    : 'badge--neutral'
                }`}
              >
                {activePoint.momChangePercent > 0 ? '▲ +' : activePoint.momChangePercent < 0 ? '▼ ' : ''}
                {Math.abs(activePoint.momChangePercent).toFixed(1)}%
              </span>
            </div>
          )}
        </div>
      )}

      {/* SVG Canvas */}
      <div className="interactive-trend-chart__svg-container">
        <svg
          className="interactive-trend-chart__svg"
          viewBox={`0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`}
          role="img"
          aria-labelledby={`${chartId}-trend-desc`}
        >
          <title id={`${chartId}-trend-desc`}>
            {`Spending trend from ${points[0].label} to ${points[points.length - 1].label}`}
          </title>

          <defs>
            {/* Area Fill Gradient */}
            <linearGradient id={`${chartId}-area-grad`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.3" />
              <stop offset="100%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.0" />
            </linearGradient>

            {/* Bar Gradient */}
            <linearGradient id={`${chartId}-bar-grad`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.9" />
              <stop offset="100%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.5" />
            </linearGradient>
          </defs>

          {/* Horizontal Gridlines & Y-Axis Labels */}
          <g className="interactive-trend-chart__grid">
            {yTicks.map((tick) => (
              <g key={tick.val} className="interactive-trend-chart__grid-row">
                <line
                  x1={MARGIN.left}
                  y1={tick.y}
                  x2={SVG_WIDTH - MARGIN.right}
                  y2={tick.y}
                  stroke="var(--color-border, #e2e8f0)"
                  strokeDasharray="4 4"
                  strokeWidth="1"
                />
                <text
                  x={MARGIN.left - 10}
                  y={tick.y + 4}
                  textAnchor="end"
                  className="interactive-trend-chart__axis-label"
                >
                  {formatCompactINR(tick.val)}
                </text>
              </g>
            ))}
          </g>

          {/* Visualization: BARS */}
          {viewMode === 'BAR' && (
            <g className="interactive-trend-chart__bars">
              {points.map((p) => {
                const barWidth = Math.max(12, Math.min(40, (INNER_WIDTH / points.length) * 0.55));
                const barHeight = Math.max(2, MARGIN.top + INNER_HEIGHT - p.y);
                const isSelected = activePoint?.yearMonth === p.yearMonth;

                return (
                  <rect
                    key={p.yearMonth}
                    x={p.x - barWidth / 2}
                    y={p.y}
                    width={barWidth}
                    height={barHeight}
                    rx="4"
                    fill={isSelected ? 'var(--color-primary, #6366f1)' : `url(#${chartId}-bar-grad)`}
                    opacity={isSelected ? 1 : 0.8}
                    className="interactive-trend-chart__bar-elem"
                    onMouseEnter={() => setSelectedIndex(p.index)}
                    onClick={() => setSelectedIndex(p.index)}
                  />
                );
              })}
            </g>
          )}

          {/* Visualization: LINE + AREA */}
          {viewMode === 'LINE' && (
            <g className="interactive-trend-chart__line-group">
              {areaPath && (
                <path d={areaPath} fill={`url(#${chartId}-area-grad)`} className="interactive-trend-chart__area" />
              )}
              {linePath && (
                <path
                  d={linePath}
                  fill="none"
                  stroke="var(--color-primary, #6366f1)"
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="interactive-trend-chart__line"
                />
              )}
            </g>
          )}

          {/* Active Crosshair / Vertical Cursor */}
          {activePoint && (
            <line
              x1={activePoint.x}
              y1={MARGIN.top}
              x2={activePoint.x}
              y2={MARGIN.top + INNER_HEIGHT}
              stroke="var(--color-primary, #6366f1)"
              strokeWidth="1.5"
              strokeDasharray="2 2"
              className="interactive-trend-chart__cursor-line"
            />
          )}

          {/* Interactive Data Points */}
          <g className="interactive-trend-chart__points">
            {points.map((p) => {
              const isSelected = activePoint?.yearMonth === p.yearMonth;

              return (
                <g
                  key={p.yearMonth}
                  className="interactive-trend-chart__point-group"
                  onMouseEnter={() => setSelectedIndex(p.index)}
                  onClick={() => setSelectedIndex(p.index)}
                  tabIndex={-1}
                >
                  {/* Invisible enlarged hit target for mouse/touch */}
                  <circle cx={p.x} cy={p.y} r="18" fill="transparent" cursor="pointer" />

                  {/* Outer glow ring when selected */}
                  {isSelected && (
                    <circle
                      cx={p.x}
                      cy={p.y}
                      r="8"
                      fill="none"
                      stroke="var(--color-primary, #6366f1)"
                      strokeWidth="2.5"
                      opacity="0.5"
                      className="interactive-trend-chart__point-halo"
                    />
                  )}

                  {/* Core point dot */}
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r={isSelected ? '5.5' : '4'}
                    fill="var(--color-surface, #ffffff)"
                    stroke="var(--color-primary, #6366f1)"
                    strokeWidth={isSelected ? '3' : '2'}
                    className="interactive-trend-chart__point-dot"
                  />
                </g>
              );
            })}
          </g>

          {/* X-Axis Month Labels */}
          <g className="interactive-trend-chart__x-axis">
            {points.map((p) => {
              const isSelected = activePoint?.yearMonth === p.yearMonth;

              return (
                <text
                  key={p.yearMonth}
                  x={p.x}
                  y={SVG_HEIGHT - 12}
                  textAnchor="middle"
                  className={`interactive-trend-chart__x-label ${
                    isSelected ? 'interactive-trend-chart__x-label--active' : ''
                  }`}
                  onClick={() => setSelectedIndex(p.index)}
                  cursor="pointer"
                >
                  {p.label}
                </text>
              );
            })}
          </g>
        </svg>
      </div>
    </div>
  );
}
