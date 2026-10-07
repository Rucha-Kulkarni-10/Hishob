package com.hishob.auth.dto;

import com.hishob.auth.entity.User;

import java.util.UUID;

public class UserSearchResponse {

    private UUID userId;
    private String name;
    private String email;

    public UserSearchResponse() {
    }

    public UserSearchResponse(UUID userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public static UserSearchResponse fromEntity(User user) {
        return new UserSearchResponse(user.getId(), user.getName(), user.getEmail());
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
}
