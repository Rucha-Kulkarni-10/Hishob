package com.hishob.balance.dto;

import java.util.List;
import java.util.UUID;

public class PersonalDebtSimplificationResponse {

    private UUID userId;
    private List<PersonalDebtSimplificationSuggestion> suggestions;

    public PersonalDebtSimplificationResponse(UUID userId, List<PersonalDebtSimplificationSuggestion> suggestions) {
        this.userId = userId;
        this.suggestions = suggestions;
    }

    public UUID getUserId() {
        return userId;
    }

    public List<PersonalDebtSimplificationSuggestion> getSuggestions() {
        return suggestions;
    }
}
