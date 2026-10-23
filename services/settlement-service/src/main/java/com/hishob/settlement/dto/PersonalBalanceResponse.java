package com.hishob.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PersonalBalanceResponse {

    /**
     * One row of {@code GET /api/personal-balances}.
     *
     * <p>For the requesting user this is their position across the whole personal graph. For every
     * other user it is the pairwise standing between them and the requesting user only — money that
     * moves directly between those two, never that person's debts to third parties.
     */
    private UUID userId;
    private BigDecimal totalPaid;
    private BigDecimal totalShare;
    private BigDecimal netBalance;
    private BalanceStatus status;

    public PersonalBalanceResponse() {
    }

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

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public void setNetBalance(BigDecimal netBalance) {
        this.netBalance = netBalance;
    }

    public BalanceStatus getStatus() {
        return status;
    }

    public void setStatus(BalanceStatus status) {
        this.status = status;
    }
}
