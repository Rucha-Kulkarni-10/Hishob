package com.hishob.notification.controller;

import com.hishob.notification.dto.NotificationResponse;
import com.hishob.notification.dto.UnreadCountResponse;
import com.hishob.notification.principal.AuthenticatedUser;
import com.hishob.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/notifications
     * Get all notifications for the authenticated user, ordered by creation time (newest first).
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<NotificationResponse> notifications = notificationService.getUserNotifications(currentUser.getUserId());
        return ResponseEntity.ok(notifications);
    }

    /**
     * GET /api/notifications/unread-count
     * Get the count of unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UnreadCountResponse response = notificationService.getUnreadCount(currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/notifications/{notificationId}/read
     * Mark a specific notification as read.
     * Verifies that the notification belongs to the authenticated user.
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        NotificationResponse response = notificationService.markAsRead(notificationId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/notifications/{notificationId}/read
     * Alternative endpoint (PATCH) for marking a notification as read.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsReadPatch(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        NotificationResponse response = notificationService.markAsRead(notificationId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

}
