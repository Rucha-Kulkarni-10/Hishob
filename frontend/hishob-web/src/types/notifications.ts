/**
 * TypeScript types derived directly from the HISHOB Notification Service DTOs.
 *
 * Backend sources:
 *   NotificationResponse.java   → NotificationResponse
 *   UnreadCountResponse.java    → UnreadCountResponse
 *   NotificationType.java       → NotificationType
 */

/** Notification types matching backend NotificationType enum. */
export type NotificationType = 
  | 'GROUP_MEMBER_ADDED'
  | 'EXPENSE_ADDED'
  | 'EXPENSE_UPDATED'
  | 'EXPENSE_DELETED'
  | 'SETTLEMENT_RECORDED'
  | 'PERSONAL_EXPENSE_ADDED'
  | 'PERSONAL_EXPENSE_UPDATED'
  | 'PERSONAL_EXPENSE_DELETED'
  | 'PERSONAL_SETTLEMENT_RECORDED'
  | 'EXPENSE_COMMENT_ADDED';

/** Notification as returned by the backend GET /api/notifications. */
export interface NotificationResponse {
  id: string;                    // UUID
  userId: string;                // UUID
  groupId: string | null;        // UUID or null for personal notifications
  type: NotificationType;        // enum
  message: string;               // Human-readable message
  isRead: boolean;               // Read status
  createdAt: string;             // ISO-8601 Instant
  readAt: string | null;         // ISO-8601 Instant or null if unread
  metadata: Record<string, unknown>; // Additional notification data
}

/** Unread count response from GET /api/notifications/unread-count. */
export interface UnreadCountResponse {
  count: number; // Long in Java, number in TypeScript
}