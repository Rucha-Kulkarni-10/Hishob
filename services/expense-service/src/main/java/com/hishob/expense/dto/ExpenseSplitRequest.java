package com.hishob.expense.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class ExpenseSplitRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @DecimalMin(value = "0.01", inclusive = false, message = "Amount must be greater than 0")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Percentage must be at least 0")
    @DecimalMax(value = "100.00", message = "Percentage must be at most 100")
    private BigDecimal percentage;

    public ExpenseSplitRequest() {
    }

    public ExpenseSplitRequest(UUID userId, BigDecimal amount, BigDecimal percentage) {
        this.userId = userId;
        this.amount = amount;
        this.percentage = percentage;
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

}
