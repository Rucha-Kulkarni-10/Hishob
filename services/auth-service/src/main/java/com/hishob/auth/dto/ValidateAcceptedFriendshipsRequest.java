package com.hishob.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class ValidateAcceptedFriendshipsRequest {

    @NotNull(message = "currentUserId is required")
    private UUID currentUserId;

    @NotNull(message = "userIds is required")
    private List<UUID> userIds;

    public ValidateAcceptedFriendshipsRequest() {
    }

    public ValidateAcceptedFriendshipsRequest(UUID currentUserId, List<UUID> userIds) {
        this.currentUserId = currentUserId;
        this.userIds = userIds;
    }

    public UUID getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(UUID currentUserId) {
        this.currentUserId = currentUserId;
    }

    public List<UUID> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<UUID> userIds) {
        this.userIds = userIds;
    }
}
