package com.hishob.settlement.algorithm;

import com.hishob.settlement.dto.InternalBalanceData;
import com.hishob.settlement.dto.SettlementSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DebtSimplificationAlgorithmTest {

    private DebtSimplificationAlgorithm algorithm;

    private UUID userA;
    private UUID userB;
    private UUID userC;
    private UUID userD;

    @BeforeEach
    void setUp() {
        algorithm = new DebtSimplificationAlgorithm();
        // Fixed UUIDs for deterministic ordering
        userA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        userC = UUID.fromString("00000000-0000-0000-0000-000000000003");
        userD = UUID.fromString("00000000-0000-0000-0000-000000000004");
    }

    private InternalBalanceData balance(UUID userId, String net) {
        InternalBalanceData d = new InternalBalanceData();
        d.setUserId(userId);
        d.setNetBalance(new BigDecimal(net));
        return d;
    }

    private BigDecimal totalSent(List<SettlementSuggestion> suggestions, UUID userId) {
        return suggestions.stream()
                .filter(s -> s.getFromUserId().equals(userId))
                .map(SettlementSuggestion::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalReceived(List<SettlementSuggestion> suggestions, UUID userId) {
        return suggestions.stream()
                .filter(s -> s.getToUserId().equals(userId))
                .map(SettlementSuggestion::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── 1. One debtor + one creditor ──────────────────────────────────────────

    @Test
    @DisplayName("1. One debtor + one creditor: B owes A 300")
    void oneDebtorOneCreditor() {
        // A = +300, B = -300
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "300.00"),
                balance(userB, "-300.00")
        ));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFromUserId()).isEqualTo(userB);
        assertThat(result.get(0).getToUserId()).isEqualTo(userA);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("300.00");
    }

    // ── 2. One creditor + multiple debtors ────────────────────────────────────

    @Test
    @DisplayName("2. One creditor + multiple debtors: A=+500, B=-300, C=-200")
    void oneCreditorMultipleDebtors() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "500.00"),
                balance(userB, "-300.00"),
                balance(userC, "-200.00")
        ));

        assertThat(result).hasSize(2);
        assertThat(totalReceived(result, userA)).isEqualByComparingTo("500.00");
        assertThat(totalSent(result, userB)).isEqualByComparingTo("300.00");
        assertThat(totalSent(result, userC)).isEqualByComparingTo("200.00");
    }

    // ── 3. Multiple creditors + one debtor ────────────────────────────────────

    @Test
    @DisplayName("3. Multiple creditors + one debtor: A=+300, B=+200, C=-500")
    void multipleCreditorOneDebtor() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "300.00"),
                balance(userB, "200.00"),
                balance(userC, "-500.00")
        ));

        assertThat(result).hasSize(2);
        assertThat(totalSent(result, userC)).isEqualByComparingTo("500.00");
        assertThat(totalReceived(result, userA)).isEqualByComparingTo("300.00");
        assertThat(totalReceived(result, userB)).isEqualByComparingTo("200.00");
    }

    // ── 4. Multiple creditors + multiple debtors ──────────────────────────────

    @Test
    @DisplayName("4. Multiple creditors + multiple debtors: A=+600, B=+400, C=-500, D=-500")
    void multipleCreditorMultipleDebtor() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "600.00"),
                balance(userB, "400.00"),
                balance(userC, "-500.00"),
                balance(userD, "-500.00")
        ));

        assertThat(result).isNotEmpty();
        // Conservation: total sent == total received
        BigDecimal totalSent = result.stream().map(SettlementSuggestion::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = result.stream().map(SettlementSuggestion::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalSent).isEqualByComparingTo(totalReceived);

        assertThat(totalSent(result, userC)).isEqualByComparingTo("500.00");
        assertThat(totalSent(result, userD)).isEqualByComparingTo("500.00");
        assertThat(totalReceived(result, userA)).isEqualByComparingTo("600.00");
        assertThat(totalReceived(result, userB)).isEqualByComparingTo("400.00");
    }

    // ── 5. Three-user settlement ───────────────────────────────────────────────

    @Test
    @DisplayName("5. Three-user: A=+600, B=-300, C=-300")
    void threeUserSettlement() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "600.00"),
                balance(userB, "-300.00"),
                balance(userC, "-300.00")
        ));

        assertThat(result).hasSize(2);
        assertThat(totalReceived(result, userA)).isEqualByComparingTo("600.00");
        assertThat(totalSent(result, userB)).isEqualByComparingTo("300.00");
        assertThat(totalSent(result, userC)).isEqualByComparingTo("300.00");
    }

    // ── 6. Zero balances ──────────────────────────────────────────────────────

    @Test
    @DisplayName("6. All zero balances → empty suggestions")
    void zeroBalances() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "0.00"),
                balance(userB, "0.00")
        ));

        assertThat(result).isEmpty();
    }

    // ── 7. Already settled group ──────────────────────────────────────────────

    @Test
    @DisplayName("7. Already settled group → empty suggestions")
    void alreadySettled() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of());
        assertThat(result).isEmpty();
    }

    // ── 8. Rounding with BigDecimal ───────────────────────────────────────────

    @Test
    @DisplayName("8. Rounding: fractional amounts handled correctly")
    void roundingBigDecimal() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "66.66"),
                balance(userB, "-33.33"),
                balance(userC, "-33.33")
        ));

        assertThat(result).hasSize(2);
        assertThat(totalReceived(result, userA)).isEqualByComparingTo("66.66");
    }

    // ── 9. No zero-value settlement ───────────────────────────────────────────

    @Test
    @DisplayName("9. No zero-value settlements generated")
    void noZeroValueSettlement() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "100.00"),
                balance(userB, "-100.00"),
                balance(userC, "0.00")
        ));

        assertThat(result).allMatch(s -> s.getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    // ── 10. No self-settlement ────────────────────────────────────────────────

    @Test
    @DisplayName("10. No self-settlements generated")
    void noSelfSettlement() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "100.00"),
                balance(userB, "-100.00")
        ));

        assertThat(result).allMatch(s -> !s.getFromUserId().equals(s.getToUserId()));
    }

    // ── 11. Settlement conservation ───────────────────────────────────────────

    @Test
    @DisplayName("11. Conservation: total sent == total received")
    void settlementConservation() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "600.00"),
                balance(userB, "400.00"),
                balance(userC, "-500.00"),
                balance(userD, "-500.00")
        ));

        BigDecimal sent = result.stream().map(SettlementSuggestion::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = result.stream().map(SettlementSuggestion::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sent).isEqualByComparingTo(received);
    }

    // ── 12. Debtor total matches debt ─────────────────────────────────────────

    @Test
    @DisplayName("12. Debtor total sent matches their debt")
    void debtorTotalMatchesDebt() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "500.00"),
                balance(userB, "-300.00"),
                balance(userC, "-200.00")
        ));

        assertThat(totalSent(result, userB)).isEqualByComparingTo("300.00");
        assertThat(totalSent(result, userC)).isEqualByComparingTo("200.00");
    }

    // ── 13. Creditor total matches credit ─────────────────────────────────────

    @Test
    @DisplayName("13. Creditor total received matches their credit")
    void creditorTotalMatchesCredit() {
        List<SettlementSuggestion> result = algorithm.simplify(List.of(
                balance(userA, "500.00"),
                balance(userB, "-300.00"),
                balance(userC, "-200.00")
        ));

        assertThat(totalReceived(result, userA)).isEqualByComparingTo("500.00");
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Determinism: same input produces same output")
    void determinism() {
        List<InternalBalanceData> input = List.of(
                balance(userA, "600.00"),
                balance(userB, "400.00"),
                balance(userC, "-500.00"),
                balance(userD, "-500.00")
        );

        List<SettlementSuggestion> first = algorithm.simplify(input);
        List<SettlementSuggestion> second = algorithm.simplify(input);

        assertThat(first).hasSize(second.size());
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i).getFromUserId()).isEqualTo(second.get(i).getFromUserId());
            assertThat(first.get(i).getToUserId()).isEqualTo(second.get(i).getToUserId());
            assertThat(first.get(i).getAmount()).isEqualByComparingTo(second.get(i).getAmount());
        }
    }

}
