package com.hishob.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class FriendBalanceResponse {

    private UUID currentUserId;
    private UUID friendUserId;
    private BigDecimal currentUserNetBalance;
    private BigDecimal friendNetBalance;
    private String currentUserStatus;
    private String friendStatus;
    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;

    public FriendBalanceResponse() {
    }

    public FriendBalanceResponse(UUID currentUserId, UUID friendUserId,
                                 BigDecimal currentUserNetBalance, BigDecimal friendNetBalance,
                                 String currentUserStatus, String friendStatus,
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

    public FriendBalanceResponse(UUID currentUserId, UUID friendUserId,
                                 BigDecimal currentUserNetBalance, BigDecimal friendNetBalance,
                                 BalanceStatus currentUserStatus, BalanceStatus friendStatus,
                                 UUID fromUserId, UUID toUserId, BigDecimal amount) {
        this(currentUserId, friendUserId, currentUserNetBalance, friendNetBalance,
                currentUserStatus != null ? currentUserStatus.name() : null,
                friendStatus != null ? friendStatus.name() : null,
                fromUserId, toUserId, amount);
    }

    public UUID getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(UUID currentUserId) {
        this.currentUserId = currentUserId;
    }

    public UUID getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(UUID friendUserId) {
        this.friendUserId = friendUserId;
    }

    public BigDecimal getCurrentUserNetBalance() {
        return currentUserNetBalance;
    }

    public void setCurrentUserNetBalance(BigDecimal currentUserNetBalance) {
        this.currentUserNetBalance = currentUserNetBalance;
    }

    public BigDecimal getFriendNetBalance() {
        return friendNetBalance;
    }

    public void setFriendNetBalance(BigDecimal friendNetBalance) {
        this.friendNetBalance = friendNetBalance;
    }

    public String getCurrentUserStatus() {
        return currentUserStatus;
    }

    public void setCurrentUserStatus(String currentUserStatus) {
        this.currentUserStatus = currentUserStatus;
    }

    public String getFriendStatus() {
        return friendStatus;
    }

    public void setFriendStatus(String friendStatus) {
        this.friendStatus = friendStatus;
    }

    public UUID getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(UUID fromUserId) {
        this.fromUserId = fromUserId;
    }

    public UUID getToUserId() {
        return toUserId;
    }

    public void setToUserId(UUID toUserId) {
        this.toUserId = toUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
