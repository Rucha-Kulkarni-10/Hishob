/**
 * Expense Service API client.
 *
 * Every call routes through the centralized Axios `api` instance,
 * which targets the API Gateway (http://localhost:8080) and attaches
 * the user's Bearer token automatically.
 *
 * Backend endpoints: ExpenseController.java
 */

import type {
  CreateExpenseRequest,
  ExpenseResponse,
} from '@/types/expenses';
import api from './api';

/** POST /api/expenses — create a new expense. Returns 201 on success. */
export async function createExpense(
  data: CreateExpenseRequest
): Promise<ExpenseResponse> {
  const response = await api.post<ExpenseResponse>('/api/expenses', data);
  return response.data;
}

/** GET /api/groups/{groupId}/expenses — list all expenses in a group, newest first. */
export async function getGroupExpenses(
  groupId: string
): Promise<ExpenseResponse[]> {
  const response = await api.get<ExpenseResponse[]>(
    `/api/groups/${groupId}/expenses`
  );
  return response.data;
}

/** GET /api/expenses/{expenseId} — fetch a single expense by UUID. */
export async function getExpense(expenseId: string): Promise<ExpenseResponse> {
  const response = await api.get<ExpenseResponse>(
    `/api/expenses/${expenseId}`
  );
  return response.data;
}

/**
 * PUT /api/expenses/{expenseId} — update an existing expense.
 * Only the original creator (createdBy) may call this.
 */
export async function updateExpense(
  expenseId: string,
  data: CreateExpenseRequest
): Promise<ExpenseResponse> {
  const response = await api.put<ExpenseResponse>(
    `/api/expenses/${expenseId}`,
    data
  );
  return response.data;
}

/**
 * DELETE /api/expenses/{expenseId} — delete an expense.
 * Only the original creator (createdBy) may call this.
 * Returns 204 No Content on success.
 */
export async function deleteExpense(expenseId: string): Promise<void> {
  await api.delete(`/api/expenses/${expenseId}`);
}
