/**
 * TypeScript types derived directly from the HISHOB Personal Settlement Service DTOs.
 *
 * Backend sources:
 *   RecordPersonalSettlementRequest.java → RecordPersonalSettlementRequest
 *   SettlementHistoryResponse.java       → PersonalSettlementHistoryResponse
 */

/**
 * Request to record a personal settlement
 * POST /api/personal-settlements
 *
 * Note: fromUserId is determined by backend from the JWT.
 */
export interface RecordPersonalSettlementRequest {
  toUserId: string;   // UUID
  amount: number;     // >= 0.01
  notes?: string;     // max 500 chars
}

/**
 * Personal settlement history response
 * Maps to SettlementHistoryResponse from backend
 */
export interface PersonalSettlementHistoryResponse {
  id: string;                    // UUID
  fromUserId: string;            // UUID
  toUserId: string;              // UUID
  amount: number;
  status: 'PENDING' | 'COMPLETED';
  notes?: string | null;
  createdAt: string;             // ISO-8601 Instant
  updatedAt: string;             // ISO-8601 Instant
}
