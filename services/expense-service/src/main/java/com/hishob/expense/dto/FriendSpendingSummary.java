package com.hishob.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class FriendSpendingSummary {

    private UUID friendUserId;
    private String friendName;
    private BigDecimal totalAmount;
    private long expenseCount;
    private BigDecimal userShare;
    private BigDecimal friendShare;

    public FriendSpendingSummary() {
    }

    public FriendSpendingSummary(
            UUID friendUserId,
            String friendName,
            BigDecimal totalAmount,
            long expenseCount,
            BigDecimal userShare,
            BigDecimal friendShare) {
        this.friendUserId = friendUserId;
        this.friendName = friendName;
        this.totalAmount = totalAmount;
        this.expenseCount = expenseCount;
        this.userShare = userShare;
        this.friendShare = friendShare;
    }

    public UUID getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(UUID friendUserId) {
        this.friendUserId = friendUserId;
    }

    public String getFriendName() {
        return friendName;
    }

    public void setFriendName(String friendName) {
        this.friendName = friendName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public long getExpenseCount() {
        return expenseCount;
    }

    public void setExpenseCount(long expenseCount) {
        this.expenseCount = expenseCount;
    }

    public BigDecimal getUserShare() {
        return userShare;
    }

    public void setUserShare(BigDecimal userShare) {
        this.userShare = userShare;
    }

    public BigDecimal getFriendShare() {
        return friendShare;
    }

    public void setFriendShare(BigDecimal friendShare) {
        this.friendShare = friendShare;
    }
}
