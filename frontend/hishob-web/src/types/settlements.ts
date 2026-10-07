/**
 * Settlement-related type definitions
 * Maps to Settlement Service DTOs
 */

/**
 * Request to record a settlement between users
 * POST /api/groups/{groupId}/settlements
 * 
 * Note: fromUserId is extracted from JWT on backend
 */
export interface RecordSettlementRequest {
  toUserId: string;
  amount: number;
  notes?: string;
}

/**
 * Settlement history item
 * Maps to SettlementHistoryResponse from backend
 */
export interface SettlementHistoryItem {
  id: string;
  fromUserId: string;
  fromUserName: string;
  toUserId: string;
  toUserName: string;
  amount: number;
  notes?: string;
  status: 'PENDING' | 'COMPLETED';
  createdAt: string; // ISO 8601 datetime from backend
  updatedAt?: string;
  settledAt?: string; // Optional alias for backward compatibility
}
