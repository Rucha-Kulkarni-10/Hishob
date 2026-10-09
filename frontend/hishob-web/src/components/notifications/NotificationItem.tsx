/**
 * NotificationItem — renders an individual notification.
 *
 * Responsibilities:
 * - render one notification
 * - unread/read visual state
 * - message
 * - timestamp
 * - appropriate visual icon based on NotificationType
 */

import React from 'react';
import type { NotificationResponse, NotificationType } from '@/types/notifications';
import { formatNotificationTime } from '@/utils/dateUtils';

interface NotificationItemProps {
  notification: NotificationResponse;
  onClick: (notification: NotificationResponse) => void;
  onMarkRead?: (notification: NotificationResponse, e: React.MouseEvent) => void;
}

function renderNotificationIcon(type: NotificationType) {
  switch (type) {
    case 'EXPENSE_ADDED':
    case 'PERSONAL_EXPENSE_ADDED':
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-11a1 1 0 10-2 0v2H7a1 1 0 100 2h2v2a1 1 0 102 0v-2h2a1 1 0 100-2h-2V7z"
            clipRule="evenodd"
          />
        </svg>
      );
    case 'EXPENSE_UPDATED':
    case 'PERSONAL_EXPENSE_UPDATED':
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
        </svg>
      );
    case 'EXPENSE_DELETED':
    case 'PERSONAL_EXPENSE_DELETED':
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
            clipRule="evenodd"
          />
        </svg>
      );
    case 'GROUP_MEMBER_ADDED':
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path d="M8 9a3 3 0 100-6 3 3 0 000 6zM8 11a6 6 0 016 6H2a6 6 0 016-6zM16 7a1 1 0 10-2 0v1h-1a1 1 0 100 2h1v1a1 1 0 102 0v-1h1a1 1 0 100-2h-1V7z" />
        </svg>
      );
    case 'EXPENSE_COMMENT_ADDED':
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M18 10c0 3.866-3.582 7-8 7a8.841 8.841 0 01-4.083-.98L2 17l1.338-3.123C2.493 12.767 2 11.434 2 10c0-3.866 3.582-7 8-7s8 3.134 8 7zM7 9H5v2h2V9zm8 0h-2v2h2V9zm-5 0h-2v2h2V9z"
            clipRule="evenodd"
          />
        </svg>
      );
    case 'SETTLEMENT_RECORDED':
    case 'PERSONAL_SETTLEMENT_RECORDED':
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
            clipRule="evenodd"
          />
        </svg>
      );
    default:
      return (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
            clipRule="evenodd"
          />
        </svg>
      );
  }
}

export default function NotificationItem({
  notification,
  onClick,
  onMarkRead,
}: NotificationItemProps) {
  const isUnread = !notification.isRead;
  const typeModifier = notification.type.toLowerCase().replace(/_/g, '-');

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onClick(notification);
    }
  };

  return (
    <div
      role="button"
      tabIndex={0}
      className={`notification-item ${isUnread ? 'notification-item--unread' : 'notification-item--read'}`}
      onClick={() => onClick(notification)}
      onKeyDown={handleKeyDown}
      aria-label={`${notification.message}. ${isUnread ? 'Unread.' : 'Read.'} ${formatNotificationTime(notification.createdAt)}`}
    >
      <div
        className={`notification-item__icon notification-item__icon--${typeModifier}`}
        aria-hidden="true"
      >
        {renderNotificationIcon(notification.type)}
      </div>

      <div className="notification-item__content">
        <p className="notification-item__message">{notification.message}</p>
        <span className="notification-item__time">
          {formatNotificationTime(notification.createdAt)}
        </span>
      </div>

      <div className="notification-item__actions">
        {isUnread && (
          <>
            {onMarkRead && (
              <button
                type="button"
                className="notification-item__mark-btn"
                title="Mark as read"
                aria-label="Mark as read"
                onClick={(e) => onMarkRead(notification, e)}
              >
                Mark read
              </button>
            )}
            <span
              className="notification-item__unread-dot"
              title="Unread notification"
              aria-hidden="true"
            />
          </>
        )}
      </div>
    </div>
  );
}
