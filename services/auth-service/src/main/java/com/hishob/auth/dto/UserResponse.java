package com.hishob.auth.dto;

import com.hishob.auth.entity.User;

import java.time.Instant;
import java.util.UUID;

public class UserResponse {

    private UUID id;
    private String name;
    private String email;
    private Instant createdAt;
    private boolean emailVerified;

    public UserResponse() {
    }

    public UserResponse(UUID id, String name, String email, Instant createdAt) {
        this(id, name, email, createdAt, false);
    }

    public UserResponse(UUID id, String name, String email, Instant createdAt, boolean emailVerified) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
        this.emailVerified = emailVerified;
    }

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.isEmailVerified()
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

}
