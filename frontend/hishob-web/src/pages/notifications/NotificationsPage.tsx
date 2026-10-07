/**
 * NotificationsPage — full notification list for the authenticated user.
 *
 * Features:
 * - Fetches real notifications from GET /api/notifications
 * - Filter tabs: All / Unread
 * - Newest first
 * - Unread / read visual distinction
 * - Individual mark-as-read via PUT /api/notifications/{id}/read
 * - Navigates to relevant HISHOB page on click (group or personal)
 * - Loading state, empty state, error state
 * - Handles groupId = null (personal notifications) safely
 * - Updates unread count via useNotifications refetch
 *
 * Does NOT implement Mark All as Read — backend does not provide that endpoint.
 */

import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { NotificationResponse } from '@/types/notifications';
import { getNotifications, markAsRead } from '@/services/notificationService';
import { getErrorMessage } from '@/utils/errorUtils';
import { getNotificationTarget } from '@/utils/dateUtils';
import { useNotifications } from '@/hooks/useNotifications';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import NotificationItem from '@/components/notifications/NotificationItem';

type FilterKey = 'all' | 'unread';

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'unread', label: 'Unread' },
];

export default function NotificationsPage() {
  const navigate = useNavigate();
  const { refetch: refetchCount } = useNotifications();

  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<FilterKey>('all');

  const fetchNotifications = useCallback(async () => {
    await Promise.resolve();
    setIsLoading(true);
    setError(null);
    try {
      const data = await getNotifications();
      // API returns newest first; sort defensively
      const sorted = [...data].sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setNotifications(sorted);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void fetchNotifications();
    });
  }, [fetchNotifications]);

  const handleNotificationClick = async (notification: NotificationResponse) => {
    if (!notification.isRead) {
      try {
        await markAsRead(notification.id);
        setNotifications((prev) =>
          prev.map((n) =>
            n.id === notification.id
              ? { ...n, isRead: true, readAt: new Date().toISOString() }
              : n
          )
        );
        void refetchCount();
      } catch (err) {
        console.warn('Failed to mark notification as read:', err);
      }
    }
    const target = getNotificationTarget(notification);
    if (target) {
      navigate(target);
    }
  };

  const handleMarkReadOnly = async (
    notification: NotificationResponse,
    e: React.MouseEvent
  ) => {
    e.stopPropagation();
    if (notification.isRead) return;
    try {
      await markAsRead(notification.id);
      setNotifications((prev) =>
        prev.map((n) =>
          n.id === notification.id
            ? { ...n, isRead: true, readAt: new Date().toISOString() }
            : n
        )
      );
      void refetchCount();
    } catch (err) {
      console.warn('Failed to mark notification as read:', err);
    }
  };

  const displayed =
    filter === 'unread'
      ? notifications.filter((n) => !n.isRead)
      : notifications;

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div className="notifications-page">
      {/* Page header */}
      <div className="page-header">
        <div className="page-header__title">
          <h1 className="page-header__heading">Notifications</h1>
          <p className="page-header__sub">
            Recent activity across your groups and personal expenses.
          </p>
        </div>
      </div>

      {/* Summary bar */}
      {!isLoading && !error && (
        <div className="notifications-page__summary">
          <span className="notifications-page__summary-text">
            {unreadCount > 0
              ? `${unreadCount} unread notification${unreadCount === 1 ? '' : 's'}`
              : 'All caught up'}
          </span>
        </div>
      )}

      {/* Filter tabs */}
      <div className="notifications-page__filters" role="tablist" aria-label="Filter notifications">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            role="tab"
            aria-selected={filter === f.key}
            className={`notifications-page__filter-tab${
              filter === f.key ? ' is-active' : ''
            }`}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
            {f.key === 'unread' && unreadCount > 0 && !isLoading && (
              <span className="notifications-page__filter-count">
                {unreadCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Content area */}
      <div className="notifications-page__body">
        {isLoading && (
          <LoadingState label="Loading notifications…" height="md" />
        )}

        {!isLoading && error && (
          <div className="notifications-page__error">
            <p className="notifications-page__error-text">{error}</p>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => void fetchNotifications()}
            >
              Retry
            </button>
          </div>
        )}

        {!isLoading && !error && notifications.length === 0 && (
          <EmptyState
            icon="🔔"
            heading="No notifications yet"
            description="Notifications will appear here when there is activity in your groups or personal expenses."
          />
        )}

        {!isLoading && !error && notifications.length > 0 && displayed.length === 0 && (
          <EmptyState
            icon="✓"
            heading="No unread notifications"
            description="You are all caught up."
            action={
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => setFilter('all')}
              >
                View all notifications
              </button>
            }
          />
        )}

        {!isLoading && !error && displayed.length > 0 && (
          <div
            className="notifications-page__list"
            role="feed"
            aria-label="Notification list"
          >
            {displayed.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                onClick={handleNotificationClick}
                onMarkRead={handleMarkReadOnly}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
