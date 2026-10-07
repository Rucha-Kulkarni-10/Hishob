package com.hishob.expense.controller;

import com.hishob.expense.dto.InternalExpenseData;
import com.hishob.expense.dto.InternalSplitData;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.repository.ExpenseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class ExpenseInternalController {

    private final ExpenseRepository expenseRepository;

    public ExpenseInternalController(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @GetMapping("/internal/groups/{groupId}/expenses")
    public ResponseEntity<List<InternalExpenseData>> getGroupExpenses(@PathVariable UUID groupId) {
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        return ResponseEntity.ok(toInternalExpenses(expenses));
    }

    @GetMapping("/internal/personal-expenses/users/{userId}")
    public ResponseEntity<List<InternalExpenseData>> getPersonalExpensesForUser(@PathVariable UUID userId) {
        List<Expense> expenses = expenseRepository.findPersonalExpensesForUser(userId);
        return ResponseEntity.ok(toInternalExpenses(expenses));
    }

    private List<InternalExpenseData> toInternalExpenses(List<Expense> expenses) {
        List<InternalExpenseData> result = expenses.stream()
                .map(e -> new InternalExpenseData(
                        e.getId(),
                        e.getGroupId(),
                        e.getTotalAmount(),
                        e.getPaidBy(),
                        e.getSplits().stream()
                                .map(s -> new InternalSplitData(s.getUserId(), s.getAmount()))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
        return result;
    }
}
