package com.hishob.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal DTO used by Balance Service to fetch completed settlements for a group.
 * Only exposes the fields required for balance adjustment.
 * Does not expose internal settlement IDs, timestamps, or status (always COMPLETED by contract).
 */
public class InternalCompletedSettlementData {

    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;

    public InternalCompletedSettlementData() {
    }

    public InternalCompletedSettlementData(UUID fromUserId, UUID toUserId, BigDecimal amount) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    public UUID getFromUserId() { return fromUserId; }
    public void setFromUserId(UUID fromUserId) { this.fromUserId = fromUserId; }

    public UUID getToUserId() { return toUserId; }
    public void setToUserId(UUID toUserId) { this.toUserId = toUserId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

}
