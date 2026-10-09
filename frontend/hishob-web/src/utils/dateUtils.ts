/**
 * Date and notification helpers.
 */

import type { NotificationResponse } from '@/types/notifications';

/**
 * Format an ISO-8601 timestamp into a readable relative or short date string.
 * Examples: "Just now", "5m ago", "2h ago", "Yesterday", "3d ago", "Jan 15, 2026"
 */
export function formatNotificationTime(isoDate: string): string {
  if (!isoDate) return '';
  try {
    const date = new Date(isoDate);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();

    if (diffMs < 0) return 'Just now';

    const diffSecs = Math.floor(diffMs / 1000);
    if (diffSecs < 60) return 'Just now';

    const diffMins = Math.floor(diffSecs / 60);
    if (diffMins < 60) return `${diffMins}m ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    const diffDays = Math.floor(diffHours / 24);
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
    });
  } catch {
    return isoDate;
  }
}

/**
 * Derives a valid navigation target for a notification, or null if no valid target exists.
 * Do not invent navigation targets if metadata does not support them.
 */
export function getNotificationTarget(notification: NotificationResponse): string | null {
  // Handle group notifications
  if (notification.groupId) {
    switch (notification.type) {
      case 'EXPENSE_ADDED':
      case 'EXPENSE_UPDATED':
      case 'EXPENSE_DELETED':
      case 'EXPENSE_COMMENT_ADDED':
        return `/groups/${notification.groupId}/expenses`;
      case 'SETTLEMENT_RECORDED':
        return `/groups/${notification.groupId}/balances`;
      case 'GROUP_MEMBER_ADDED':
      default:
        return `/groups/${notification.groupId}`;
    }
  }

  // Handle personal notification types
  switch (notification.type) {
    case 'PERSONAL_EXPENSE_ADDED':
    case 'PERSONAL_EXPENSE_UPDATED':
    case 'PERSONAL_EXPENSE_DELETED':
    case 'EXPENSE_COMMENT_ADDED':
      return '/expenses';
    case 'PERSONAL_SETTLEMENT_RECORDED':
      return '/balances';
    default:
      return null;
  }
}
