/**
 * Expense Comment Service API client.
 *
 * All requests route through the centralized Axios `api` instance (API Gateway).
 *
 * Backend endpoints:
 *   GET    /api/expenses/{expenseId}/comments
 *   POST   /api/expenses/{expenseId}/comments
 *   PUT    /api/expenses/{expenseId}/comments/{commentId}
 *   DELETE /api/expenses/{expenseId}/comments/{commentId}
 */

import api from './api';
import type {
  CreateExpenseCommentRequest,
  ExpenseCommentResponse,
  UpdateExpenseCommentRequest,
} from '@/types/comments';

/** GET /api/expenses/{expenseId}/comments — list all comments for an expense */
export async function getExpenseComments(
  expenseId: string
): Promise<ExpenseCommentResponse[]> {
  const response = await api.get<ExpenseCommentResponse[]>(
    `/api/expenses/${expenseId}/comments`
  );
  return response.data;
}

/** POST /api/expenses/{expenseId}/comments — add a comment to an expense */
export async function createExpenseComment(
  expenseId: string,
  data: CreateExpenseCommentRequest
): Promise<ExpenseCommentResponse> {
  const response = await api.post<ExpenseCommentResponse>(
    `/api/expenses/${expenseId}/comments`,
    data
  );
  return response.data;
}

/** PUT /api/expenses/{expenseId}/comments/{commentId} — update an existing comment */
export async function updateExpenseComment(
  expenseId: string,
  commentId: string,
  data: UpdateExpenseCommentRequest
): Promise<ExpenseCommentResponse> {
  const response = await api.put<ExpenseCommentResponse>(
    `/api/expenses/${expenseId}/comments/${commentId}`,
    data
  );
  return response.data;
}

/** DELETE /api/expenses/{expenseId}/comments/{commentId} — delete a comment */
export async function deleteExpenseComment(
  expenseId: string,
  commentId: string
): Promise<void> {
  await api.delete(`/api/expenses/${expenseId}/comments/${commentId}`);
}
