package com.hishob.settlement.dto;

import java.util.List;
import java.util.UUID;

public class PersonalDebtSimplificationResponse {

    private UUID userId;
    private List<PersonalDebtSimplificationSuggestion> suggestions;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<PersonalDebtSimplificationSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<PersonalDebtSimplificationSuggestion> suggestions) {
        this.suggestions = suggestions;
    }
}
