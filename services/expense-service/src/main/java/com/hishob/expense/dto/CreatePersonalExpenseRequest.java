package com.hishob.expense.dto;

import com.hishob.expense.entity.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class CreatePersonalExpenseRequest {

    @NotBlank(message = "Description is required")
    @Size(max = 300, message = "Description cannot exceed 300 characters")
    private String description;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Total amount must be at least 0.01")
    private BigDecimal totalAmount;

    @NotNull(message = "paidBy is required")
    private UUID paidBy;

    @NotNull(message = "splitType is required")
    private SplitType splitType;

    @NotEmpty(message = "At least one split is required")
    @Valid
    private List<ExpenseSplitRequest> splits;

    public CreatePersonalExpenseRequest() {
    }

    public CreatePersonalExpenseRequest(String description, BigDecimal totalAmount,
                                        UUID paidBy, SplitType splitType,
                                        List<ExpenseSplitRequest> splits) {
        this.description = description;
        this.totalAmount = totalAmount;
        this.paidBy = paidBy;
        this.splitType = splitType;
        this.splits = splits;
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

    public SplitType getSplitType() {
        return splitType;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

    public List<ExpenseSplitRequest> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplitRequest> splits) {
        this.splits = splits;
    }
}
