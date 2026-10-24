package com.hishob.settlement.service;

import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.ExpenseServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.dto.*;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.exception.BalanceCalculationException;
import com.hishob.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BalanceCalculationService {

    private final GroupServiceClient groupServiceClient;
    private final ExpenseServiceClient expenseServiceClient;
    private final SettlementRepository settlementRepository;
    private final AuthServiceClient authServiceClient;

    public BalanceCalculationService(GroupServiceClient groupServiceClient,
                                     ExpenseServiceClient expenseServiceClient,
                                     SettlementRepository settlementRepository,
                                     AuthServiceClient authServiceClient) {
        this.groupServiceClient = groupServiceClient;
        this.expenseServiceClient = expenseServiceClient;
        this.settlementRepository = settlementRepository;
        this.authServiceClient = authServiceClient;
    }

    /**
     * Calculates effective net balances for all group members by:
     *
     *  1. Computing expense-derived net balances:
     *       netBalance = totalPaid - totalShare
     *
     *  2. Fetching COMPLETED settlements directly from SettlementRepository.
     *
     *  3. Applying each COMPLETED settlement:
     *       fromUserId (payer):    netBalance += amount  (debt reduced)
     *       toUserId   (receiver): netBalance -= amount  (credit reduced)
     *
     *  4. Validating that no settlement references a user outside the group,
     *     that all settlement amounts are positive, and that no settlement
     *     creates an over-adjusted balance.
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

    /**
     * Rows for {@code GET /api/personal-balances}.
     *
     * <p>The current user's row is their graph-global position (everything they paid minus
     * everything they owe across all personal expenses) because the summary cards need the
     * "You owe" / "You are owed" totals. Every other row is the pairwise standing between the
     * current user and that person — only money that moves directly between the two of them.
     * A friend's debts to third parties are not the current user's to settle and must not inflate
     * the amount on their card.
     *
     * <p>Pairwise is the one definition of "amount payable to a particular friend": it also backs
     * {@code GET /api/friends/{id}/balance} and the cap enforced when a personal settlement is
     * recorded, so an amount the UI offers to settle is always an amount the backend accepts.
     */
    public PersonalBalanceSummaryResponse calculatePersonalBalances(UUID currentUserId) {
        PersonalLedger ledger = loadPersonalLedger(currentUserId);

        List<PersonalBalanceResponse> balances = new ArrayList<>();
        for (UUID userId : ledger.participants) {
            if (userId.equals(currentUserId)) {
                BigDecimal net = money(ledger.currentUserPaid
                        .subtract(ledger.currentUserShare)
                        .add(ledger.currentUserSettled));
                balances.add(new PersonalBalanceResponse(userId,
                        money(ledger.currentUserPaid), money(ledger.currentUserShare), net, resolveStatus(net)));
                continue;
            }
            PairwisePosition counterparty = ledger.pairwise.get(userId);
            BigDecimal net = counterparty == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : counterparty.netForCounterparty();
            BigDecimal paid = counterparty == null ? BigDecimal.ZERO : counterparty.credit;
            BigDecimal share = counterparty == null ? BigDecimal.ZERO : counterparty.debt;
            balances.add(new PersonalBalanceResponse(userId, money(paid), money(share), net, resolveStatus(net)));
        }

        verifyPersonalConservation(balances);
        return new PersonalBalanceSummaryResponse(currentUserId, balances);
    }

    public FriendBalanceResponse calculateFriendBalance(UUID currentUserId, UUID friendUserId) {
        authServiceClient.validateAcceptedFriendship(currentUserId, friendUserId);
        PersonalLedger ledger = loadPersonalLedger(currentUserId);

        PairwisePosition counterparty = ledger.pairwise.get(friendUserId);
        BigDecimal currentNet = counterparty == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : counterparty.netForCurrentUser();

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

        // ── Step 2: fetch COMPLETED settlements directly from repository ──────
        List<Settlement> completedSettlementEntities =
                settlementRepository.findByGroupIdAndStatus(groupId, SettlementStatus.COMPLETED);
        List<CompletedSettlementData> completedSettlements = completedSettlementEntities == null ? List.of() :
                completedSettlementEntities.stream()
                        .map(s -> new CompletedSettlementData(s.getFromUserId(), s.getToUserId(), s.getAmount()))
                        .toList();

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
            if (fromBalance.compareTo(BigDecimal.ZERO) < 0) {
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
            netBalances.put(fromUserId, fromBalance.add(amount));
            netBalances.put(toUserId,   toBalance.subtract(amount));
        }

        // ── Step 5: build response and verify conservation ────────────────────
        List<UserBalanceResponse> balances = new ArrayList<>();
        for (UUID memberId : memberIds) {
            BigDecimal net = netBalances.get(memberId);
            BigDecimal paid  = totalPaid.getOrDefault(memberId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = totalShare.getOrDefault(memberId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            balances.add(new UserBalanceResponse(memberId, paid, share, net, resolveStatus(net)));
        }

        verifyConservation(balances, groupId);

        return new GroupBalanceResponse(groupId, balances);
    }

    /**
     * Single pass behind every personal surface: who appears in the current user's graph, the
     * current user's graph-global totals, and the pairwise standing with each counterparty.
     */
    private PersonalLedger loadPersonalLedger(UUID currentUserId) {
        List<InternalExpenseData> expenses = expenseServiceClient.getPersonalExpensesForUser(currentUserId);
        PersonalLedger ledger = new PersonalLedger();

        for (InternalExpenseData expense : expenses == null ? List.<InternalExpenseData>of() : expenses) {
            if (expense.getGroupId() != null) {
                continue;
            }
            UUID payer = expense.getPaidBy();
            if (payer != null) {
                ledger.participants.add(payer);
                if (payer.equals(currentUserId)) {
                    ledger.currentUserPaid = ledger.currentUserPaid.add(money(expense.getTotalAmount()));
                }
            }
            if (payer == null || expense.getSplits() == null) {
                continue;
            }
            for (InternalSplitData split : expense.getSplits()) {
                UUID splitter = split.getUserId();
                if (splitter == null || split.getAmount() == null) {
                    continue;
                }
                ledger.participants.add(splitter);
                BigDecimal amount = money(split.getAmount());
                if (splitter.equals(currentUserId)) {
                    ledger.currentUserShare = ledger.currentUserShare.add(amount);
                }
                if (splitter.equals(payer)) {
                    continue;
                }
                if (splitter.equals(currentUserId)) {
                    ledger.position(payer).addCredit(amount);
                } else if (payer.equals(currentUserId)) {
                    ledger.position(splitter).addDebt(amount);
                }
            }
        }

        for (CompletedSettlementData settlement : completedPersonalSettlements(currentUserId)) {
            UUID fromUserId = settlement.getFromUserId();
            UUID toUserId = settlement.getToUserId();
            BigDecimal amount = money(settlement.getAmount());
            if (fromUserId == null || toUserId == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BalanceCalculationException("Completed personal settlement has invalid data");
            }
            if (fromUserId.equals(currentUserId)) {
                ledger.currentUserSettled = ledger.currentUserSettled.add(amount);
                if (!toUserId.equals(currentUserId)) {
                    ledger.participants.add(toUserId);
                    ledger.position(toUserId).addDebt(amount);
                }
            }
            if (toUserId.equals(currentUserId)) {
                ledger.currentUserSettled = ledger.currentUserSettled.subtract(amount);
                if (!fromUserId.equals(currentUserId)) {
                    ledger.participants.add(fromUserId);
                    ledger.position(fromUserId).addCredit(amount);
                }
            }
        }
        return ledger;
    }

    private List<CompletedSettlementData> completedPersonalSettlements(UUID currentUserId) {
        List<Settlement> entities = settlementRepository.findPersonalByUserIdAndStatus(currentUserId, SettlementStatus.COMPLETED);
        return entities == null ? List.of() : entities.stream()
                .map(s -> new CompletedSettlementData(s.getFromUserId(), s.getToUserId(), s.getAmount()))
                .toList();
    }

    private static final class PersonalLedger {

        private final Set<UUID> participants = new TreeSet<>(Comparator.comparing(UUID::toString));
        private final Map<UUID, PairwisePosition> pairwise = new HashMap<>();
        private BigDecimal currentUserPaid = BigDecimal.ZERO;
        private BigDecimal currentUserShare = BigDecimal.ZERO;
        private BigDecimal currentUserSettled = BigDecimal.ZERO;

        private PairwisePosition position(UUID userId) {
            return pairwise.computeIfAbsent(userId, ignored -> new PairwisePosition());
        }
    }

    /** Money that moves directly between the current user and one counterparty. */
    private static final class PairwisePosition {

        private BigDecimal credit = BigDecimal.ZERO;
        private BigDecimal debt = BigDecimal.ZERO;

        private void addCredit(BigDecimal amount) {
            credit = credit.add(amount);
        }

        private void addDebt(BigDecimal amount) {
            debt = debt.add(amount);
        }

        /** Positive when the counterparty is owed by the current user. */
        private BigDecimal netForCounterparty() {
            return money(credit.subtract(debt));
        }

        /** The same figure read from the current user's side — the cap applied to settlements. */
        private BigDecimal netForCurrentUser() {
            return money(debt.subtract(credit));
        }
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

    private static BigDecimal money(BigDecimal value) {
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
