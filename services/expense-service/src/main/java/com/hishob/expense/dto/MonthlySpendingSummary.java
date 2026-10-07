package com.hishob.expense.dto;

import java.math.BigDecimal;

public class MonthlySpendingSummary {

    private String yearMonth;
    private String label;
    private BigDecimal totalAmount;
    private long expenseCount;

    public MonthlySpendingSummary() {
    }

    public MonthlySpendingSummary(String yearMonth, String label, BigDecimal totalAmount, long expenseCount) {
        this.yearMonth = yearMonth;
        this.label = label;
        this.totalAmount = totalAmount;
        this.expenseCount = expenseCount;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public long getExpenseCount() {
        return expenseCount;
    }

    public void setExpenseCount(long expenseCount) {
        this.expenseCount = expenseCount;
    }
}
