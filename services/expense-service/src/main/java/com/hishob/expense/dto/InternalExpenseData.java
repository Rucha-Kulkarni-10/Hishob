package com.hishob.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class InternalExpenseData {

    private UUID expenseId;
    private UUID groupId;
    private BigDecimal totalAmount;
    private UUID paidBy;
    private List<InternalSplitData> splits;

    public InternalExpenseData() {
    }

    public InternalExpenseData(UUID expenseId, UUID groupId, BigDecimal totalAmount,
                               UUID paidBy, List<InternalSplitData> splits) {
        this.expenseId = expenseId;
        this.groupId = groupId;
        this.totalAmount = totalAmount;
        this.paidBy = paidBy;
        this.splits = splits;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(UUID expenseId) {
        this.expenseId = expenseId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
    }

    public List<InternalSplitData> getSplits() {
        return splits;
    }

    public void setSplits(List<InternalSplitData> splits) {
        this.splits = splits;
    }
}
