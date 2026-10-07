package com.hishob.expense.dto;

import java.util.Map;
import java.util.UUID;

/**
 * CreateNotificationRequest — DTO for creating notifications via internal API.
 * Must match the Notification Service's internal API contract exactly.
 */
public class CreateNotificationRequest {

    private UUID userId;
    private UUID groupId;
    private String type;
    private String message;
    private Map<String, Object> metadata;

    public CreateNotificationRequest() {
    }

    public CreateNotificationRequest(UUID userId, UUID groupId, String type, String message, Map<String, Object> metadata) {
        this.userId = userId;
        this.groupId = groupId;
        this.type = type;
        this.message = message;
        this.metadata = metadata;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
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