/**
 * TypeScript types derived directly from the HISHOB Balance Service DTOs.
 *
 * Backend sources:
 *   BalanceStatus.java         → BalanceStatus
 *   UserBalanceResponse.java   → MemberBalance
 *   GroupBalanceResponse.java  → GroupBalanceResponse
 *   SettlementSuggestion.java  → SettlementSuggestion  (settlement-service)
 */

/** Balance status returned per member. */
export type BalanceStatus = 'GETS' | 'OWES' | 'SETTLED';

/**
 * Per-member balance entry inside GroupBalanceResponse.
 * netBalance = totalPaid − totalShare (calculated server-side).
 * Positive → member is owed (GETS). Negative → member owes (OWES). Zero → SETTLED.
 */
export interface MemberBalance {
  userId: string;        // UUID
  totalPaid: number;     // total amount paid as payer across all group expenses
  totalShare: number;    // total share owed across all group expenses
  netBalance: number;    // totalPaid − totalShare, after completed settlements applied
  status: BalanceStatus;
}

/** Response from GET /api/groups/{groupId}/balances */
export interface GroupBalanceResponse {
  groupId: string;              // UUID
  balances: MemberBalance[];
}

/**
 * One simplified debt transaction from GET /api/groups/{groupId}/settlements.
 * fromUserId = the person who should PAY.
 * toUserId   = the person who should RECEIVE.
 */
export interface SettlementSuggestion {
  fromUserId: string;   // UUID — debtor
  toUserId: string;     // UUID — creditor
  amount: number;
}
