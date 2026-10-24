/**
 * GroupReportsPage — /groups/:id/reports
 *
 * Implements the approved Reports & Analytics MVP:
 * 1. Financial Overview (Total Group Spend, Expense Count, Average Expense, Settled Volume)
 * 2. Member Spending (Total Paid, Total Share, Net Position, CSS visual comparison)
 * 3. Monthly Spending Trend (strictly by Expense.createdAt)
 * 4. Filters (All Time, This Month, Last 30 Days, Custom Range, Payer, Split Type)
 * 5. CSV Export
 * 6. Print / PDF friendly layout via window.print() and @media print
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroupDetail, listMembers } from '@/services/groupService';
import { getGroupAnalytics, exportGroupExpensesCsv } from '@/services/analyticsService';
import type { GroupAnalyticsQueryParams } from '@/services/analyticsService';
import type { GroupResponse, MemberResponse } from '@/types/groups';
import type {
  GroupAnalyticsResponse,
  TimeRangeOption,
  SplitTypeFilter,
} from '@/types/analytics';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage, isAxiosError } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import SpendingOverviewCards from '@/components/reports/SpendingOverviewCards';
import InteractiveDonutChart from '@/components/reports/InteractiveDonutChart';
import InteractiveTrendLineChart from '@/components/reports/InteractiveTrendLineChart';
import ParticipantComparisonChart from '@/components/reports/ParticipantComparisonChart';
import ReportFilters from '@/components/reports/ReportFilters';

const USER_PREFIX_RE = /^User-([0-9a-fA-F]{8})$/;
const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function parseCsvLine(line: string): string[] {
  const result: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (inQuotes) {
      if (c === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (c === '"') {
        inQuotes = false;
      } else {
        current += c;
      }
    } else {
      if (c === '"') {
        inQuotes = true;
      } else if (c === ',') {
        result.push(current);
        current = '';
      } else {
        current += c;
      }
    }
  }
  result.push(current);
  return result;
}

function escapeCsvField(field: string): string {
  if (/[",\r\n]/.test(field)) {
    return '"' + field.replace(/"/g, '""') + '"';
  }
  return field;
}

function resolvePaidByName(
  rawValue: string,
  prefixToName: Record<string, string>,
  fullIdToName: Record<string, string>
): string {
  const trimmed = rawValue.trim();

  const prefixMatch = trimmed.match(USER_PREFIX_RE);
  if (prefixMatch) {
    const name = prefixToName[prefixMatch[1].toLowerCase()];
    if (name) return name;
    return 'Unknown member';
  }

  if (UUID_RE.test(trimmed)) {
    const name = fullIdToName[trimmed.toLowerCase()];
    if (name) return name;
    return 'Unknown member';
  }

  if (trimmed) return trimmed;
  return 'Unknown member';
}

function transformCsvPaidBy(
  csvText: string,
  prefixToName: Record<string, string>,
  fullIdToName: Record<string, string>
): string {
  const normalized = csvText.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const lines = normalized.split('\n');
  const transformed: string[] = [];
  let paidByIndex = 4; // Default index for new CSV format with Category

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trim() === '') {
      transformed.push(line);
      continue;
    }
    if (i === 0) {
      const headerFields = parseCsvLine(line);
      const foundIndex = headerFields.findIndex(
        (h) => h.trim().toLowerCase() === 'paid by'
      );
      if (foundIndex !== -1) {
        paidByIndex = foundIndex;
      }
      transformed.push(line);
      continue;
    }
    const fields = parseCsvLine(line);
    if (fields.length > paidByIndex) {
      fields[paidByIndex] = resolvePaidByName(
        fields[paidByIndex],
        prefixToName,
        fullIdToName
      );
    }
    transformed.push(fields.map(escapeCsvField).join(','));
  }

  return transformed.join('\r\n');
}

export default function GroupReportsPage() {
  const { id: groupId } = useParams<{ id: string }>();
  const { user } = useAuth();

  // Page level states
  const [group, setGroup] = useState<GroupResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [analytics, setAnalytics] = useState<GroupAnalyticsResponse | null>(null);

  // Status flags
  const [pageLoading, setPageLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [is404, setIs404] = useState(false);
  const [is403, setIs403] = useState(false);

  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [analyticsError, setAnalyticsError] = useState<string | null>(null);

  // Filter state
  const [timeRange, setTimeRange] = useState<TimeRangeOption>('ALL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [payerId, setPayerId] = useState('');
  const [splitType, setSplitType] = useState<SplitTypeFilter>('ALL');

  // Export state
  const [isExporting, setIsExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  // Member name mapping (full UUID -> name)
  const memberNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const m of members) {
      const lowerId = m.userId.toLowerCase();
      map[lowerId] = m.name || (user && m.userId === user.id ? user.name : 'Unknown member');
    }
    return map;
  }, [members, user]);

  // Prefix lookup for CSV "User-xxxxxxxx" pattern (8-char UUID prefix -> name)
  const memberPrefixNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const m of members) {
      const prefix = m.userId.substring(0, 8).toLowerCase();
      map[prefix] = m.name || (user && m.userId === user.id ? user.name : 'Unknown member');
    }
    return map;
  }, [members, user]);

  // Load group details & members
  const loadPageMeta = useCallback(async (gId: string) => {
    await Promise.resolve();
    setPageLoading(true);
    setPageError(null);
    setIs404(false);
    setIs403(false);

    try {
      const [g, ms] = await Promise.all([getGroupDetail(gId), listMembers(gId)]);
      setGroup(g);
      setMembers(ms);
    } catch (err: unknown) {
      if (isAxiosError(err)) {
        const status = err.response?.status;
        if (status === 404) {
          setIs404(true);
          return;
        }
        if (status === 403) {
          setIs403(true);
          return;
        }
      }
      setPageError(getErrorMessage(err));
    } finally {
      setPageLoading(false);
    }
  }, []);

  // Query sent to the analytics and CSV endpoints. payerId and splitType belong here as well:
  // the backend applies them to the KPI totals, member table, monthly trend, category donut and
  // CSV export, so the filter bar can no longer describe one section while another ignores it.
  const analyticsQuery = useMemo<GroupAnalyticsQueryParams>(() => ({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
    payerId: payerId || undefined,
    splitType: splitType === 'ALL' ? undefined : splitType,
  }), [startDate, endDate, payerId, splitType]);

  // Load analytics with the current filter selection
  const loadAnalytics = useCallback(
    async (gId: string, query: GroupAnalyticsQueryParams) => {
      await Promise.resolve();
      setAnalyticsLoading(true);
      setAnalyticsError(null);
      try {
        const data = await getGroupAnalytics(gId, query);
        setAnalytics(data);
      } catch (err) {
        setAnalyticsError(getErrorMessage(err));
      } finally {
        setAnalyticsLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    if (!groupId) return;
    queueMicrotask(() => {
      void loadPageMeta(groupId);
    });
  }, [groupId, loadPageMeta]);

  useEffect(() => {
    if (!groupId || pageLoading || is404 || is403 || pageError) return;
    queueMicrotask(() => {
      void loadAnalytics(groupId, analyticsQuery);
    });
  }, [groupId, pageLoading, is404, is403, pageError, analyticsQuery, loadAnalytics]);

  // Handler for filter bar changes
  const handleFilterChange = (filters: {
    timeRange: TimeRangeOption;
    startDate: string;
    endDate: string;
    payerId: string;
    splitType: SplitTypeFilter;
  }) => {
    setTimeRange(filters.timeRange);
    setStartDate(filters.startDate);
    setEndDate(filters.endDate);
    setPayerId(filters.payerId);
    setSplitType(filters.splitType);
  };

  const handleResetFilters = () => {
    setTimeRange('ALL');
    setStartDate('');
    setEndDate('');
    setPayerId('');
    setSplitType('ALL');
  };

  // CSV Export
  const handleExportCsv = async () => {
    if (!groupId) return;
    setIsExporting(true);
    setExportError(null);
    try {
      const blob = await exportGroupExpensesCsv(groupId, analyticsQuery);
      const csvText = await blob.text();
      const transformedCsv = transformCsvPaidBy(csvText, memberPrefixNames, memberNames);
      const newBlob = new Blob([transformedCsv], {
        type: blob.type || 'text/csv;charset=utf-8;',
      });
      const url = window.URL.createObjectURL(newBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `expenses-${group?.name ? group.name.replace(/\s+/g, '_') : groupId}.csv`;
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

  // Print friendly handler
  const handlePrint = () => {
    window.print();
  };

  // The member table is no longer filtered in the browser — the backend already restricted the
  // whole response to the selected payer and split type.
  const isFilterActive = Boolean(
    analyticsQuery.startDate || analyticsQuery.endDate || analyticsQuery.payerId || analyticsQuery.splitType
  );
  const hasNoMatchingExpenses =
    !analyticsLoading && !analyticsError && (analytics?.expenseCount ?? 0) === 0;

  // Render guards
  if (pageLoading) {
    return <LoadingState label="Loading report data…" height="lg" />;
  }

  if (is404) {
    return (
      <EmptyState
        icon="🔍"
        heading="Group not found"
        description="This group may have been deleted or you followed a bad link."
        action={<Link to="/groups" className="btn btn--primary">Back to My Groups</Link>}
      />
    );
  }

  if (is403) {
    return (
      <EmptyState
        icon="🔒"
        heading="You can't view reports for this group"
        description="You must be a member of this group to view reports & analytics."
        action={<Link to="/groups" className="btn btn--primary">Back to My Groups</Link>}
      />
    );
  }

  if (pageError) {
    return (
      <div className="alert alert--error groups-page__alert" role="alert">
        <span>{pageError}</span>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => groupId && loadPageMeta(groupId)}
        >
          Retry
        </button>
      </div>
    );
  }

  if (!group) return null;

  return (
    <div className="group-details-page report-page">
      {/* Header */}
      <header className="page-header page-header--report">
        <div className="page-header__title">
          <nav aria-label="Breadcrumb" className="group-details-breadcrumb">
            <Link to="/groups">My Groups</Link>
            <span aria-hidden="true"> / </span>
            <Link to={`/groups/${groupId}`}>{group.name}</Link>
            <span aria-hidden="true"> / </span>
            <span>Reports & Analytics</span>
          </nav>
          <div className="group-details-title-row">
            <h2 className="page-header__heading">📊 Reports & Analytics</h2>
          </div>
          <p className="group-details-meta">
            {group.name} · Real-time financial insights
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
            aria-label="Export expenses to CSV"
          >
            {isExporting ? 'Exporting…' : '📥 Export CSV'}
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => groupId && loadAnalytics(groupId, analyticsQuery)}
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

      {/* Filter toolbar */}
      <section className="group-details-section report-filter-section no-print" aria-label="Report Filters">
        <ReportFilters
          timeRange={timeRange}
          startDate={startDate}
          endDate={endDate}
          payerId={payerId}
          splitType={splitType}
          members={members}
          memberNames={memberNames}
          isLoading={analyticsLoading}
          onFilterChange={handleFilterChange}
          onReset={handleResetFilters}
        />
      </section>

      {/* Analytics Error */}
      {analyticsError && (
        <div className="alert alert--error groups-page__alert" role="alert">
          <span>{analyticsError}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => groupId && loadAnalytics(groupId, analyticsQuery)}
          >
            Retry
          </button>
        </div>
      )}

      {/* No results: say so plainly instead of showing a page of ₹0.00 cards that look like data */}
      {hasNoMatchingExpenses ? (
        <EmptyState
          icon="📊"
          heading="No expenses in this selection"
          description={
            isFilterActive
              ? 'No expenses in this group match the filters you applied. Clear them to see the full report.'
              : 'This group has no expenses yet. Add an expense to see spending analytics and trends.'
          }
          action={
            isFilterActive ? (
              <button type="button" className="btn btn--primary" onClick={handleResetFilters}>
                Clear filters
              </button>
            ) : (
              <Link to={`/groups/${groupId}`} className="btn btn--primary">
                Back to group
              </Link>
            )
          }
        />
      ) : (
        <>
      {/* 1. Financial Overview Section */}
      <section className="group-details-section" aria-label="Financial Overview">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">
            Financial Overview
          </h3>
          <p className="page-header__sub">Key financial metrics for the selected timeframe</p>
        </div>

        <SpendingOverviewCards
          totalSpending={Number(analytics?.totalSpending ?? 0)}
          expenseCount={analytics?.expenseCount ?? 0}
          averageExpense={Number(analytics?.averageExpense ?? 0)}
          totalSettledVolume={Number(analytics?.totalSettledVolume ?? 0)}
          isLoading={analyticsLoading}
        />
      </section>

      {/* 2. Member Spending Section */}
      <section className="group-details-section" aria-label="Member Spending Breakdown">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">
            Member Spending & Consumption
          </h3>
          <p className="page-header__sub">
            Comparison between money paid out versus share consumed by each member
          </p>
        </div>

        {analyticsLoading ? (
          <LoadingState label="Computing member spending…" height="sm" />
        ) : (
          <ParticipantComparisonChart
            mode="GROUP"
            items={analytics?.memberSpending ?? []}
            memberNames={memberNames}
            currentUserId={user?.id ?? null}
          />
        )}
      </section>

      {/* 3. Monthly Spending Timeline Section */}
      <section className="group-details-section" aria-label="Monthly Spending Trends">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">
            Monthly Spending Trends
          </h3>
          <p className="page-header__sub">Expense volume grouped strictly by record creation date</p>
        </div>

        {analyticsLoading ? (
          <LoadingState label="Computing monthly timeline…" height="sm" />
        ) : (
          <InteractiveTrendLineChart monthlyTrends={analytics?.monthlyTrends ?? []} />
        )}
      </section>

      {/* 4. Category Spending Breakdown Section */}
      <section className="group-details-section" aria-label="Category Spending Breakdown">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">
            Category Spending
          </h3>
          <p className="page-header__sub">Breakdown of expenses by category with total volume and percentage</p>
        </div>

        {analyticsLoading ? (
          <LoadingState label="Computing category spending…" height="sm" />
        ) : (
          <InteractiveDonutChart
            categorySpending={analytics?.categorySpending ?? []}
            totalAmount={Number(analytics?.totalSpending ?? 0)}
          />
        )}
      </section>
        </>
      )}
    </div>
  );
}
