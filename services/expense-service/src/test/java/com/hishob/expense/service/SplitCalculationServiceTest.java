package com.hishob.expense.service;

import com.hishob.expense.dto.ExpenseSplitRequest;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.DuplicateParticipantException;
import com.hishob.expense.exception.InvalidSplitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SplitCalculationServiceTest {

    private final SplitCalculationService svc = new SplitCalculationService();

    @Test
    @DisplayName("EQUAL: 100 / 3 => 33.34, 33.33, 33.33 with exact sum 100.00")
    void testEqual_100_dividedBy3() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        List<ExpenseSplit> result = svc.calculateEqualSplits(new BigDecimal("100"), List.of(u1, u2, u3));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("33.34");
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("33.33");
        assertThat(result.get(2).getAmount()).isEqualByComparingTo("33.33");
        BigDecimal sum = result.stream()
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("EQUAL: 1000 / 6 => sum equals 1000.00 exactly")
    void testEqual_1000_dividedBy6_sumOnly() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        UUID u5 = UUID.randomUUID();
        UUID u6 = UUID.randomUUID();

        List<ExpenseSplit> result = svc.calculateEqualSplits(new BigDecimal("1000"), List.of(u1, u2, u3, u4, u5, u6));

        BigDecimal sum = result.stream()
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("EXACT: valid 600+400 matches total 1000")
    void testExact_600_400_matches_1000() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, new BigDecimal("600"), null);
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, new BigDecimal("400"), null);

        List<ExpenseSplit> result = svc.validateAndBuildExactSplits(new BigDecimal("1000"), List.of(r1, r2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(u1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("600.00");
        assertThat(result.get(0).getSplitType()).isEqualTo(SplitType.EXACT);
        assertThat(result.get(1).getUserId()).isEqualTo(u2);
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("400.00");
        assertThat(result.get(1).getSplitType()).isEqualTo(SplitType.EXACT);
        BigDecimal sum = result.stream()
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("EXACT: 600+300 vs total 1000 throws InvalidSplitException")
    void testExact_600_300_mismatch_throws() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, new BigDecimal("600"), null);
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, new BigDecimal("300"), null);

        assertThatThrownBy(() -> svc.validateAndBuildExactSplits(new BigDecimal("1000"), List.of(r1, r2)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("does not match expense total");
    }

    @Test
    @DisplayName("PERCENTAGE: 50+50 of 1000 converts to 500+500")
    void testPercentage_50_50_of_1000() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, null, new BigDecimal("50"));
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, null, new BigDecimal("50"));

        List<ExpenseSplit> result = svc.validateAndBuildPercentageSplits(new BigDecimal("1000"), List.of(r1, r2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(u1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("500.00");
        assertThat(result.get(0).getPercentage()).isEqualByComparingTo("50");
        assertThat(result.get(0).getSplitType()).isEqualTo(SplitType.PERCENTAGE);
        assertThat(result.get(1).getUserId()).isEqualTo(u2);
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("500.00");
        assertThat(result.get(1).getPercentage()).isEqualByComparingTo("50");
        assertThat(result.get(1).getSplitType()).isEqualTo(SplitType.PERCENTAGE);
        BigDecimal sum = result.stream()
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("PERCENTAGE: 50+49 (sum 99) throws InvalidSplitException")
    void testPercentage_50_49_sum_99_throws() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, null, new BigDecimal("50"));
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, null, new BigDecimal("49"));

        assertThatThrownBy(() -> svc.validateAndBuildPercentageSplits(new BigDecimal("1000"), List.of(r1, r2)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("does not equal 100%");
    }

    @Test
    @DisplayName("EQUAL with duplicate userIds -> validateNoDuplicateUsers throws")
    void testValidateNoDuplicateUsers_duplicates_throws() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        assertThatThrownBy(() -> svc.validateNoDuplicateUsers(List.of(u1, u2, u1)))
                .isInstanceOf(DuplicateParticipantException.class)
                .hasMessageContaining("duplicate participants");
    }

    @Test
    @DisplayName("EXACT with negative amount throws InvalidSplitException")
    void testExact_negative_amount_throws() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, new BigDecimal("-10"), null);
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, new BigDecimal("1010"), null);

        assertThatThrownBy(() -> svc.validateAndBuildExactSplits(new BigDecimal("1000"), List.of(r1, r2)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("EXACT with zero amount throws InvalidSplitException")
    void testExact_zero_amount_throws() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, BigDecimal.ZERO, null);
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, new BigDecimal("1000"), null);

        assertThatThrownBy(() -> svc.validateAndBuildExactSplits(new BigDecimal("1000"), List.of(r1, r2)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("PERCENTAGE: decimal 33.33+33.33+33.34=100% of 100 converts correctly, sum=100.00")
    void testPercentage_decimal_3333_3333_3334_of_100() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, null, new BigDecimal("33.33"));
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, null, new BigDecimal("33.33"));
        ExpenseSplitRequest r3 = new ExpenseSplitRequest(u3, null, new BigDecimal("33.34"));

        List<ExpenseSplit> result = svc.validateAndBuildPercentageSplits(new BigDecimal("100"), List.of(r1, r2, r3));

        assertThat(result).hasSize(3);
        BigDecimal sum = result.stream()
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("PERCENTAGE: negative rounding remainder is subtracted so sum matches total")
    void testPercentage_negativeRoundingRemainder_adjustsDown() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ExpenseSplitRequest r1 = new ExpenseSplitRequest(u1, null, new BigDecimal("50"));
        ExpenseSplitRequest r2 = new ExpenseSplitRequest(u2, null, new BigDecimal("50"));

        List<ExpenseSplit> result = svc.validateAndBuildPercentageSplits(new BigDecimal("0.05"), List.of(r1, r2));

        assertThat(result).hasSize(2);
        BigDecimal sum = result.stream()
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.05");
        assertThat(result)
                .extracting(ExpenseSplit::getAmount)
                .containsExactly(new BigDecimal("0.02"), new BigDecimal("0.03"));
    }
}
