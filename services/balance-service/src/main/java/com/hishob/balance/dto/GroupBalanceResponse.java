package com.hishob.balance.dto;

import java.util.List;
import java.util.UUID;

public class GroupBalanceResponse {

    private UUID groupId;
    private List<UserBalanceResponse> balances;

    public GroupBalanceResponse(UUID groupId, List<UserBalanceResponse> balances) {
        this.groupId = groupId;
        this.balances = balances;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public List<UserBalanceResponse> getBalances() {
        return balances;
    }

}
