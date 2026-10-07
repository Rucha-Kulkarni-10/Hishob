package com.hishob.balance.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class InternalSplitData {

    private UUID userId;
    private BigDecimal amount;

    public InternalSplitData() {
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

}
