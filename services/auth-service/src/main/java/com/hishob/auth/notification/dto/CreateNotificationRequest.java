package com.hishob.auth.notification.dto;

import com.hishob.auth.notification.enums.NotificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * CreateNotificationRequest — request DTO for internal notification creation.
 *
 * Used by other services to create notifications via POST /internal/notifications.
 * Requires X-Internal-Token authentication.
 */
public class CreateNotificationRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    private UUID groupId;  // Nullable

    @NotNull(message = "type is required")
    private NotificationType type;

    @NotNull(message = "message is required")
    @Size(min = 1, max = 500, message = "message must be between 1 and 500 characters")
    private String message;

    private Map<String, Object> metadata;  // Nullable

    public CreateNotificationRequest() {
    }

    public CreateNotificationRequest(UUID userId, UUID groupId, NotificationType type, String message, Map<String, Object> metadata) {
        this.userId = userId;
        this.groupId = groupId;
        this.type = type;
        this.message = message;
        this.metadata = metadata;
    }

    // Getters and Setters

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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
