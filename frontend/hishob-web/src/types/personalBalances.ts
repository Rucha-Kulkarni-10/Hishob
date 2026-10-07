/**
 * TypeScript types derived directly from the HISHOB Personal Balance Service DTOs.
 *
 * Backend sources:
 *   BalanceStatus.java                       → BalanceStatus
 *   PersonalBalanceResponse.java             → PersonalBalanceResponse
 *   PersonalBalanceSummaryResponse.java      → PersonalBalanceSummaryResponse
 *   FriendBalanceResponse.java               → FriendBalanceResponse
 *   PersonalDebtSimplificationSuggestion.java → PersonalDebtSimplificationSuggestion
 *   PersonalDebtSimplificationResponse.java  → PersonalDebtSimplificationResponse
 */

export type BalanceStatus = 'GETS' | 'OWES' | 'SETTLED';

/** Balance for a single person in personal context */
export interface PersonalBalanceResponse {
  userId: string;        // UUID
  totalPaid: number;
  totalShare: number;
  netBalance: number;
  status: BalanceStatus;
}

/** Response from GET /api/personal-balances */
export interface PersonalBalanceSummaryResponse {
  userId: string;        // UUID
  balances: PersonalBalanceResponse[];
}

/** Friend specific balance from GET /api/friends/{friendUserId}/balance */
export interface FriendBalanceResponse {
  currentUserId: string;             // UUID
  friendUserId: string;              // UUID
  currentUserNetBalance: number;
  friendNetBalance: number;
  currentUserStatus: BalanceStatus;
  friendStatus: BalanceStatus;
  fromUserId: string | null;         // UUID - debtor if any
  toUserId: string | null;           // UUID - creditor if any
  amount: number;
}

/** Debt simplification suggestion */
export interface PersonalDebtSimplificationSuggestion {
  fromUserId: string;   // UUID — debtor
  toUserId: string;     // UUID — creditor
  amount: number;
}

/** Response from GET /api/personal-balances/simplify */
export interface PersonalDebtSimplificationResponse {
  userId: string;       // UUID
  suggestions: PersonalDebtSimplificationSuggestion[];
}
