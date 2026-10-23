package com.hishob.expense.service;

import com.hishob.expense.dto.DashboardOverviewResponse;
import com.hishob.expense.dto.DashboardOverviewResponse.ScopeTotals;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates the two separate expense systems into one read-only overview for the
 * user dashboard. Group and personal figures are computed from the same authoritative
 * scoped queries that back the individual lists, so the combined totals can neither
 * omit nor double-count an expense. Ownership and visibility of each expense are never
 * changed by this aggregation.
 */
@Service
@Transactional(readOnly = true)
public class DashboardOverviewService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;

    public DashboardOverviewService(ExpenseRepository expenseRepository, GroupService groupService) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
    }

    public DashboardOverviewResponse getOverview(UUID currentUserId) {
        List<Expense> personalExpenses = expenseRepository.findPersonalExpensesForUser(currentUserId);

        List<UUID> memberGroupIds = groupService.listGroupIdsForMember(currentUserId);
        List<Expense> groupExpenses = memberGroupIds.isEmpty()
                ? List.of()
                : expenseRepository.findByGroupIdIn(memberGroupIds);

        ScopeTotals personal = sumScope(personalExpenses, currentUserId);
        ScopeTotals group = sumScope(groupExpenses, currentUserId);
        ScopeTotals combined = addScopes(group, personal);

        return new DashboardOverviewResponse(group, personal, combined);
    }

    private ScopeTotals sumScope(List<Expense> expenses, UUID userId) {
        BigDecimal recordedSpend = BigDecimal.ZERO;
        BigDecimal yourShare = BigDecimal.ZERO;
        BigDecimal youPaid = BigDecimal.ZERO;

        for (Expense expense : expenses) {
            BigDecimal total = money(expense.getTotalAmount());
            recordedSpend = recordedSpend.add(total);
            if (userId.equals(expense.getPaidBy())) {
                youPaid = youPaid.add(total);
            }
            List<ExpenseSplit> splits = expense.getSplits();
            if (splits != null) {
                for (ExpenseSplit split : splits) {
                    if (userId.equals(split.getUserId())) {
                        yourShare = yourShare.add(money(split.getAmount()));
                    }
                }
            }
        }

        return new ScopeTotals(expenses.size(), money(recordedSpend), money(yourShare), money(youPaid));
    }

    private ScopeTotals addScopes(ScopeTotals a, ScopeTotals b) {
        return new ScopeTotals(
                a.getExpenseCount() + b.getExpenseCount(),
                money(a.getRecordedSpend().add(b.getRecordedSpend())),
                money(a.getYourShare().add(b.getYourShare())),
                money(a.getYouPaid().add(b.getYouPaid()))
        );
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
