/**
 * ReportFilters — provides time range, payer, and split type filter controls.
 */

import { useState } from 'react';
import type { TimeRangeOption, SplitTypeFilter } from '@/types/analytics';
import type { MemberResponse } from '@/types/groups';

interface ReportFiltersProps {
  timeRange: TimeRangeOption;
  startDate: string;
  endDate: string;
  payerId: string;
  splitType: SplitTypeFilter;
  members: MemberResponse[];
  memberNames: Record<string, string>;
  isLoading: boolean;
  onFilterChange: (filters: {
    timeRange: TimeRangeOption;
    startDate: string;
    endDate: string;
    payerId: string;
    splitType: SplitTypeFilter;
  }) => void;
  onReset: () => void;
}

export default function ReportFilters({
  timeRange,
  startDate,
  endDate,
  payerId,
  splitType,
  members,
  memberNames,
  isLoading,
  onFilterChange,
  onReset,
}: ReportFiltersProps) {
  const [localTimeRange, setLocalTimeRange] = useState<TimeRangeOption>(timeRange);
  const [localStart, setLocalStart] = useState(startDate);
  const [localEnd, setLocalEnd] = useState(endDate);
  const [localPayer, setLocalPayer] = useState(payerId);
  const [localSplit, setLocalSplit] = useState<SplitTypeFilter>(splitType);

  const handleTimeRangeClick = (range: TimeRangeOption) => {
    setLocalTimeRange(range);
    let s = '';
    let e = '';

    const now = new Date();
    if (range === 'THIS_MONTH') {
      const year = now.getFullYear();
      const month = String(now.getMonth() + 1).padStart(2, '0');
      s = `${year}-${month}-01`;
      e = now.toISOString().split('T')[0];
    } else if (range === 'LAST_30_DAYS') {
      const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
      s = thirtyDaysAgo.toISOString().split('T')[0];
      e = now.toISOString().split('T')[0];
    }

    setLocalStart(s);
    setLocalEnd(e);

    onFilterChange({
      timeRange: range,
      startDate: s,
      endDate: e,
      payerId: localPayer,
      splitType: localSplit,
    });
  };

  const handleApply = (e: React.FormEvent) => {
    e.preventDefault();
    onFilterChange({
      timeRange: localTimeRange,
      startDate: localStart,
      endDate: localEnd,
      payerId: localPayer,
      splitType: localSplit,
    });
  };

  const handleReset = () => {
    setLocalTimeRange('ALL');
    setLocalStart('');
    setLocalEnd('');
    setLocalPayer('');
    setLocalSplit('ALL');
    onReset();
  };

  return (
    <div className="report-filter-bar">
      {/* Preset Time Range Chips */}
      <div className="report-filter-presets">
        <button
          type="button"
          className={`btn btn--sm ${localTimeRange === 'ALL' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => handleTimeRangeClick('ALL')}
          disabled={isLoading}
        >
          All Time
        </button>
        <button
          type="button"
          className={`btn btn--sm ${localTimeRange === 'THIS_MONTH' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => handleTimeRangeClick('THIS_MONTH')}
          disabled={isLoading}
        >
          This Month
        </button>
        <button
          type="button"
          className={`btn btn--sm ${localTimeRange === 'LAST_30_DAYS' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => handleTimeRangeClick('LAST_30_DAYS')}
          disabled={isLoading}
        >
          Last 30 Days
        </button>
        <button
          type="button"
          className={`btn btn--sm ${localTimeRange === 'CUSTOM' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => setLocalTimeRange('CUSTOM')}
          disabled={isLoading}
        >
          Custom Range
        </button>
      </div>

      {/* Advanced Filters Form */}
      <form onSubmit={handleApply} className="report-filter-controls">
        {localTimeRange === 'CUSTOM' && (
          <div className="report-filter-field-group">
            <div className="form-field">
              <label htmlFor="filter-start-date" className="form-field__label">
                From
              </label>
              <input
                id="filter-start-date"
                type="date"
                className="form-field__input"
                value={localStart}
                onChange={(e) => setLocalStart(e.target.value)}
                disabled={isLoading}
              />
            </div>
            <div className="form-field">
              <label htmlFor="filter-end-date" className="form-field__label">
                To
              </label>
              <input
                id="filter-end-date"
                type="date"
                className="form-field__input"
                value={localEnd}
                onChange={(e) => setLocalEnd(e.target.value)}
                disabled={isLoading}
              />
            </div>
          </div>
        )}

        {/* Payer Filter */}
        <div className="form-field">
          <label htmlFor="filter-payer" className="form-field__label">
            Payer
          </label>
          <select
            id="filter-payer"
            className="form-field__input"
            value={localPayer}
            onChange={(e) => setLocalPayer(e.target.value)}
            disabled={isLoading}
          >
            <option value="">All Members</option>
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>
                {memberNames[m.userId] || m.name || 'Unknown member'}
              </option>
            ))}
          </select>
        </div>

        {/* Split Type Filter */}
        <div className="form-field">
          <label htmlFor="filter-split" className="form-field__label">
            Split Type
          </label>
          <select
            id="filter-split"
            className="form-field__input"
            value={localSplit}
            onChange={(e) => setLocalSplit(e.target.value as SplitTypeFilter)}
            disabled={isLoading}
          >
            <option value="ALL">All Types</option>
            <option value="EQUAL">EQUAL</option>
            <option value="EXACT">EXACT</option>
            <option value="PERCENTAGE">PERCENTAGE</option>
          </select>
        </div>

        <div className="report-filter-actions">
          <button type="submit" className="btn btn--primary btn--sm" disabled={isLoading}>
            Apply
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={handleReset}
            disabled={isLoading}
          >
            Reset
          </button>
        </div>
      </form>
    </div>
  );
}
