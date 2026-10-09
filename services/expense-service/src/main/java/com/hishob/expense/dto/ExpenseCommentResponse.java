package com.hishob.expense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hishob.expense.entity.ExpenseComment;

import java.time.Instant;
import java.util.UUID;

public class ExpenseCommentResponse {

    private UUID id;
    private UUID expenseId;
    private UUID authorUserId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    @JsonProperty("isEdited")
    private boolean isEdited;

    public ExpenseCommentResponse() {
    }

    public ExpenseCommentResponse(UUID id, UUID expenseId, UUID authorUserId,
                                  String content, Instant createdAt, Instant updatedAt,
                                  boolean isEdited) {
        this.id = id;
        this.expenseId = expenseId;
        this.authorUserId = authorUserId;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isEdited = isEdited;
    }

    public static ExpenseCommentResponse fromEntity(ExpenseComment comment) {
        boolean edited = comment.getUpdatedAt() != null
                && comment.getCreatedAt() != null
                && comment.getUpdatedAt().isAfter(comment.getCreatedAt());

        return new ExpenseCommentResponse(
                comment.getId(),
                comment.getExpense().getId(),
                comment.getAuthorUserId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                edited
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(UUID expenseId) {
        this.expenseId = expenseId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(UUID authorUserId) {
        this.authorUserId = authorUserId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public boolean getIsEdited() {
        return isEdited;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }
}
