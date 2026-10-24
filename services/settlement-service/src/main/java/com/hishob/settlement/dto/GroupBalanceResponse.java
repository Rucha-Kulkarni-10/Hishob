package com.hishob.settlement.dto;

import java.util.List;
import java.util.UUID;

public class GroupBalanceResponse {

    private UUID groupId;
    private List<UserBalanceResponse> balances;

    public GroupBalanceResponse() {
    }

    public GroupBalanceResponse(UUID groupId, List<UserBalanceResponse> balances) {
        this.groupId = groupId;
        this.balances = balances;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public List<UserBalanceResponse> getBalances() {
        return balances;
    }

    public void setBalances(List<UserBalanceResponse> balances) {
        this.balances = balances;
    }

}
