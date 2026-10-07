package com.hishob.balance.service;

import com.hishob.balance.client.AuthServiceClient;
import com.hishob.balance.client.ExpenseServiceClient;
import com.hishob.balance.client.GroupServiceClient;
import com.hishob.balance.client.SettlementServiceClient;
import com.hishob.balance.dto.*;
import com.hishob.balance.exception.BalanceCalculationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BalanceCalculationService {

    private final GroupServiceClient groupServiceClient;
    private final ExpenseServiceClient expenseServiceClient;
    private final SettlementServiceClient settlementServiceClient;
    private final AuthServiceClient authServiceClient;

    public BalanceCalculationService(GroupServiceClient groupServiceClient,
                                     ExpenseServiceClient expenseServiceClient,
                                     SettlementServiceClient settlementServiceClient,
                                     AuthServiceClient authServiceClient) {
        this.groupServiceClient = groupServiceClient;
        this.expenseServiceClient = expenseServiceClient;
        this.settlementServiceClient = settlementServiceClient;
        this.authServiceClient = authServiceClient;
    }

    /**
     * Calculates effective net balances for all group members by:
     *
     *  1. Computing expense-derived net balances:
     *       netBalance = totalPaid - totalShare
     *
     *  2. Fetching COMPLETED settlements from Settlement Service.
     *
     *  3. Applying each COMPLETED settlement:
     *       fromUserId (payer):    netBalance += amount  (debt reduced)
     *       toUserId   (receiver): netBalance -= amount  (credit reduced)
     *
     *  4. Validating that no settlement references a user outside the group,
     *     that all settlement amounts are positive, and that no settlement
     *     creates an over-adjusted balance (i.e., a debtor cannot pay more
     *     than they owe and thereby become a creditor — that would indicate
     *     inconsistent settlement data).
     *
     *  5. Verifying conservation: sum of all effective net balances == 0.
     *
     * PENDING settlements are never included — only COMPLETED ones affect balances.
     */
    public GroupBalanceResponse calculateGroupBalances(UUID groupId, UUID currentUserId) {
        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, currentUserId);
        return calculateBalancesForGroup(groupId);
    }

    public GroupBalanceResponse calculateGroupBalancesInternal(UUID groupId) {
        groupServiceClient.validateGroupExists(groupId);
        return calculateBalancesForGroup(groupId);
    }

    public PersonalBalanceSummaryResponse calculatePersonalBalances(UUID currentUserId) {
        List<InternalExpenseData> expenses = expenseServiceClient.getPersonalExpensesForUser(currentUserId);
        List<CompletedSettlementData> settlements = settlementServiceClient.getCompletedPersonalSettlements(currentUserId);
        if (settlements == null) {
            settlements = List.of();
        }
        List<PersonalBalanceResponse> balances = calculatePersonalBalancesFromExpenses(expenses, settlements);
        return new PersonalBalanceSummaryResponse(currentUserId, balances);
    }

    public FriendBalanceResponse calculateFriendBalance(UUID currentUserId, UUID friendUserId) {
        authServiceClient.validateAcceptedFriendship(currentUserId, friendUserId);
        List<InternalExpenseData> expenses = expenseServiceClient.getPersonalExpensesForUser(currentUserId);

        BigDecimal currentNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (InternalExpenseData expense : expenses) {
            UUID payer = expense.getPaidBy();
            if (payer == null || expense.getSplits() == null) {
                continue;
            }
            for (InternalSplitData split : expense.getSplits()) {
                if (split.getUserId() == null || split.getAmount() == null || split.getUserId().equals(payer)) {
                    continue;
                }
                BigDecimal amount = money(split.getAmount());
                if (payer.equals(currentUserId) && split.getUserId().equals(friendUserId)) {
                    currentNet = currentNet.add(amount);
                } else if (payer.equals(friendUserId) && split.getUserId().equals(currentUserId)) {
                    currentNet = currentNet.subtract(amount);
                }
            }
        }

        List<CompletedSettlementData> settlements = settlementServiceClient.getCompletedPersonalSettlements(currentUserId);
        if (settlements == null) {
            settlements = List.of();
        }
        for (CompletedSettlementData settlement : settlements) {
            UUID fromUserId = settlement.getFromUserId();
            UUID toUserId = settlement.getToUserId();
            BigDecimal amount = money(settlement.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BalanceCalculationException("Completed personal settlement has invalid amount");
            }
            if (fromUserId.equals(currentUserId) && toUserId.equals(friendUserId)) {
                currentNet = currentNet.add(amount);
            } else if (fromUserId.equals(friendUserId) && toUserId.equals(currentUserId)) {
                currentNet = currentNet.subtract(amount);
            }
        }

        currentNet = money(currentNet);
        BigDecimal friendNet = currentNet.negate().setScale(2, RoundingMode.HALF_UP);
        UUID fromUserId = null;
        UUID toUserId = null;
        BigDecimal amount = currentNet.abs().setScale(2, RoundingMode.HALF_UP);
        if (currentNet.compareTo(BigDecimal.ZERO) > 0) {
            fromUserId = friendUserId;
            toUserId = currentUserId;
        } else if (currentNet.compareTo(BigDecimal.ZERO) < 0) {
            fromUserId = currentUserId;
            toUserId = friendUserId;
        }

        return new FriendBalanceResponse(
                currentUserId,
                friendUserId,
                currentNet,
                friendNet,
                resolveStatus(currentNet),
                resolveStatus(friendNet),
                fromUserId,
                toUserId,
                amount);
    }

    public PersonalDebtSimplificationResponse simplifyPersonalBalances(UUID currentUserId) {
        PersonalBalanceSummaryResponse summary = calculatePersonalBalances(currentUserId);
        return new PersonalDebtSimplificationResponse(currentUserId, simplify(summary.getBalances()));
    }

    private GroupBalanceResponse calculateBalancesForGroup(UUID groupId) {
        List<UUID> memberIds = groupServiceClient.getGroupMemberIds(groupId);
        List<InternalExpenseData> expenses = expenseServiceClient.getGroupExpenses(groupId);

        // ── Step 1: expense-derived balances ─────────────────────────────────
        Map<UUID, BigDecimal> totalPaid  = new HashMap<>();
        Map<UUID, BigDecimal> totalShare = new HashMap<>();

        for (UUID memberId : memberIds) {
            totalPaid.put(memberId, BigDecimal.ZERO);
            totalShare.put(memberId, BigDecimal.ZERO);
        }

        for (InternalExpenseData expense : expenses) {
            UUID payer = expense.getPaidBy();
            totalPaid.merge(payer, expense.getTotalAmount(), BigDecimal::add);
            for (InternalSplitData split : expense.getSplits()) {
                totalShare.merge(split.getUserId(), split.getAmount(), BigDecimal::add);
            }
        }

        // Build mutable net balance map (keyed by memberId)
        Map<UUID, BigDecimal> netBalances = new LinkedHashMap<>();
        for (UUID memberId : memberIds) {
            BigDecimal paid  = totalPaid.getOrDefault(memberId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = totalShare.getOrDefault(memberId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            netBalances.put(memberId, paid.subtract(share));
        }

        // ── Step 2: fetch COMPLETED settlements ───────────────────────────────
        // ServiceUnavailableException propagates as-is → HTTP 503.
        // We never silently assume zero settlements on downstream failure.
        List<CompletedSettlementData> completedSettlements =
                settlementServiceClient.getCompletedSettlements(groupId);

        // ── Step 3 & 4: apply settlements with validation ─────────────────────
        for (CompletedSettlementData settlement : completedSettlements) {
            UUID fromUserId = settlement.getFromUserId();
            UUID toUserId   = settlement.getToUserId();
            BigDecimal amount = settlement.getAmount() != null
                    ? settlement.getAmount().setScale(2, RoundingMode.HALF_UP)
                    : null;

            // Validate: amount must be positive
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BalanceCalculationException(
                        "Completed settlement has invalid amount for group " + groupId
                        + ": fromUserId=" + fromUserId + " toUserId=" + toUserId
                        + " amount=" + amount);
            }

            // Validate: both users must be known group members
            if (!netBalances.containsKey(fromUserId)) {
                throw new BalanceCalculationException(
                        "Completed settlement references payer " + fromUserId
                        + " who is not a member of group " + groupId);
            }
            if (!netBalances.containsKey(toUserId)) {
                throw new BalanceCalculationException(
                        "Completed settlement references receiver " + toUserId
                        + " who is not a member of group " + groupId);
            }

            BigDecimal fromBalance = netBalances.get(fromUserId);
            BigDecimal toBalance   = netBalances.get(toUserId);

            // Validate: settlement must not cause over-adjustment.
            // A debtor (fromBalance < 0) can only pay up to their outstanding debt.
            // If the settlement amount exceeds what the payer owes, the data is
            // inconsistent — we reject it rather than silently invert the balance.
            //
            // Example of rejection:
            //   expense-derived: B = -500
            //   completed settlement: B → A = 600
            //   Result would be B = -500 + 600 = +100 (B becomes a creditor — inconsistent)
            //
            // We only apply this check when fromBalance is negative (payer is a debtor).
            // A payer who is already SETTLED or a creditor paying forward is not rejected
            // here — that edge case is unusual but not mathematically invalid (it may
            // represent a gratuity or out-of-band correction).
            if (fromBalance.compareTo(BigDecimal.ZERO) < 0) {
                // fromBalance is negative; the absolute debt is -fromBalance
                // If amount > -fromBalance, the payer over-paid
                if (amount.compareTo(fromBalance.negate()) > 0) {
                    throw new BalanceCalculationException(
                            "Completed settlement amount " + amount
                            + " exceeds payer " + fromUserId
                            + "'s outstanding debt " + fromBalance.negate()
                            + " in group " + groupId
                            + ". Settlement data is inconsistent.");
                }
            }

            // Apply the settlement:
            //   payer's debt decreases  → netBalance increases
            //   receiver's credit decreases → netBalance decreases
            netBalances.put(fromUserId, fromBalance.add(amount));
            netBalances.put(toUserId,   toBalance.subtract(amount));
        }

        // ── Step 5: build response and verify conservation ────────────────────
        List<UserBalanceResponse> balances = new ArrayList<>();
        for (UUID memberId : memberIds) {
            BigDecimal net = netBalances.get(memberId);
            // Retrieve original paid/share for the response fields (unchanged by settlements)
            BigDecimal paid  = totalPaid.getOrDefault(memberId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = totalShare.getOrDefault(memberId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            balances.add(new UserBalanceResponse(memberId, paid, share, net, resolveStatus(net)));
        }

        verifyConservation(balances, groupId);

        return new GroupBalanceResponse(groupId, balances);
    }

    private List<PersonalBalanceResponse> calculatePersonalBalancesFromExpenses(
            List<InternalExpenseData> expenses,
            List<CompletedSettlementData> settlements) {
        Map<UUID, BigDecimal> totalPaid = new HashMap<>();
        Map<UUID, BigDecimal> totalShare = new HashMap<>();
        Set<UUID> users = new TreeSet<>(Comparator.comparing(UUID::toString));

        for (InternalExpenseData expense : expenses) {
            if (expense.getGroupId() != null) {
                continue;
            }
            UUID payer = expense.getPaidBy();
            if (payer != null) {
                users.add(payer);
                totalPaid.merge(payer, money(expense.getTotalAmount()), BigDecimal::add);
            }
            if (expense.getSplits() != null) {
                for (InternalSplitData split : expense.getSplits()) {
                    if (split.getUserId() == null) {
                        continue;
                    }
                    users.add(split.getUserId());
                    totalShare.merge(split.getUserId(), money(split.getAmount()), BigDecimal::add);
                }
            }
        }

        Map<UUID, BigDecimal> settlementAdjustments = new HashMap<>();
        for (CompletedSettlementData settlement : settlements) {
            UUID fromUserId = settlement.getFromUserId();
            UUID toUserId = settlement.getToUserId();
            BigDecimal amount = money(settlement.getAmount());
            if (fromUserId == null || toUserId == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BalanceCalculationException("Completed personal settlement has invalid data");
            }
            users.add(fromUserId);
            users.add(toUserId);
            settlementAdjustments.merge(fromUserId, amount, BigDecimal::add);
            settlementAdjustments.merge(toUserId, amount.negate(), BigDecimal::add);
        }

        List<PersonalBalanceResponse> balances = new ArrayList<>();
        for (UUID userId : users) {
            BigDecimal paid = money(totalPaid.getOrDefault(userId, BigDecimal.ZERO));
            BigDecimal share = money(totalShare.getOrDefault(userId, BigDecimal.ZERO));
            BigDecimal net = money(paid.subtract(share).add(settlementAdjustments.getOrDefault(userId, BigDecimal.ZERO)));
            balances.add(new PersonalBalanceResponse(userId, paid, share, net, resolveStatus(net)));
        }

        verifyPersonalConservation(balances);
        return balances;
    }

    private List<PersonalDebtSimplificationSuggestion> simplify(List<PersonalBalanceResponse> balances) {
        List<BalanceEntry> creditors = new ArrayList<>();
        List<BalanceEntry> debtors = new ArrayList<>();

        for (PersonalBalanceResponse balance : balances) {
            BigDecimal net = money(balance.getNetBalance());
            int cmp = net.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new BalanceEntry(balance.getUserId(), net));
            } else if (cmp < 0) {
                debtors.add(new BalanceEntry(balance.getUserId(), net.negate()));
            }
        }

        Comparator<BalanceEntry> byUserId = Comparator.comparing(entry -> entry.userId.toString());
        creditors.sort(byUserId);
        debtors.sort(byUserId);

        List<PersonalDebtSimplificationSuggestion> suggestions = new ArrayList<>();
        int creditorIndex = 0;
        int debtorIndex = 0;
        while (creditorIndex < creditors.size() && debtorIndex < debtors.size()) {
            BalanceEntry creditor = creditors.get(creditorIndex);
            BalanceEntry debtor = debtors.get(debtorIndex);
            BigDecimal transfer = money(creditor.amount.min(debtor.amount));

            if (transfer.compareTo(BigDecimal.ZERO) > 0 && !debtor.userId.equals(creditor.userId)) {
                suggestions.add(new PersonalDebtSimplificationSuggestion(debtor.userId, creditor.userId, transfer));
            }

            creditor.amount = money(creditor.amount.subtract(transfer));
            debtor.amount = money(debtor.amount.subtract(transfer));
            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) creditorIndex++;
            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) debtorIndex++;
        }

        return suggestions;
    }

    private BalanceStatus resolveStatus(BigDecimal net) {
        int cmp = net.compareTo(BigDecimal.ZERO);
        if (cmp > 0) return BalanceStatus.GETS;
        if (cmp < 0) return BalanceStatus.OWES;
        return BalanceStatus.SETTLED;
    }

    private void verifyConservation(List<UserBalanceResponse> balances, UUID groupId) {
        BigDecimal sum = balances.stream()
                .map(UserBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new BalanceCalculationException(
                    "Balance conservation failed for group " + groupId + ": sum=" + sum);
        }
    }

    private void verifyPersonalConservation(List<PersonalBalanceResponse> balances) {
        BigDecimal sum = balances.stream()
                .map(PersonalBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (money(sum).compareTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BalanceCalculationException("Personal balance conservation failed: sum=" + sum);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static class BalanceEntry {
        private UUID userId;
        private BigDecimal amount;

        private BalanceEntry(UUID userId, BigDecimal amount) {
            this.userId = userId;
            this.amount = amount;
        }
    }

}
