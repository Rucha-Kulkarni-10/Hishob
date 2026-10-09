package com.hishob.expense.controller;

import com.hishob.expense.dto.CreateExpenseCommentRequest;
import com.hishob.expense.dto.ExpenseCommentResponse;
import com.hishob.expense.dto.UpdateExpenseCommentRequest;
import com.hishob.expense.principal.AuthenticatedUser;
import com.hishob.expense.service.ExpenseCommentService;
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
public class ExpenseCommentController {

    private final ExpenseCommentService expenseCommentService;

    public ExpenseCommentController(ExpenseCommentService expenseCommentService) {
        this.expenseCommentService = expenseCommentService;
    }

    @GetMapping("/api/expenses/{expenseId}/comments")
    public ResponseEntity<List<ExpenseCommentResponse>> listComments(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<ExpenseCommentResponse> comments = expenseCommentService.listComments(expenseId, currentUser.getUserId());
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/api/expenses/{expenseId}/comments")
    public ResponseEntity<ExpenseCommentResponse> createComment(
            @PathVariable UUID expenseId,
            @Valid @RequestBody CreateExpenseCommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseCommentResponse response = expenseCommentService.createComment(expenseId, request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/expenses/{expenseId}/comments/{commentId}")
    public ResponseEntity<ExpenseCommentResponse> updateComment(
            @PathVariable UUID expenseId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateExpenseCommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ExpenseCommentResponse response = expenseCommentService.updateComment(expenseId, commentId, request, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/expenses/{expenseId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID expenseId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        expenseCommentService.deleteComment(expenseId, commentId, currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
