/**
 * Analytics and Reporting Types for HISHOB
 */

export interface MemberSpendingSummary {
  userId: string;
  totalPaid: number;
  totalShare: number;
  netPosition: number;
  status: 'OWED' | 'OWES' | 'SETTLED' | string;
}

export interface MonthlySpendingSummary {
  yearMonth: string;
  label: string;
  totalAmount: number;
  expenseCount: number;
}

export interface GroupAnalyticsResponse {
  groupId: string;
  totalSpending: number;
  expenseCount: number;
  averageExpense: number;
  totalSettledVolume: number;
  memberSpending: MemberSpendingSummary[];
  monthlyTrends: MonthlySpendingSummary[];
}

export type TimeRangeOption = 'ALL' | 'THIS_MONTH' | 'LAST_30_DAYS' | 'CUSTOM';
export type SplitTypeFilter = 'ALL' | 'EQUAL' | 'EXACT' | 'PERCENTAGE';

export interface AnalyticsFilterState {
  timeRange: TimeRangeOption;
  startDate?: string;
  endDate?: string;
  payerId?: string;
  splitType?: SplitTypeFilter;
}
