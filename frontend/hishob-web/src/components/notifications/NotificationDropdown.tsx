/**
 * NotificationDropdown — desktop popover showing recent notifications.
 *
 * Responsibilities:
 * - Fetch recent notifications via GET /api/notifications
 * - Show newest first (top 5-10)
 * - Click unread: markAsRead -> update state -> refresh count -> navigate if target exists
 * - Provide "View all notifications" link to /notifications
 * - Handle loading, empty state, and API error gracefully
 */

import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { NotificationResponse } from '@/types/notifications';
import { getNotifications, markAsRead } from '@/services/notificationService';
import { getErrorMessage } from '@/utils/errorUtils';
import { getNotificationTarget } from '@/utils/dateUtils';
import LoadingState from '@/components/common/LoadingState';
import NotificationItem from './NotificationItem';

interface NotificationDropdownProps {
  onClose: () => void;
  onNotificationRead?: () => void;
}

export default function NotificationDropdown({
  onClose,
  onNotificationRead,
}: NotificationDropdownProps) {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRecentNotifications = useCallback(async (showLoading = false) => {
    await Promise.resolve();
    if (showLoading) {
      setIsLoading(true);
    }
    try {
      setError(null);
      const data = await getNotifications();
      // Sort newest first
      const sorted = [...data].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setNotifications(sorted.slice(0, 10));
    } catch (err) {
      console.warn('Failed to load notifications in dropdown:', err);
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void fetchRecentNotifications();
    });
  }, [fetchRecentNotifications]);

  const handleNotificationClick = async (notification: NotificationResponse) => {
    // 1. Mark as read if unread
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
        onNotificationRead?.();
      } catch (err) {
        console.warn('Failed to mark notification as read:', err);
      }
    }

    // 2. Close dropdown
    onClose();

    // 3. Navigate if target is valid
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
    try {
      await markAsRead(notification.id);
      setNotifications((prev) =>
        prev.map((n) =>
          n.id === notification.id
            ? { ...n, isRead: true, readAt: new Date().toISOString() }
            : n
        )
      );
      onNotificationRead?.();
    } catch (err) {
      console.warn('Failed to mark notification as read:', err);
    }
  };

  const handleViewAllClick = (e: React.MouseEvent) => {
    e.preventDefault();
    onClose();
    navigate('/notifications');
  };

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div
      className="notification-dropdown"
      role="region"
      aria-label="Recent notifications"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="notification-dropdown__header">
        <h3 className="notification-dropdown__title">Notifications</h3>
        {unreadCount > 0 && (
          <span className="notification-dropdown__badge">
            {unreadCount} unread
          </span>
        )}
      </div>

      <div className="notification-dropdown__body">
        {isLoading && (
          <div className="notification-dropdown__state">
            <LoadingState label="Loading notifications…" height="sm" />
          </div>
        )}

        {!isLoading && error && (
          <div className="notification-dropdown__state notification-dropdown__error">
            <p className="notification-dropdown__error-text">{error}</p>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={() => void fetchRecentNotifications(true)}
            >
              Retry
            </button>
          </div>
        )}

        {!isLoading && !error && notifications.length === 0 && (
          <div className="notification-dropdown__state notification-dropdown__empty">
            <span className="notification-dropdown__empty-icon" aria-hidden="true">
              🔔
            </span>
            <p className="notification-dropdown__empty-text">No notifications yet</p>
            <p className="notification-dropdown__empty-sub">
              You are all caught up!
            </p>
          </div>
        )}

        {!isLoading && !error && notifications.length > 0 && (
          <div className="notification-dropdown__list" role="feed">
            {notifications.map((notification) => (
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

      <div className="notification-dropdown__footer">
        <button
          type="button"
          className="notification-dropdown__view-all-btn"
          onClick={handleViewAllClick}
        >
          View all notifications →
        </button>
      </div>
    </div>
  );
}
