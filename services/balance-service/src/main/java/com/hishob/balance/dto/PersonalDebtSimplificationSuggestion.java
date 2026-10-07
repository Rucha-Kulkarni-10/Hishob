package com.hishob.balance.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PersonalDebtSimplificationSuggestion {

    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;

    public PersonalDebtSimplificationSuggestion(UUID fromUserId, UUID toUserId, BigDecimal amount) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
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
