/**
 * TypeScript types derived directly from the HISHOB Expense Service DTOs.
 *
 * Backend sources:
 *   SplitType.java            → SplitType
 *   ExpenseSplitRequest.java  → ExpenseSplitRequest
 *   CreateExpenseRequest.java → CreateExpenseRequest
 *   ExpenseSplitResponse.java → ExpenseSplitResponse
 *   ExpenseResponse.java      → ExpenseResponse
 */

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENTAGE';

/** One entry in the splits array of CreateExpenseRequest */
export interface ExpenseSplitRequest {
  userId: string;        // UUID
  amount?: number;       // required for EXACT
  percentage?: number;   // required for PERCENTAGE, 0–100
}

/** POST /api/expenses  and  PUT /api/expenses/{id} request body */
export interface CreateExpenseRequest {
  groupId: string;             // UUID
  description: string;         // 1–300 chars
  totalAmount: number;         // ≥ 0.01
  paidBy: string;              // UUID, must be a group member
  splitType: SplitType;
  splits: ExpenseSplitRequest[];
}

/** Per-participant split as returned in ExpenseResponse */
export interface ExpenseSplitResponse {
  userId: string;              // UUID
  amount: number;
  percentage: number | null;   // null for EQUAL and EXACT splits
  splitType: SplitType;
}

/** Response shape for all expense endpoints */
export interface ExpenseResponse {
  id: string;                  // UUID
  groupId: string;             // UUID
  description: string;
  totalAmount: number;
  paidBy: string;              // UUID
  createdBy: string;           // UUID
  splitType: SplitType;
  splits: ExpenseSplitResponse[];
  createdAt: string;           // ISO-8601 Instant
  updatedAt: string;           // ISO-8601 Instant
}
