package com.hishob.balance.principal;

import java.util.UUID;

public class AuthenticatedUser {

    private final UUID userId;
    private final String email;

    public AuthenticatedUser(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

}
