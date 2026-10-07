package com.hishob.auth.dto;

import java.time.Instant;
import java.util.UUID;

public class FriendRequestResponse {

    private UUID requestId;
    private UUID userId;
    private String name;
    private String email;
    private Instant createdAt;

    public FriendRequestResponse() {
    }

    public FriendRequestResponse(UUID requestId, UUID userId, String name, String email, Instant createdAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
