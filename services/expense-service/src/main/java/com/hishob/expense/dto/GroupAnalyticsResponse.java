package com.hishob.expense.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupAnalyticsResponse {

    private UUID groupId;
    private BigDecimal totalSpending;
    private long expenseCount;
    private BigDecimal averageExpense;
    private BigDecimal totalSettledVolume;
    private List<MemberSpendingSummary> memberSpending;
    private List<MonthlySpendingSummary> monthlyTrends;
    private List<CategorySpendingSummary> categorySpending = new ArrayList<>();

    public GroupAnalyticsResponse() {
    }

    public GroupAnalyticsResponse(UUID groupId, BigDecimal totalSpending, long expenseCount,
                                  BigDecimal averageExpense, BigDecimal totalSettledVolume,
                                  List<MemberSpendingSummary> memberSpending,
                                  List<MonthlySpendingSummary> monthlyTrends) {
        this(groupId, totalSpending, expenseCount, averageExpense, totalSettledVolume, memberSpending, monthlyTrends, new ArrayList<>());
    }

    public GroupAnalyticsResponse(UUID groupId, BigDecimal totalSpending, long expenseCount,
                                  BigDecimal averageExpense, BigDecimal totalSettledVolume,
                                  List<MemberSpendingSummary> memberSpending,
                                  List<MonthlySpendingSummary> monthlyTrends,
                                  List<CategorySpendingSummary> categorySpending) {
        this.groupId = groupId;
        this.totalSpending = totalSpending;
        this.expenseCount = expenseCount;
        this.averageExpense = averageExpense;
        this.totalSettledVolume = totalSettledVolume;
        this.memberSpending = memberSpending;
        this.monthlyTrends = monthlyTrends;
        this.categorySpending = categorySpending != null ? categorySpending : new ArrayList<>();
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
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

    public BigDecimal getTotalSettledVolume() {
        return totalSettledVolume;
    }

    public void setTotalSettledVolume(BigDecimal totalSettledVolume) {
        this.totalSettledVolume = totalSettledVolume;
    }

    public List<MemberSpendingSummary> getMemberSpending() {
        return memberSpending;
    }

    public void setMemberSpending(List<MemberSpendingSummary> memberSpending) {
        this.memberSpending = memberSpending;
    }

    public List<MonthlySpendingSummary> getMonthlyTrends() {
        return monthlyTrends;
    }

    public void setMonthlyTrends(List<MonthlySpendingSummary> monthlyTrends) {
        this.monthlyTrends = monthlyTrends;
    }

    public List<CategorySpendingSummary> getCategorySpending() {
        return categorySpending;
    }

    public void setCategorySpending(List<CategorySpendingSummary> categorySpending) {
        this.categorySpending = categorySpending;
    }
}

