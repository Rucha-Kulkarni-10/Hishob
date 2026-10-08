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

export type ExpenseCategory =
  | 'FOOD'
  | 'GROCERIES'
  | 'TRAVEL'
  | 'SHOPPING'
  | 'ENTERTAINMENT'
  | 'BILLS'
  | 'HEALTH'
  | 'EDUCATION'
  | 'OTHER';

export interface CategoryMetadata {
  label: string;
  icon: string;
}

export const CATEGORY_METADATA: Record<ExpenseCategory, CategoryMetadata> = {
  FOOD: { label: 'Food & Dining', icon: '🍔' },
  GROCERIES: { label: 'Groceries', icon: '🛒' },
  TRAVEL: { label: 'Travel & Transit', icon: '✈️' },
  SHOPPING: { label: 'Shopping', icon: '🛍️' },
  ENTERTAINMENT: { label: 'Entertainment', icon: '🎬' },
  BILLS: { label: 'Bills & Utilities', icon: '📄' },
  HEALTH: { label: 'Health & Fitness', icon: '🏥' },
  EDUCATION: { label: 'Education', icon: '🎓' },
  OTHER: { label: 'Other', icon: '🏷️' },
};

export const EXPENSE_CATEGORIES: ExpenseCategory[] = [
  'FOOD',
  'GROCERIES',
  'TRAVEL',
  'SHOPPING',
  'ENTERTAINMENT',
  'BILLS',
  'HEALTH',
  'EDUCATION',
  'OTHER',
];

export function getCategoryMeta(category?: ExpenseCategory | null): CategoryMetadata {
  if (category && CATEGORY_METADATA[category]) {
    return CATEGORY_METADATA[category];
  }
  return CATEGORY_METADATA.OTHER;
}

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
  category?: ExpenseCategory;
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
  category: ExpenseCategory;
  splits: ExpenseSplitResponse[];
  createdAt: string;           // ISO-8601 Instant
  updatedAt: string;           // ISO-8601 Instant
}
