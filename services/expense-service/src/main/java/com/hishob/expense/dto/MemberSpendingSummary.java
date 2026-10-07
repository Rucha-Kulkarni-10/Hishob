package com.hishob.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class MemberSpendingSummary {

    private UUID userId;
    private BigDecimal totalPaid;
    private BigDecimal totalShare;
    private BigDecimal netPosition;
    private String status;

    public MemberSpendingSummary() {
    }

    public MemberSpendingSummary(UUID userId, BigDecimal totalPaid, BigDecimal totalShare,
                                 BigDecimal netPosition, String status) {
        this.userId = userId;
        this.totalPaid = totalPaid;
        this.totalShare = totalShare;
        this.netPosition = netPosition;
        this.status = status;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public BigDecimal getTotalShare() {
        return totalShare;
    }

    public void setTotalShare(BigDecimal totalShare) {
        this.totalShare = totalShare;
    }

    public BigDecimal getNetPosition() {
        return netPosition;
    }

    public void setNetPosition(BigDecimal netPosition) {
        this.netPosition = netPosition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
