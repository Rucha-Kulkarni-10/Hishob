package com.hishob.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class SendFriendRequest {

    @NotNull(message = "recipientUserId is required")
    private UUID recipientUserId;

    public SendFriendRequest() {
    }

    public SendFriendRequest(UUID recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(UUID recipientUserId) {
        this.recipientUserId = recipientUserId;
    }
}
