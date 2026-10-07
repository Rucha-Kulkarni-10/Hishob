package com.hishob.balance.dto;

import java.util.List;
import java.util.UUID;

public class PersonalBalanceSummaryResponse {

    private UUID userId;
    private List<PersonalBalanceResponse> balances;

    public PersonalBalanceSummaryResponse(UUID userId, List<PersonalBalanceResponse> balances) {
        this.userId = userId;
        this.balances = balances;
    }

    public UUID getUserId() {
        return userId;
    }

    public List<PersonalBalanceResponse> getBalances() {
        return balances;
    }
}
