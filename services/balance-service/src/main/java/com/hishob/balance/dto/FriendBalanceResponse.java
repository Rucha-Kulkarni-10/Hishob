package com.hishob.balance.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class FriendBalanceResponse {

    private UUID currentUserId;
    private UUID friendUserId;
    private BigDecimal currentUserNetBalance;
    private BigDecimal friendNetBalance;
    private BalanceStatus currentUserStatus;
    private BalanceStatus friendStatus;
    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;

    public FriendBalanceResponse(UUID currentUserId, UUID friendUserId,
                                 BigDecimal currentUserNetBalance, BigDecimal friendNetBalance,
                                 BalanceStatus currentUserStatus, BalanceStatus friendStatus,
                                 UUID fromUserId, UUID toUserId, BigDecimal amount) {
        this.currentUserId = currentUserId;
        this.friendUserId = friendUserId;
        this.currentUserNetBalance = currentUserNetBalance;
        this.friendNetBalance = friendNetBalance;
        this.currentUserStatus = currentUserStatus;
        this.friendStatus = friendStatus;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    public UUID getCurrentUserId() {
        return currentUserId;
    }

    public UUID getFriendUserId() {
        return friendUserId;
    }

    public BigDecimal getCurrentUserNetBalance() {
        return currentUserNetBalance;
    }

    public BigDecimal getFriendNetBalance() {
        return friendNetBalance;
    }

    public BalanceStatus getCurrentUserStatus() {
        return currentUserStatus;
    }

    public BalanceStatus getFriendStatus() {
        return friendStatus;
    }

    public UUID getFromUserId() {
        return fromUserId;
    }

    public UUID getToUserId() {
        return toUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
