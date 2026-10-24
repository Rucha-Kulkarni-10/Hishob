package com.hishob.auth.dto;

import com.hishob.auth.entity.User;

import java.util.UUID;

/**
 * Identity projection for cross-service name resolution. Deliberately excludes contact
 * details: callers need a display name for a known user ID, not their address.
 */
public class UserSummaryResponse {

    private UUID id;
    private String name;

    public UserSummaryResponse() {
    }

    public UserSummaryResponse(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public static UserSummaryResponse fromEntity(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName()
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

}
