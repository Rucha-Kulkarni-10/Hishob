package com.hishob.auth.dto;

import com.hishob.auth.entity.User;

import java.util.UUID;

/**
 * Search result shown before a friendship exists, so it must never carry contact details.
 */
public class UserSearchResponse {

    private UUID userId;
    private String name;

    public UserSearchResponse() {
    }

    public UserSearchResponse(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public static UserSearchResponse fromEntity(User user) {
        return new UserSearchResponse(user.getId(), user.getName());
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
}
