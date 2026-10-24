package com.hishob.expense.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AddMemberRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    public AddMemberRequest() {
    }

    public AddMemberRequest(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

}
