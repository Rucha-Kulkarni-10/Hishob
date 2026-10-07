package com.hishob.notification.dto;

import com.hishob.notification.entity.Notification;
import com.hishob.notification.enums.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * NotificationResponse — DTO for notification data returned to clients.
 *
 * Maps from Notification entity. Used in public API responses.
 */
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private UUID groupId;
    private NotificationType type;
    private String message;
    private Boolean isRead;
    private Instant createdAt;
    private Instant readAt;
    private Map<String, Object> metadata;

    public NotificationResponse() {
    }

    public NotificationResponse(Notification notification) {
        this.id = notification.getId();
        this.userId = notification.getUserId();
        this.groupId = notification.getGroupId();
        this.type = notification.getType();
        this.message = notification.getMessage();
        this.isRead = notification.getIsRead();
        this.createdAt = notification.getCreatedAt();
        this.readAt = notification.getReadAt();
        this.metadata = notification.getMetadata();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Convenience method
    public boolean isRead() {
        return Boolean.TRUE.equals(isRead);
    }
}
