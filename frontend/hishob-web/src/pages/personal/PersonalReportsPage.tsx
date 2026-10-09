/**
 * PersonalReportsPage — /reports/personal
 *
 * Implements Phase 20B: Personal Finance Analytics & Reports
 * 1. Personal Financial Overview (Total Spending, Total Expenses, Average Expense, Your Share, You Paid)
 * 2. Category Spending Breakdown (with percentage and count)
 * 3. Friend Spending Breakdown (with real names, volume, expense count, user's share, friend's share)
 * 4. Monthly Spending Trends (strictly by Expense.createdAt)
 * 5. Time Filters (All Time, This Month, Last 30 Days, Custom Range)
 * 6. CSV Export (personal expenses with properly formatted participant & split data)
 * 7. Print / PDF friendly layout via window.print() and @media print
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getFriends } from '@/services/friendService';
import { getPersonalAnalytics, exportPersonalExpensesCsv } from '@/services/analyticsService';
import type { PersonalAnalyticsResponse, TimeRangeOption } from '@/types/analytics';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import InteractiveDonutChart from '@/components/reports/InteractiveDonutChart';
import InteractiveTrendLineChart from '@/components/reports/InteractiveTrendLineChart';
import ParticipantComparisonChart from '@/components/reports/ParticipantComparisonChart';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

export default function PersonalReportsPage() {
  // Data state
  const [analytics, setAnalytics] = useState<PersonalAnalyticsResponse | null>(null);
  const [friends, setFriends] = useState<Friend[]>([]);

  // Loading & Error states
  const [pageLoading, setPageLoading] = useState(true);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  // Filter state
  const [timeRange, setTimeRange] = useState<TimeRangeOption>('ALL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Export state
  const [isExporting, setIsExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  // Load friends for name resolution
  const loadFriendsData = useCallback(async () => {
    try {
      const friendsList = await getFriends();
      setFriends(friendsList);
    } catch {
      // Non-critical if friend list fails to load
    }
  }, []);

  // Load personal analytics
  const loadAnalyticsData = useCallback(async (sDate?: string, eDate?: string) => {
    setAnalyticsLoading(true);
    setPageError(null);
    try {
      const data = await getPersonalAnalytics({
        startDate: sDate || undefined,
        endDate: eDate || undefined,
      });
      setAnalytics(data);
    } catch (err) {
      setPageError(getErrorMessage(err));
    } finally {
      setAnalyticsLoading(false);
      setPageLoading(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    queueMicrotask(() => {
      void loadFriendsData();
      void loadAnalyticsData(startDate, endDate);
    });
  }, [loadFriendsData, loadAnalyticsData, startDate, endDate]);

  // Filter handlers
  const handleTimeRangeChange = (range: TimeRangeOption) => {
    setTimeRange(range);
    const now = new Date();

    if (range === 'ALL') {
      setStartDate('');
      setEndDate('');
    } else if (range === 'THIS_MONTH') {
      const start = new Date(now.getFullYear(), now.getMonth(), 1);
      const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
      setStartDate(start.toISOString().split('T')[0]);
      setEndDate(end.toISOString().split('T')[0]);
    } else if (range === 'LAST_30_DAYS') {
      const start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      setStartDate(start.toISOString().split('T')[0]);
      setEndDate(now.toISOString().split('T')[0]);
    }
  };

  const handleResetFilters = () => {
    setTimeRange('ALL');
    setStartDate('');
    setEndDate('');
  };

  // CSV Export
  const handleExportCsv = async () => {
    setIsExporting(true);
    setExportError(null);
    try {
      const blob = await exportPersonalExpensesCsv({
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `personal-expenses-${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setExportError(getErrorMessage(err));
    } finally {
      setIsExporting(false);
    }
  };

  // Print handler
  const handlePrint = () => {
    window.print();
  };

  const hasExpenses = analytics && analytics.expenseCount > 0;
  const friendSpendingList = useMemo(() => analytics?.friendSpending ?? [], [analytics]);

  const friendNamesMap = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const f of friends) {
      const name =
        f.name && f.name.trim() && !UUID_REGEX.test(f.name.trim())
          ? f.name.trim()
          : f.email && f.email.trim() && !UUID_REGEX.test(f.email.trim())
          ? f.email.trim()
          : 'Unknown member';
      map[f.userId] = name;
      map[f.userId.toLowerCase()] = name;
    }
    return map;
  }, [friends]);

  if (pageLoading) {
    return <LoadingState label="Loading personal reports…" height="lg" />;
  }

  return (
    <div className="group-details-page report-page">
      {/* Header */}
      <header className="page-header page-header--report">
        <div className="page-header__title">
          <nav aria-label="Breadcrumb" className="group-details-breadcrumb">
            <Link to="/expenses">Personal Expenses</Link>
            <span aria-hidden="true"> / </span>
            <span>Personal Reports</span>
          </nav>
          <div className="group-details-title-row">
            <h2 className="page-header__heading">📊 Personal Reports</h2>
            <span className="badge badge--primary">Personal</span>
          </div>
          <p className="group-details-meta">
            Your personal spending overview · Real-time financial insights
          </p>
        </div>

        <div className="report-header-actions no-print">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={handlePrint}
            title="Print or save as PDF"
            aria-label="Print Report"
          >
            🖨️ Print / PDF
          </button>
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={handleExportCsv}
            disabled={isExporting || analyticsLoading}
            aria-label="Export personal expenses to CSV"
          >
            {isExporting ? 'Exporting…' : '📥 Export CSV'}
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void loadAnalyticsData(startDate, endDate)}
            disabled={analyticsLoading}
            aria-label="Refresh report"
          >
            {analyticsLoading ? 'Refreshing…' : '↻ Refresh'}
          </button>
        </div>
      </header>

      {/* CSV Export error alert */}
      {exportError && (
        <div className="alert alert--error groups-page__alert no-print" role="alert">
          <span>Failed to export CSV: {exportError}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setExportError(null)}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Error Banner */}
      {pageError && (
        <div className="alert alert--error groups-page__alert" role="alert">
          <span>{pageError}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void loadAnalyticsData(startDate, endDate)}
          >
            Retry
          </button>
        </div>
      )}

      {/* Filter Bar */}
      <section className="group-details-section report-filter-section no-print" aria-label="Report Filters">
        <div className="report-filter-bar" style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'center' }}>
          {/* Time range tabs/select */}
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button
              type="button"
              className={`btn btn--sm ${timeRange === 'ALL' ? 'btn--primary' : 'btn--ghost'}`}
              onClick={() => handleTimeRangeChange('ALL')}
            >
              All Time
            </button>
            <button
              type="button"
              className={`btn btn--sm ${timeRange === 'THIS_MONTH' ? 'btn--primary' : 'btn--ghost'}`}
              onClick={() => handleTimeRangeChange('THIS_MONTH')}
            >
              This Month
            </button>
            <button
              type="button"
              className={`btn btn--sm ${timeRange === 'LAST_30_DAYS' ? 'btn--primary' : 'btn--ghost'}`}
              onClick={() => handleTimeRangeChange('LAST_30_DAYS')}
            >
              Last 30 Days
            </button>
            <button
              type="button"
              className={`btn btn--sm ${timeRange === 'CUSTOM' ? 'btn--primary' : 'btn--ghost'}`}
              onClick={() => handleTimeRangeChange('CUSTOM')}
            >
              Custom Range
            </button>
          </div>

          {/* Custom Date Pickers */}
          {timeRange === 'CUSTOM' && (
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <label htmlFor="report-custom-start" style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                  From:
                </label>
                <input
                  id="report-custom-start"
                  type="date"
                  className="form-field__input"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  style={{ width: 'auto', padding: '0.3rem 0.5rem' }}
                />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <label htmlFor="report-custom-end" style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                  To:
                </label>
                <input
                  id="report-custom-end"
                  type="date"
                  className="form-field__input"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  style={{ width: 'auto', padding: '0.3rem 0.5rem' }}
                />
              </div>
            </div>
          )}

          {/* Reset Filters */}
          {(timeRange !== 'ALL' || startDate || endDate) && (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={handleResetFilters}
            >
              Clear Filter
            </button>
          )}
        </div>
      </section>

      {/* Main Content / Empty State */}
      {!hasExpenses && !analyticsLoading ? (
        <EmptyState
          icon="📊"
          heading="No Personal Expenses Found"
          description={
            timeRange !== 'ALL' || startDate || endDate
              ? 'No personal expenses match the selected date range. Try clearing your filters.'
              : 'You have not recorded any personal expenses yet. Add a personal expense to view spending analytics and trends.'
          }
          action={
            <Link to="/expenses" className="btn btn--primary">
              + Add Personal Expense
            </Link>
          }
        />
      ) : (
        <>
          {/* 1. KPI Overview Cards */}
          <section className="group-details-section" aria-label="Personal Spending Overview">
            <div className="page-header page-header--section">
              <h3 className="page-header__heading page-header__heading--sm">
                Spending Overview
              </h3>
              <p className="page-header__sub">Key metrics for personal expenses in the selected timeframe</p>
            </div>

            <div className="report-kpi-grid">
              {/* Total Spending */}
              <div className="report-kpi-card report-kpi-card--primary">
                <div className="report-kpi-card__label">Total Spending</div>
                <div className="report-kpi-card__value">
                  {analyticsLoading ? '…' : formatINR(Number(analytics?.totalSpending ?? 0))}
                </div>
                <div className="report-kpi-card__hint">Overall personal volume</div>
              </div>

              {/* Total Expenses */}
              <div className="report-kpi-card">
                <div className="report-kpi-card__label">Total Expenses</div>
                <div className="report-kpi-card__value">
                  {analyticsLoading ? '…' : analytics?.expenseCount ?? 0}
                </div>
                <div className="report-kpi-card__hint">Personal transactions</div>
              </div>

              {/* Average Expense */}
              <div className="report-kpi-card">
                <div className="report-kpi-card__label">Average Expense</div>
                <div className="report-kpi-card__value">
                  {analyticsLoading ? '…' : formatINR(Number(analytics?.averageExpense ?? 0))}
                </div>
                <div className="report-kpi-card__hint">Per transaction average</div>
              </div>

              {/* Your Share */}
              <div className="report-kpi-card report-kpi-card--accent">
                <div className="report-kpi-card__label">Your Share</div>
                <div className="report-kpi-card__value">
                  {analyticsLoading ? '…' : formatINR(Number(analytics?.totalUserShare ?? 0))}
                </div>
                <div className="report-kpi-card__hint">Your actual consumption</div>
              </div>

              {/* You Paid */}
              <div className="report-kpi-card">
                <div className="report-kpi-card__label">You Paid</div>
                <div className="report-kpi-card__value">
                  {analyticsLoading ? '…' : formatINR(Number(analytics?.totalPaidByUser ?? 0))}
                </div>
                <div className="report-kpi-card__hint">Total out-of-pocket paid</div>
              </div>
            </div>
          </section>

          {/* 2. Category Spending Breakdown */}
          <section className="group-details-section" aria-label="Category Spending Breakdown">
            <div className="page-header page-header--section">
              <h3 className="page-header__heading page-header__heading--sm">
                Category Spending
              </h3>
              <p className="page-header__sub">Breakdown of personal expenses by category</p>
            </div>

            {analyticsLoading ? (
              <LoadingState label="Computing category breakdown…" height="sm" />
            ) : (
              <InteractiveDonutChart
                categorySpending={analytics?.categorySpending ?? []}
                totalAmount={Number(analytics?.totalSpending ?? 0)}
                title="Personal Spending by Category"
              />
            )}
          </section>

          {/* 3. Friend Spending Breakdown */}
          <section className="group-details-section" aria-label="Friend Spending Breakdown">
            <div className="page-header page-header--section">
              <h3 className="page-header__heading page-header__heading--sm">
                Friend Spending Breakdown
              </h3>
              <p className="page-header__sub">Spending volume involving each friend</p>
            </div>

            {analyticsLoading ? (
              <LoadingState label="Computing friend spending…" height="sm" />
            ) : (
              <ParticipantComparisonChart
                mode="PERSONAL"
                items={friendSpendingList}
                friendNames={friendNamesMap}
                title="Spending by Friend"
                emptyMessage="No friend spending data in this range."
              />
            )}
          </section>

          {/* 4. Monthly Spending Timeline */}
          <section className="group-details-section" aria-label="Monthly Spending Trends">
            <div className="page-header page-header--section">
              <h3 className="page-header__heading page-header__heading--sm">
                Monthly Spending Trends
              </h3>
              <p className="page-header__sub">Personal expense volume grouped strictly by record creation date</p>
            </div>

            {analyticsLoading ? (
              <LoadingState label="Computing monthly timeline…" height="sm" />
            ) : (
              <InteractiveTrendLineChart
                monthlyTrends={analytics?.monthlyTrends ?? []}
                title="Personal Monthly Spending Trends"
              />
            )}
          </section>
        </>
      )}
    </div>
  );
}
