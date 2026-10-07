package com.hishob.expense.controller;

import com.hishob.expense.dto.CreateExpenseRequest;
import com.hishob.expense.dto.CreatePersonalExpenseRequest;
import com.hishob.expense.dto.ExpenseResponse;
import com.hishob.expense.dto.UpdatePersonalExpenseRequest;
import com.hishob.expense.principal.AuthenticatedUser;
import com.hishob.expense.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/api/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(
            @Valid @RequestBody CreateExpenseRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseResponse response = expenseService.createExpense(request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/personal-expenses")
    public ResponseEntity<ExpenseResponse> createPersonalExpense(
            @Valid @RequestBody CreatePersonalExpenseRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseResponse response = expenseService.createPersonalExpense(request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/groups/{groupId}/expenses")
    public ResponseEntity<List<ExpenseResponse>> listGroupExpenses(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<ExpenseResponse> response = expenseService.listGroupExpenses(groupId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/personal-expenses")
    public ResponseEntity<List<ExpenseResponse>> listPersonalExpenses(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<ExpenseResponse> response = expenseService.listPersonalExpenses(currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/friends/{friendUserId}/expenses")
    public ResponseEntity<List<ExpenseResponse>> listFriendPersonalExpenses(
            @PathVariable UUID friendUserId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<ExpenseResponse> response = expenseService.listFriendPersonalExpenses(friendUserId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpenseDetail(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseResponse response = expenseService.getExpenseDetail(expenseId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/personal-expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> getPersonalExpenseDetail(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseResponse response = expenseService.getPersonalExpenseDetail(expenseId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable UUID expenseId,
            @Valid @RequestBody CreateExpenseRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseResponse response = expenseService.updateExpense(expenseId, request, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/personal-expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> updatePersonalExpense(
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdatePersonalExpenseRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseResponse response = expenseService.updatePersonalExpense(expenseId, request, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        expenseService.deleteExpense(expenseId, currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/personal-expenses/{expenseId}")
    public ResponseEntity<Void> deletePersonalExpense(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        expenseService.deletePersonalExpense(expenseId, currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
