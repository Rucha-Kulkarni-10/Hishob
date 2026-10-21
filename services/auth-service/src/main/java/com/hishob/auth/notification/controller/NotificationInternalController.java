package com.hishob.auth.notification.controller;

import com.hishob.auth.notification.dto.CreateNotificationRequest;
import com.hishob.auth.notification.dto.NotificationResponse;
import com.hishob.auth.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only controller for Notification functionality in Auth Service.
 *
 * All endpoints under /internal/** are:
 *   - Protected by X-Internal-Token via InternalAuthFilter
 *   - NOT routed through the API Gateway
 *   - NOT accessible by public clients
 *
 * Used by other services (Group, Expense, Settlement) to create notifications for users.
 */
@RestController
@RequestMapping("/internal/notifications")
public class NotificationInternalController {

    private final NotificationService notificationService;

    public NotificationInternalController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * POST /internal/notifications
     * Create a new notification for a user.
     * Called by other microservices when events occur that require user notification.
     *
     * Requires: X-Internal-Token header.
     * Not accessible through the API Gateway.
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request) {
        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
