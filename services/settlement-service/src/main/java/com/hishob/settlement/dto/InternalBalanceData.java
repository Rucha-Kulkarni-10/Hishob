package com.hishob.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class InternalBalanceData {

    private UUID userId;
    private BigDecimal netBalance;

    public InternalBalanceData() {
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getNetBalance() { return netBalance; }
    public void setNetBalance(BigDecimal netBalance) { this.netBalance = netBalance; }

}
