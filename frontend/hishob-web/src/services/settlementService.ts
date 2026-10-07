/**
 * Settlement Service
 * Handles settlement recording and history retrieval
 */

import api from './api';
import type { RecordSettlementRequest, SettlementHistoryItem } from '../types/settlements';

/**
 * Record a settlement between users
 * POST /api/groups/{groupId}/settlements
 * 
 * @param groupId - Group ID
 * @param request - Settlement details (fromUserId extracted from JWT on backend)
 * @returns Promise resolving when settlement is recorded
 */
export const recordSettlement = async (
  groupId: string,
  request: RecordSettlementRequest
): Promise<void> => {
  await api.post(`/api/groups/${groupId}/settlements`, request);
};

/**
 * Get settlement history for a group
 * GET /api/groups/{groupId}/settlements/history
 * 
 * @param groupId - Group ID
 * @returns Promise resolving to array of settlement history items
 */
export const getSettlementHistory = async (
  groupId: string
): Promise<SettlementHistoryItem[]> => {
  const response = await api.get<SettlementHistoryItem[]>(
    `/api/groups/${groupId}/settlements/history`
  );
  return response.data;
};
