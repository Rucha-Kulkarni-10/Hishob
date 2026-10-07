/**
 * Personal Settlement Service API client.
 *
 * All calls route through the centralized Axios `api` instance (API Gateway).
 *
 * Backend endpoints:
 *   POST /api/personal-settlements
 *   GET  /api/personal-settlements/history
 *   GET  /api/friends/{friendUserId}/settlements
 */

import api from './api';
import type {
  PersonalSettlementHistoryResponse,
  RecordPersonalSettlementRequest,
} from '@/types/personalSettlements';

/** POST /api/personal-settlements — record a personal settlement */
export async function createPersonalSettlement(
  request: RecordPersonalSettlementRequest
): Promise<PersonalSettlementHistoryResponse> {
  const response = await api.post<PersonalSettlementHistoryResponse>(
    '/api/personal-settlements',
    request
  );
  return response.data;
}

/** GET /api/personal-settlements/history — get personal settlement history for current user */
export async function getPersonalSettlementHistory(): Promise<
  PersonalSettlementHistoryResponse[]
> {
  const response = await api.get<PersonalSettlementHistoryResponse[]>(
    '/api/personal-settlements/history'
  );
  return response.data;
}

/** GET /api/friends/{friendUserId}/settlements — get settlement history with specific friend */
export async function getFriendSettlementHistory(
  friendUserId: string
): Promise<PersonalSettlementHistoryResponse[]> {
  const response = await api.get<PersonalSettlementHistoryResponse[]>(
    `/api/friends/${friendUserId}/settlements`
  );
  return response.data;
}
