package com.hishob.auth.dto;

import com.hishob.auth.entity.User;

import java.util.UUID;

public class UserSummaryResponse {

    private UUID id;
    private String name;
    private String email;

    public UserSummaryResponse() {
    }

    public UserSummaryResponse(UUID id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public static UserSummaryResponse fromEntity(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
