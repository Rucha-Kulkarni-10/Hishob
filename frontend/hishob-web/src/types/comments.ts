/**
 * Types for Expense Comments and Discussions.
 */

export interface ExpenseCommentResponse {
  id: string;
  expenseId: string;
  authorUserId: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  isEdited: boolean;
}

export interface CreateExpenseCommentRequest {
  content: string;
}

export interface UpdateExpenseCommentRequest {
  content: string;
}
