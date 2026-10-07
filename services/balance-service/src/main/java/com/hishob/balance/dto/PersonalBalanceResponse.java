package com.hishob.balance.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PersonalBalanceResponse {

    private UUID userId;
    private BigDecimal totalPaid;
    private BigDecimal totalShare;
    private BigDecimal netBalance;
    private BalanceStatus status;

    public PersonalBalanceResponse(UUID userId, BigDecimal totalPaid, BigDecimal totalShare,
                                   BigDecimal netBalance, BalanceStatus status) {
        this.userId = userId;
        this.totalPaid = totalPaid;
        this.totalShare = totalShare;
        this.netBalance = netBalance;
        this.status = status;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public BigDecimal getTotalShare() {
        return totalShare;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public BalanceStatus getStatus() {
        return status;
    }
}
