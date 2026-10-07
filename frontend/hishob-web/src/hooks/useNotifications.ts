/**
 * Notification polling hook.
 *
 * Provides unread notification count with automatic polling.
 * Polls every 30 seconds and handles cleanup on unmount.
 * Avoids polling when the browser tab is hidden.
 */

import { useCallback, useEffect, useState } from 'react';
import { getUnreadCount } from '@/services/notificationService';
import { getToken } from '@/utils/tokenStorage';

const POLL_INTERVAL_MS = 30000; // 30 seconds

export function useNotifications() {
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);

  const fetchUnreadCount = useCallback(async () => {
    await Promise.resolve();
    if (!getToken()) {
      setUnreadCount(0);
      return;
    }

    try {
      setIsLoading(true);
      const response = await getUnreadCount();
      setUnreadCount(response.count);
    } catch (error) {
      // Fail silently - notifications should not break the app
      console.warn('Failed to fetch unread notification count:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Initial fetch
  useEffect(() => {
    queueMicrotask(() => {
      void fetchUnreadCount();
    });
  }, [fetchUnreadCount]);

  // Polling interval and visibility/event listeners
  useEffect(() => {
    const interval = setInterval(() => {
      // Only poll if the document is visible (tab is active) and token exists
      if (!document.hidden && getToken()) {
        void fetchUnreadCount();
      }
    }, POLL_INTERVAL_MS);

    // Listen for visibility changes to poll immediately when tab becomes active
    const handleVisibilityChange = () => {
      if (!document.hidden && getToken()) {
        void fetchUnreadCount();
      }
    };

    // Listen for custom read events to immediately refresh unread count
    const handleNotificationRead = () => {
      if (getToken()) {
        void fetchUnreadCount();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('hishob:notification-read', handleNotificationRead);

    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('hishob:notification-read', handleNotificationRead);
    };
  }, [fetchUnreadCount]);

  return {
    unreadCount,
    isLoading,
    refetch: fetchUnreadCount,
  };
}