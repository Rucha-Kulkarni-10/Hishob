package com.hishob.auth.notification.controller;

import com.hishob.auth.entity.User;
import com.hishob.auth.exception.ResourceNotFoundException;
import com.hishob.auth.notification.dto.NotificationResponse;
import com.hishob.auth.notification.dto.UnreadCountResponse;
import com.hishob.auth.notification.service.NotificationService;
import com.hishob.auth.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    private UUID resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName();
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("User email not found in authentication");
        }
        String normEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normEmail)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normEmail));
    }

    /**
     * GET /api/notifications
     * Get all notifications for the authenticated user, ordered by creation time (newest first).
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        List<NotificationResponse> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * GET /api/notifications/unread-count
     * Get the count of unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        UnreadCountResponse response = notificationService.getUnreadCount(userId);
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
            Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        NotificationResponse response = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/notifications/{notificationId}/read
     * Alternative endpoint (PATCH) for marking a notification as read.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsReadPatch(
            @PathVariable UUID notificationId,
            Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        NotificationResponse response = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(response);
    }

}
