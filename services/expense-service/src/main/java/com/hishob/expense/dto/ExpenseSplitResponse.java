package com.hishob.expense.dto;

import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;

import java.math.BigDecimal;
import java.util.UUID;

public class ExpenseSplitResponse {

    private UUID userId;
    private BigDecimal amount;
    private BigDecimal percentage;
    private SplitType splitType;

    public ExpenseSplitResponse() {
    }

    public ExpenseSplitResponse(UUID userId, BigDecimal amount, BigDecimal percentage, SplitType splitType) {
        this.userId = userId;
        this.amount = amount;
        this.percentage = percentage;
        this.splitType = splitType;
    }

    public static ExpenseSplitResponse fromEntity(ExpenseSplit s) {
        return new ExpenseSplitResponse(
                s.getUserId(),
                s.getAmount(),
                s.getPercentage(),
                s.getSplitType()
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

}
