package com.hishob.expense.service;

import com.hishob.expense.dto.ExpenseSplitRequest;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.DuplicateParticipantException;
import com.hishob.expense.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class SplitCalculationService {

    public static final int MONETARY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public List<ExpenseSplit> calculateEqualSplits(BigDecimal total, List<UUID> userIds) {
        BigDecimal scaledTotal;
        if (total.scale() > MONETARY_SCALE) {
            scaledTotal = total.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        } else {
            scaledTotal = total;
        }

        int n = userIds.size();
        if (n <= 0) {
            throw new InvalidSplitException("At least one participant required");
        }

        BigDecimal base = scaledTotal.divide(BigDecimal.valueOf(n), MONETARY_SCALE, RoundingMode.DOWN);
        BigDecimal allocated = base.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = scaledTotal.subtract(allocated);
        int remainderCents = remainder.scaleByPowerOfTen(MONETARY_SCALE).intValueExact();

        List<ExpenseSplit> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal amount = base;
            if (i < remainderCents) {
                amount = amount.add(new BigDecimal("0.01"));
            }
            result.add(new ExpenseSplit(userIds.get(i), amount, SplitType.EQUAL, null));
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseSplit split : result) {
            sum = sum.add(split.getAmount());
        }
        if (sum.compareTo(scaledTotal) != 0) {
            throw new InvalidSplitException("Split total does not match expense total");
        }

        return result;
    }

    public List<ExpenseSplit> validateAndBuildExactSplits(BigDecimal total, List<ExpenseSplitRequest> requests) {
        if (requests.isEmpty()) {
            throw new InvalidSplitException("Splits cannot be empty");
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseSplitRequest req : requests) {
            if (req.getAmount() == null) {
                throw new InvalidSplitException("Exact split amount is required for user " + req.getUserId());
            }
            if (req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSplitException("Exact split amount must be positive for user " + req.getUserId());
            }
            sum = sum.add(req.getAmount());
        }

        if (sum.compareTo(total) != 0) {
            throw new InvalidSplitException("Exact split total (" + sum + ") does not match expense total (" + total + ")");
        }

        List<ExpenseSplit> result = new ArrayList<>(requests.size());
        for (ExpenseSplitRequest req : requests) {
            BigDecimal scaledAmount = req.getAmount().setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
            result.add(new ExpenseSplit(req.getUserId(), scaledAmount, SplitType.EXACT, null));
        }

        return result;
    }

    public List<ExpenseSplit> validateAndBuildPercentageSplits(BigDecimal total, List<ExpenseSplitRequest> requests) {
        if (requests.isEmpty()) {
            throw new InvalidSplitException("Splits cannot be empty");
        }

        BigDecimal pctSum = BigDecimal.ZERO;
        for (ExpenseSplitRequest req : requests) {
            if (req.getPercentage() == null) {
                throw new InvalidSplitException("Percentage is required for user " + req.getUserId());
            }
            if (req.getPercentage().compareTo(BigDecimal.ZERO) <= 0 || req.getPercentage().compareTo(ONE_HUNDRED) > 0) {
                throw new InvalidSplitException("Percentage must be between 0 (exclusive) and 100 (inclusive) for user " + req.getUserId());
            }
            pctSum = pctSum.add(req.getPercentage());
        }

        if (pctSum.compareTo(ONE_HUNDRED) != 0) {
            throw new InvalidSplitException("Percentage total (" + pctSum + ") does not equal 100%");
        }

        List<BigDecimal> baseAmounts = new ArrayList<>(requests.size());
        BigDecimal baseSum = BigDecimal.ZERO;
        for (ExpenseSplitRequest req : requests) {
            BigDecimal amount = total.multiply(req.getPercentage()).divide(ONE_HUNDRED, MONETARY_SCALE, RoundingMode.HALF_UP);
            baseAmounts.add(amount);
            baseSum = baseSum.add(amount);
        }

        BigDecimal remainder = total.subtract(baseSum);
        List<ExpenseSplit> result = new ArrayList<>(requests.size());
        BigDecimal finalSum = BigDecimal.ZERO;
        int adjustmentCents = Math.abs(remainder.scaleByPowerOfTen(MONETARY_SCALE).intValueExact());
        int adjustmentDirection = remainder.signum();
        for (int i = 0; i < requests.size(); i++) {
            ExpenseSplitRequest req = requests.get(i);
            BigDecimal finalAmount = baseAmounts.get(i);
            if (i < adjustmentCents) {
                if (adjustmentDirection > 0) {
                    finalAmount = finalAmount.add(new BigDecimal("0.01"));
                } else if (adjustmentDirection < 0) {
                    finalAmount = finalAmount.subtract(new BigDecimal("0.01"));
                    if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new InvalidSplitException("Percentage split produced a non-positive amount for user " + req.getUserId());
                    }
                }
            }
            finalSum = finalSum.add(finalAmount);
            result.add(new ExpenseSplit(req.getUserId(), finalAmount, SplitType.PERCENTAGE, req.getPercentage()));
        }

        if (finalSum.compareTo(total) != 0) {
            throw new InvalidSplitException("Split total does not match expense total");
        }

        return result;
    }

    public void validateNoDuplicateUsers(List<UUID> userIds) {
        Set<UUID> distinct = new HashSet<>(userIds);
        if (distinct.size() != userIds.size()) {
            throw new DuplicateParticipantException("Expense contains duplicate participants");
        }
    }
}
