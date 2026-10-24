package com.hishob.settlement.dto;

import java.util.List;
import java.util.UUID;

public class PersonalBalanceSummaryResponse {

    private UUID userId;
    private List<PersonalBalanceResponse> balances;

    public PersonalBalanceSummaryResponse() {
    }

    public PersonalBalanceSummaryResponse(UUID userId, List<PersonalBalanceResponse> balances) {
        this.userId = userId;
        this.balances = balances;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<PersonalBalanceResponse> getBalances() {
        return balances;
    }

    public void setBalances(List<PersonalBalanceResponse> balances) {
        this.balances = balances;
    }
}
