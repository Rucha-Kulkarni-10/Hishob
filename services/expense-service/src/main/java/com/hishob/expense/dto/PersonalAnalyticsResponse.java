package com.hishob.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public class PersonalAnalyticsResponse {

    private BigDecimal totalSpending;
    private long expenseCount;
    private BigDecimal averageExpense;
    private BigDecimal totalPaidByUser;
    private BigDecimal totalUserShare;
    private List<CategorySpendingSummary> categorySpending;
    private List<FriendSpendingSummary> friendSpending;
    private List<MonthlySpendingSummary> monthlyTrends;

    public PersonalAnalyticsResponse() {
    }

    public PersonalAnalyticsResponse(
            BigDecimal totalSpending,
            long expenseCount,
            BigDecimal averageExpense,
            BigDecimal totalPaidByUser,
            BigDecimal totalUserShare,
            List<CategorySpendingSummary> categorySpending,
            List<FriendSpendingSummary> friendSpending,
            List<MonthlySpendingSummary> monthlyTrends) {
        this.totalSpending = totalSpending;
        this.expenseCount = expenseCount;
        this.averageExpense = averageExpense;
        this.totalPaidByUser = totalPaidByUser;
        this.totalUserShare = totalUserShare;
        this.categorySpending = categorySpending;
        this.friendSpending = friendSpending;
        this.monthlyTrends = monthlyTrends;
    }

    public BigDecimal getTotalSpending() {
        return totalSpending;
    }

    public void setTotalSpending(BigDecimal totalSpending) {
        this.totalSpending = totalSpending;
    }

    public long getExpenseCount() {
        return expenseCount;
    }

    public void setExpenseCount(long expenseCount) {
        this.expenseCount = expenseCount;
    }

    public BigDecimal getAverageExpense() {
        return averageExpense;
    }

    public void setAverageExpense(BigDecimal averageExpense) {
        this.averageExpense = averageExpense;
    }

    public BigDecimal getTotalPaidByUser() {
        return totalPaidByUser;
    }

    public void setTotalPaidByUser(BigDecimal totalPaidByUser) {
        this.totalPaidByUser = totalPaidByUser;
    }

    public BigDecimal getTotalUserShare() {
        return totalUserShare;
    }

    public void setTotalUserShare(BigDecimal totalUserShare) {
        this.totalUserShare = totalUserShare;
    }

    public List<CategorySpendingSummary> getCategorySpending() {
        return categorySpending;
    }

    public void setCategorySpending(List<CategorySpendingSummary> categorySpending) {
        this.categorySpending = categorySpending;
    }

    public List<FriendSpendingSummary> getFriendSpending() {
        return friendSpending;
    }

    public void setFriendSpending(List<FriendSpendingSummary> friendSpending) {
        this.friendSpending = friendSpending;
    }

    public List<MonthlySpendingSummary> getMonthlyTrends() {
        return monthlyTrends;
    }

    public void setMonthlyTrends(List<MonthlySpendingSummary> monthlyTrends) {
        this.monthlyTrends = monthlyTrends;
    }
}
