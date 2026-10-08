package com.hishob.expense.dto;

import com.hishob.expense.entity.ExpenseCategory;

import java.math.BigDecimal;

public class CategorySpendingSummary {

    private ExpenseCategory category;
    private BigDecimal totalAmount;
    private long expenseCount;
    private double percentageOfTotal;

    public CategorySpendingSummary() {
    }

    public CategorySpendingSummary(ExpenseCategory category, BigDecimal totalAmount,
                                   long expenseCount, double percentageOfTotal) {
        this.category = category;
        this.totalAmount = totalAmount;
        this.expenseCount = expenseCount;
        this.percentageOfTotal = percentageOfTotal;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
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

    public double getPercentageOfTotal() {
        return percentageOfTotal;
    }

    public void setPercentageOfTotal(double percentageOfTotal) {
        this.percentageOfTotal = percentageOfTotal;
    }
}
