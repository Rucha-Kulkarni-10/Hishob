/**
 * Personal Balance Service API client.
 *
 * All calls route through the centralized Axios `api` instance (API Gateway).
 *
 * Backend endpoints:
 *   GET /api/personal-balances
 *   GET /api/friends/{friendUserId}/balance
 *   GET /api/personal-balances/simplify
 */

import api from './api';
import type {
  FriendBalanceResponse,
  PersonalBalanceSummaryResponse,
  PersonalDebtSimplificationResponse,
} from '@/types/personalBalances';

/** GET /api/personal-balances — get summary of personal balances for current user */
export async function getPersonalBalances(): Promise<PersonalBalanceSummaryResponse> {
  const response = await api.get<PersonalBalanceSummaryResponse>(
    '/api/personal-balances'
  );
  return response.data;
}

/** GET /api/friends/{friendUserId}/balance — get pairwise balance with specific friend */
export async function getFriendBalance(
  friendUserId: string
): Promise<FriendBalanceResponse> {
  const response = await api.get<FriendBalanceResponse>(
    `/api/friends/${friendUserId}/balance`
  );
  return response.data;
}

/** GET /api/personal-balances/simplify — get debt simplification suggestions across friends */
export async function getSimplification(): Promise<PersonalDebtSimplificationResponse> {
  const response = await api.get<PersonalDebtSimplificationResponse>(
    '/api/personal-balances/simplify'
  );
  return response.data;
}
