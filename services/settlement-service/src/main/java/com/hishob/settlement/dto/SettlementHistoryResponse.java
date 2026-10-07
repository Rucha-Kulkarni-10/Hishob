package com.hishob.settlement.dto;

import com.hishob.settlement.entity.SettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SettlementHistoryResponse {

    private UUID id;
    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;
    private SettlementStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    public SettlementHistoryResponse() {
    }

    public SettlementHistoryResponse(UUID id, UUID fromUserId, UUID toUserId,
                                     BigDecimal amount, SettlementStatus status,
                                     Instant createdAt, Instant updatedAt) {
        this(id, fromUserId, toUserId, amount, status, null, createdAt, updatedAt);
    }

    public SettlementHistoryResponse(UUID id, UUID fromUserId, UUID toUserId,
                                     BigDecimal amount, SettlementStatus status,
                                     String notes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFromUserId() { return fromUserId; }
    public void setFromUserId(UUID fromUserId) { this.fromUserId = fromUserId; }

    public UUID getToUserId() { return toUserId; }
    public void setToUserId(UUID toUserId) { this.toUserId = toUserId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
