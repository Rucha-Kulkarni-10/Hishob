/**
 * TypeScript types derived directly from the HISHOB Personal Expense Service DTOs.
 *
 * Backend sources:
 *   SplitType.java                    → SplitType
 *   CreatePersonalExpenseRequest.java → CreatePersonalExpenseRequest
 *   UpdatePersonalExpenseRequest.java → UpdatePersonalExpenseRequest
 *   ExpenseSplitResponse.java         → PersonalExpenseSplitResponse
 *   ExpenseResponse.java              → PersonalExpenseResponse
 */

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENTAGE';

/** One entry in the splits array for Create/Update personal expense */
export interface PersonalExpenseSplitRequest {
  userId: string;        // UUID
  amount?: number;       // required for EXACT
  percentage?: number;   // required for PERCENTAGE, 0–100
}

/** POST /api/personal-expenses request body */
export interface CreatePersonalExpenseRequest {
  description: string;         // 1–300 chars
  totalAmount: number;         // >= 0.01
  paidBy: string;              // UUID
  splitType: SplitType;
  splits: PersonalExpenseSplitRequest[];
}

/** PUT /api/personal-expenses/{id} request body */
export interface UpdatePersonalExpenseRequest {
  description: string;         // 1–300 chars
  totalAmount: number;         // >= 0.01
  paidBy: string;              // UUID
  splitType: SplitType;
  splits: PersonalExpenseSplitRequest[];
}

/** Per-participant split as returned in ExpenseResponse */
export interface PersonalExpenseSplitResponse {
  userId: string;              // UUID
  amount: number;
  percentage: number | null;   // null for EQUAL and EXACT splits
  splitType: SplitType;
}

/** Response shape for personal expense endpoints */
export interface PersonalExpenseResponse {
  id: string;                  // UUID
  groupId?: string | null;     // null for personal expenses
  description: string;
  totalAmount: number;
  paidBy: string;              // UUID
  createdBy: string;           // UUID
  splitType: SplitType;
  splits: PersonalExpenseSplitResponse[];
  createdAt: string;           // ISO-8601 Instant
  updatedAt: string;           // ISO-8601 Instant
}
