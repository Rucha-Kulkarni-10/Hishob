package com.hishob.balance.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO received from Settlement Service internal endpoint:
 * GET /internal/groups/{groupId}/settlements/completed
 *
 * Only contains fields needed for balance adjustment.
 * fromUserId = the payer (debtor who sent money).
 * toUserId   = the receiver (creditor who received money).
 * amount     = amount paid (always positive, COMPLETED status only).
 */
public class CompletedSettlementData {

    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;

    public CompletedSettlementData() {
    }

    public UUID getFromUserId() { return fromUserId; }
    public void setFromUserId(UUID fromUserId) { this.fromUserId = fromUserId; }

    public UUID getToUserId() { return toUserId; }
    public void setToUserId(UUID toUserId) { this.toUserId = toUserId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

}
