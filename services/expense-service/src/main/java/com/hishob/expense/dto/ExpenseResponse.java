package com.hishob.expense.dto;

import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ExpenseResponse {

    private UUID id;
    private UUID groupId;
    private String description;
    private BigDecimal totalAmount;
    private UUID paidBy;
    private UUID createdBy;
    private SplitType splitType;
    private ExpenseCategory category;
    private List<ExpenseSplitResponse> splits;
    private Instant createdAt;
    private Instant updatedAt;

    public ExpenseResponse() {
    }

    public ExpenseResponse(UUID id, UUID groupId, String description, BigDecimal totalAmount,
                           UUID paidBy, UUID createdBy, SplitType splitType,
                           List<ExpenseSplitResponse> splits, Instant createdAt, Instant updatedAt) {
        this(id, groupId, description, totalAmount, paidBy, createdBy, splitType, ExpenseCategory.OTHER, splits, createdAt, updatedAt);
    }

    public ExpenseResponse(UUID id, UUID groupId, String description, BigDecimal totalAmount,
                           UUID paidBy, UUID createdBy, SplitType splitType, ExpenseCategory category,
                           List<ExpenseSplitResponse> splits, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.groupId = groupId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.paidBy = paidBy;
        this.createdBy = createdBy;
        this.splitType = splitType;
        this.category = category != null ? category : ExpenseCategory.OTHER;
        this.splits = splits;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ExpenseResponse fromEntity(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getGroupId(),
                e.getDescription(),
                e.getTotalAmount(),
                e.getPaidBy(),
                e.getCreatedBy(),
                e.getSplitType(),
                e.getCategory() != null ? e.getCategory() : ExpenseCategory.OTHER,
                e.getSplits().stream()
                        .map(ExpenseSplitResponse::fromEntity)
                        .collect(Collectors.toList()),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

    public ExpenseCategory getCategory() {
        return category != null ? category : ExpenseCategory.OTHER;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category != null ? category : ExpenseCategory.OTHER;
    }

    public List<ExpenseSplitResponse> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplitResponse> splits) {
        this.splits = splits;
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

}

