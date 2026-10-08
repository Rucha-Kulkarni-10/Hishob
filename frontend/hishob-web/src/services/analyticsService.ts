/**
 * Analytics and Reporting Service API client.
 *
 * Targets API Gateway (http://localhost:8080) routed to expense-service:
 *   - GET /api/groups/{groupId}/expenses/analytics
 *   - GET /api/groups/{groupId}/expenses/export/csv
 */

import api from './api';
import type { GroupAnalyticsResponse, PersonalAnalyticsResponse } from '@/types/analytics';

export interface AnalyticsQueryParams {
  startDate?: string;
  endDate?: string;
}

/**
 * Fetch financial overview, member spending and monthly trends for a group.
 */
export async function getGroupAnalytics(
  groupId: string,
  params?: AnalyticsQueryParams
): Promise<GroupAnalyticsResponse> {
  const response = await api.get<GroupAnalyticsResponse>(
    `/api/groups/${groupId}/expenses/analytics`,
    { params }
  );
  return response.data;
}

/**
 * Export group expenses as CSV.
 * Returns a Blob suitable for browser download.
 */
export async function exportGroupExpensesCsv(
  groupId: string,
  params?: AnalyticsQueryParams
): Promise<Blob> {
  const response = await api.get(`/api/groups/${groupId}/expenses/export/csv`, {
    params,
    responseType: 'blob',
  });
  return response.data;
}

/**
 * Fetch financial overview, friend spending, monthly trends, and category breakdown for personal expenses.
 */
export async function getPersonalAnalytics(
  params?: AnalyticsQueryParams
): Promise<PersonalAnalyticsResponse> {
  const response = await api.get<PersonalAnalyticsResponse>(
    '/api/personal-expenses/analytics',
    { params }
  );
  return response.data;
}

/**
 * Export personal expenses as CSV.
 * Returns a Blob suitable for browser download.
 */
export async function exportPersonalExpensesCsv(
  params?: AnalyticsQueryParams
): Promise<Blob> {
  const response = await api.get('/api/personal-expenses/export/csv', {
    params,
    responseType: 'blob',
  });
  return response.data;
}

