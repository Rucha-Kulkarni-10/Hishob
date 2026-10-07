/**
 * Notification Service API client.
 *
 * All HTTP requests to the HISHOB notification endpoints go through the
 * centralized Axios `api` instance, which targets the API Gateway and
 * attaches the user's Bearer token automatically.
 *
 * Backend endpoints: NotificationController.java @RequestMapping("/api/notifications")
 */

import type {
  NotificationResponse,
  UnreadCountResponse,
} from '@/types/notifications';
import api from './api';

/** Fetch all notifications for the authenticated user, newest first. */
export async function getNotifications(): Promise<NotificationResponse[]> {
  const response = await api.get<NotificationResponse[]>('/api/notifications');
  return response.data;
}

/** Get the count of unread notifications for the authenticated user. */
export async function getUnreadCount(): Promise<UnreadCountResponse> {
  const response = await api.get<UnreadCountResponse>('/api/notifications/unread-count');
  return response.data;
}

/**
 * Mark a specific notification as read.
 * Returns the updated notification.
 */
export async function markAsRead(notificationId: string): Promise<NotificationResponse> {
  const response = await api.put<NotificationResponse>(`/api/notifications/${notificationId}/read`);
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('hishob:notification-read'));
  }
  return response.data;
}