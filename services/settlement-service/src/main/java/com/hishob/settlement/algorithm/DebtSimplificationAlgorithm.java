package com.hishob.settlement.algorithm;

import com.hishob.settlement.dto.InternalBalanceData;
import com.hishob.settlement.dto.SettlementSuggestion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic debt simplification algorithm.
 *
 * Algorithm:
 * 1. Separate members into creditors (netBalance > 0) and debtors (netBalance < 0).
 * 2. Sort both lists by userId (deterministic ordering).
 * 3. Use two pointers: for each debtor, match against the current creditor.
 *    Transfer min(debtorDebt, creditorCredit).
 * 4. Reduce both balances. Advance the pointer whose balance reaches zero.
 * 5. Continue until all balances are zero.
 * 6. Never emit zero-value or self-settlements.
 *
 * Note: This greedy approach minimises the number of transactions for many
 * common cases but is not guaranteed to be universally optimal for all
 * possible debt graphs.
 */
@Component
public class DebtSimplificationAlgorithm {

    public List<SettlementSuggestion> simplify(List<InternalBalanceData> balances) {
        // Build mutable sorted lists of creditors and debtors
        List<BalanceEntry> creditors = new ArrayList<>();
        List<BalanceEntry> debtors = new ArrayList<>();

        for (InternalBalanceData b : balances) {
            int cmp = b.getNetBalance().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new BalanceEntry(b.getUserId(), b.getNetBalance()));
            } else if (cmp < 0) {
                debtors.add(new BalanceEntry(b.getUserId(), b.getNetBalance().negate()));
            }
            // SETTLED members (zero) are ignored
        }

        // Deterministic ordering by userId string
        Comparator<BalanceEntry> byId = Comparator.comparing(e -> e.userId.toString());
        creditors.sort(byId);
        debtors.sort(byId);

        List<SettlementSuggestion> result = new ArrayList<>();

        int ci = 0;
        int di = 0;

        while (ci < creditors.size() && di < debtors.size()) {
            BalanceEntry creditor = creditors.get(ci);
            BalanceEntry debtor = debtors.get(di);

            BigDecimal transfer = creditor.amount.min(debtor.amount);

            if (transfer.compareTo(BigDecimal.ZERO) > 0
                    && !debtor.userId.equals(creditor.userId)) {
                result.add(new SettlementSuggestion(debtor.userId, creditor.userId, transfer));
            }

            creditor.amount = creditor.amount.subtract(transfer);
            debtor.amount = debtor.amount.subtract(transfer);

            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) ci++;
            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) di++;
        }

        return result;
    }

    private static class BalanceEntry {
        UUID userId;
        BigDecimal amount;

        BalanceEntry(UUID userId, BigDecimal amount) {
            this.userId = userId;
            this.amount = amount;
        }
    }

}
