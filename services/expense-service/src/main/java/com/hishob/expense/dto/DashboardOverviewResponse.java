package com.hishob.expense.dto;

import java.math.BigDecimal;

/**
 * Combined overview of a single user's expense activity across the two separate
 * systems (group and personal). Group and personal scopes are disjoint by design
 * (a group expense has a non-null groupId; a personal expense has a null groupId),
 * so the combined scope counts every expense exactly once.
 *
 * Metric definitions — deliberately kept distinct, never used interchangeably:
 *   recordedSpend — full totalAmount of every expense visible in the scope
 *                   (for a group this is the whole group expense, not the user's part).
 *   yourShare     — the portion attributed to the user by split rules; membership
 *                   alone contributes nothing.
 *   youPaid       — totalAmount of expenses the user fronted (paidBy = user).
 */
public class DashboardOverviewResponse {

    private ScopeTotals group;
    private ScopeTotals personal;
    private ScopeTotals combined;

    public DashboardOverviewResponse() {
    }

    public DashboardOverviewResponse(ScopeTotals group, ScopeTotals personal, ScopeTotals combined) {
        this.group = group;
        this.personal = personal;
        this.combined = combined;
    }

    public ScopeTotals getGroup() {
        return group;
    }

    public void setGroup(ScopeTotals group) {
        this.group = group;
    }

    public ScopeTotals getPersonal() {
        return personal;
    }

    public void setPersonal(ScopeTotals personal) {
        this.personal = personal;
    }

    public ScopeTotals getCombined() {
        return combined;
    }

    public void setCombined(ScopeTotals combined) {
        this.combined = combined;
    }

    public static class ScopeTotals {

        private long expenseCount;
        private BigDecimal recordedSpend;
        private BigDecimal yourShare;
        private BigDecimal youPaid;

        public ScopeTotals() {
        }

        public ScopeTotals(long expenseCount, BigDecimal recordedSpend, BigDecimal yourShare, BigDecimal youPaid) {
            this.expenseCount = expenseCount;
            this.recordedSpend = recordedSpend;
            this.yourShare = yourShare;
            this.youPaid = youPaid;
        }

        public long getExpenseCount() {
            return expenseCount;
        }

        public void setExpenseCount(long expenseCount) {
            this.expenseCount = expenseCount;
        }

        public BigDecimal getRecordedSpend() {
            return recordedSpend;
        }

        public void setRecordedSpend(BigDecimal recordedSpend) {
            this.recordedSpend = recordedSpend;
        }

        public BigDecimal getYourShare() {
            return yourShare;
        }

        public void setYourShare(BigDecimal yourShare) {
            this.yourShare = yourShare;
        }

        public BigDecimal getYouPaid() {
            return youPaid;
        }

        public void setYouPaid(BigDecimal youPaid) {
            this.youPaid = youPaid;
        }
    }
}
