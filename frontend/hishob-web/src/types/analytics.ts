/**
 * Analytics and Reporting Types for HISHOB
 */

import type { ExpenseCategory } from './expenses';

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

export interface CategorySpendingSummary {
  category: ExpenseCategory;
  totalAmount: number;
  expenseCount: number;
  percentageOfTotal: number;
}

/**
 * Mirrors com.hishob.expense.dto.FriendSpendingSummary — the backend names the volume field
 * totalAmount, so the UI must not invent a totalSpending alias.
 */
export interface FriendSpendingSummary {
  friendUserId: string;
  friendName?: string;
  totalAmount: number;
  expenseCount: number;
  userShare?: number;
  friendShare?: number;
}

export interface PersonalAnalyticsResponse {
  totalSpending: number;
  expenseCount: number;
  averageExpense: number;
  totalPaidByUser: number;
  totalUserShare: number;
  categorySpending?: CategorySpendingSummary[];
  friendSpending?: FriendSpendingSummary[];
  monthlyTrends: MonthlySpendingSummary[];
}

export interface GroupAnalyticsResponse {
  groupId: string;
  totalSpending: number;
  expenseCount: number;
  averageExpense: number;
  totalSettledVolume: number;
  memberSpending: MemberSpendingSummary[];
  monthlyTrends: MonthlySpendingSummary[];
  categorySpending?: CategorySpendingSummary[];
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

