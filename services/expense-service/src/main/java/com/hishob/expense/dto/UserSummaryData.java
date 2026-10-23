package com.hishob.expense.dto;

import java.util.UUID;

/** Projection of Auth Service's UserSummaryResponse — display name only, never contact details. */
public class UserSummaryData {

    private UUID id;
    private String name;

    public UserSummaryData() {
    }

    public UserSummaryData(UUID id, String name) {
        this.id = id;
        this.name = name;
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
