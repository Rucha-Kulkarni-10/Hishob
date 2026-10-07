import api from './api';
import type { GroupBalanceResponse, SettlementSuggestion } from '../types/balances';

/**
 * Balance Service API client.
 * 
 * Backend endpoints:
 *   GET /api/groups/{groupId}/balances    → GroupBalanceResponse
 *   GET /api/groups/{groupId}/settlements → SettlementSuggestion[]
 */

export async function getGroupBalances(groupId: string): Promise<GroupBalanceResponse> {
  const response = await api.get<GroupBalanceResponse>(`/api/groups/${groupId}/balances`);
  return response.data;
}

export async function getSettlementSuggestions(groupId: string): Promise<SettlementSuggestion[]> {
  const response = await api.get<SettlementSuggestion[]>(`/api/groups/${groupId}/settlements`);
  return response.data;
}
