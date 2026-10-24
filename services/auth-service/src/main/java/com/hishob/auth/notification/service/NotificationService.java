package com.hishob.auth.notification.service;

import com.hishob.auth.notification.dto.CreateNotificationRequest;
import com.hishob.auth.notification.dto.NotificationResponse;
import com.hishob.auth.notification.dto.UnreadCountResponse;
import com.hishob.auth.notification.entity.Notification;
import com.hishob.auth.notification.exception.NotificationNotFoundException;
import com.hishob.auth.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Get all notifications for the authenticated user, ordered by creation time (newest first).
     */
    public List<NotificationResponse> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get unread notifications count for the authenticated user.
     */
    public UnreadCountResponse getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    /**
     * Mark a specific notification as read.
     * Verifies that the notification belongs to the authenticated user.
     */
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found or does not belong to user"));

        if (!notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    /**
     * Internal API: Create a new notification for a user.
     * Called by other services via internal endpoints.
     */
    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setGroupId(request.getGroupId());
        notification.setType(request.getType());
        notification.setMessage(request.getMessage());
        notification.setIsRead(false);
        notification.setMetadata(request.getMetadata());

        notification = notificationRepository.save(notification);
        return toResponse(notification);
    }

    /**
     * Helper: Mark all notifications as read for a user.
     * Can be used for "mark all as read" functionality.
     */
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadForUser(userId, Instant.now());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n);
    }

}
