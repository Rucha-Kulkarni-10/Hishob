package com.hishob.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PersonalDebtSimplificationSuggestion {

    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;

    public PersonalDebtSimplificationSuggestion() {
    }

    public PersonalDebtSimplificationSuggestion(UUID fromUserId, UUID toUserId, BigDecimal amount) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
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
