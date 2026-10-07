/**
 * Personal Expense Service API client.
 *
 * All calls route through the centralized Axios `api` instance (API Gateway).
 *
 * Backend endpoints:
 *   GET    /api/personal-expenses
 *   GET    /api/personal-expenses/{expenseId}
 *   POST   /api/personal-expenses
 *   PUT    /api/personal-expenses/{expenseId}
 *   DELETE /api/personal-expenses/{expenseId}
 *   GET    /api/friends/{friendUserId}/expenses
 */

import api from './api';
import type {
  CreatePersonalExpenseRequest,
  PersonalExpenseResponse,
  UpdatePersonalExpenseRequest,
} from '@/types/personalExpenses';

/** GET /api/personal-expenses — list all personal expenses involving the current user */
export async function getPersonalExpenses(): Promise<PersonalExpenseResponse[]> {
  const response = await api.get<PersonalExpenseResponse[]>('/api/personal-expenses');
  return response.data;
}

/** GET /api/personal-expenses/{expenseId} — fetch a single personal expense by ID */
export async function getPersonalExpense(
  expenseId: string
): Promise<PersonalExpenseResponse> {
  const response = await api.get<PersonalExpenseResponse>(
    `/api/personal-expenses/${expenseId}`
  );
  return response.data;
}

/** POST /api/personal-expenses — create a new personal expense */
export async function createPersonalExpense(
  request: CreatePersonalExpenseRequest
): Promise<PersonalExpenseResponse> {
  const response = await api.post<PersonalExpenseResponse>(
    '/api/personal-expenses',
    request
  );
  return response.data;
}

/** PUT /api/personal-expenses/{expenseId} — update an existing personal expense (creator only) */
export async function updatePersonalExpense(
  expenseId: string,
  request: UpdatePersonalExpenseRequest
): Promise<PersonalExpenseResponse> {
  const response = await api.put<PersonalExpenseResponse>(
    `/api/personal-expenses/${expenseId}`,
    request
  );
  return response.data;
}

/** DELETE /api/personal-expenses/{expenseId} — delete an expense (creator only) */
export async function deletePersonalExpense(expenseId: string): Promise<void> {
  await api.delete(`/api/personal-expenses/${expenseId}`);
}

/** GET /api/friends/{friendUserId}/expenses — list personal expenses shared between current user and friend */
export async function getFriendPersonalExpenses(
  friendUserId: string
): Promise<PersonalExpenseResponse[]> {
  const response = await api.get<PersonalExpenseResponse[]>(
    `/api/friends/${friendUserId}/expenses`
  );
  return response.data;
}
