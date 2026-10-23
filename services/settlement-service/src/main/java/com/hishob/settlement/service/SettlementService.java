package com.hishob.settlement.service;

import com.hishob.settlement.algorithm.DebtSimplificationAlgorithm;
import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.client.NotificationServiceClient;
import com.hishob.settlement.dto.*;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.exception.InvalidRecipientException;
import com.hishob.settlement.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    /**
     * Window used by the recent-duplicate guard. A double-clicked "Settle Up" button produces two
     * identical requests within a second or two; anything older than this is treated as a genuine
     * repeat payment.
     */
    private static final int DUPLICATE_WINDOW_SECONDS = 5;

    private final GroupServiceClient groupServiceClient;
    private final BalanceCalculationService balanceCalculationService;
    private final AuthServiceClient authServiceClient;
    private final DebtSimplificationAlgorithm algorithm;
    private final SettlementRepository settlementRepository;
    private final NotificationServiceClient notificationServiceClient;

    /**
     * Recordings currently in flight on this JVM instance, keyed by settlement identity. See
     * {@link #recordOnce} for exactly what this does and does not guarantee.
     */
    private final Set<String> recordingsInFlight = ConcurrentHashMap.newKeySet();

    public SettlementService(GroupServiceClient groupServiceClient,
                             BalanceCalculationService balanceCalculationService,
                             AuthServiceClient authServiceClient,
                             DebtSimplificationAlgorithm algorithm,
                             SettlementRepository settlementRepository,
                             NotificationServiceClient notificationServiceClient) {
        this.groupServiceClient = groupServiceClient;
        this.balanceCalculationService = balanceCalculationService;
        this.authServiceClient = authServiceClient;
        this.algorithm = algorithm;
        this.settlementRepository = settlementRepository;
        this.notificationServiceClient = notificationServiceClient;
    }

    public List<SettlementSuggestion> getSuggestions(UUID groupId, UUID currentUserId) {
        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, currentUserId);
        GroupBalanceResponse groupBalances = balanceCalculationService.calculateGroupBalancesInternal(groupId);
        List<InternalBalanceData> balances = groupBalances.getBalances().stream()
                .map(b -> new InternalBalanceData(b.getUserId(), b.getNetBalance()))
                .toList();
        return algorithm.simplify(balances);
    }

    @Transactional
    public SettlementHistoryResponse recordSettlement(UUID groupId, UUID fromUserId,
                                                      RecordSettlementRequest request) {
        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, fromUserId);

        UUID toUserId = request.getToUserId();
        if (fromUserId.equals(toUserId)) {
            throw new InvalidRecipientException("Payer and recipient cannot be the same user");
        }

        groupServiceClient.validateRecipientMember(groupId, toUserId);

        BigDecimal amount = money(request.getAmount());

        // Pre-settlement validation: check payer's outstanding debt via local BalanceCalculationService
        GroupBalanceResponse groupBalances = balanceCalculationService.calculateGroupBalancesInternal(groupId);
        UserBalanceResponse payerBalance = groupBalances.getBalances().stream()
                .filter(b -> b.getUserId().equals(fromUserId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Payer is not part of group balances"));

        BigDecimal netBalance = payerBalance.getNetBalance();
        if (netBalance.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalArgumentException("Payer has no outstanding debt in this group");
        }

        BigDecimal outstandingDebt = netBalance.negate();
        if (amount.compareTo(outstandingDebt) > 0) {
            throw new IllegalArgumentException("Settlement amount " + amount
                    + " exceeds payer's outstanding debt of " + outstandingDebt);
        }

        Settlement settlement = new Settlement(groupId, fromUserId, toUserId, amount, SettlementStatus.COMPLETED);
        settlement.setNotes(request.getNotes());

        return recordOnce(recordingKey(groupId, fromUserId, toUserId, amount), () -> {
            // Recent-duplicate guard: a same-details settlement recorded in the last 5 seconds is a
            // double click, not a second payment. UX guard only — it is not a uniqueness guarantee.
            if (settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                    groupId, fromUserId, toUserId, amount, Instant.now().minusSeconds(DUPLICATE_WINDOW_SECONDS)).isPresent()) {
                throw new IllegalArgumentException("A settlement with the same details was just recorded. Please wait a moment.");
            }
            Settlement saved = settlementRepository.save(settlement);
            sendSettlementNotifications(saved);
            return toHistoryResponse(saved);
        });
    }

    @Transactional
    public SettlementHistoryResponse recordPersonalSettlement(UUID fromUserId,
                                                             RecordPersonalSettlementRequest request) {
        UUID toUserId = request.getToUserId();
        if (fromUserId.equals(toUserId)) {
            throw new InvalidRecipientException("Payer and recipient cannot be the same user");
        }

        authServiceClient.validateAcceptedFriendship(fromUserId, toUserId);

        BigDecimal amount = money(request.getAmount());
        BigDecimal outstandingDebt = findOutstandingPersonalDebt(fromUserId, toUserId);
        if (outstandingDebt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payer has no outstanding personal debt to this recipient");
        }
        if (amount.compareTo(outstandingDebt) > 0) {
            throw new IllegalArgumentException("Settlement amount " + amount
                    + " exceeds payer's outstanding personal debt of " + outstandingDebt
                    + " to this recipient");
        }

        Settlement settlement = new Settlement(null, fromUserId, toUserId, amount, SettlementStatus.COMPLETED);
        settlement.setNotes(request.getNotes());

        return recordOnce(recordingKey(null, fromUserId, toUserId, amount), () -> {
            // Same recent-duplicate guard as the group path; UX guard only, not a uniqueness guarantee.
            if (settlementRepository
                    .findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                            fromUserId, toUserId, amount, Instant.now().minusSeconds(DUPLICATE_WINDOW_SECONDS))
                    .isPresent()) {
                throw new IllegalArgumentException("A settlement with the same details was just recorded. Please wait a moment.");
            }
            Settlement saved = settlementRepository.save(settlement);
            sendSettlementNotifications(saved);
            return toHistoryResponse(saved);
        });
    }

    /**
     * Runs a settlement recording exactly once per identical request that overlaps in time.
     *
     * <p>The database-backed 5-second check in the caller cannot stop two submissions that are
     * in flight at the same moment: neither transaction has committed its row yet, so neither
     * sees the other. This in-process key closes that gap for the common case — one instance
     * handling a double-click or a retry of the same request.
     *
     * <p>It is deliberately <strong>not</strong> presented as a durable idempotency guarantee.
     * The key lives in heap memory, so it is void across restarts and across instances of a
     * horizontally-scaled deployment. A real guarantee needs an idempotency key column with a
     * unique index plus client-supplied request keys; that is a schema change and is deferred to
     * a dedicated migration phase.
     */
    private SettlementHistoryResponse recordOnce(String key, Supplier<SettlementHistoryResponse> action) {
        if (!recordingsInFlight.add(key)) {
            throw new IllegalArgumentException("An identical settlement is already being recorded. Please wait for it to finish.");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Release only after the transaction resolves: freeing the key earlier would let the
            // second click start while the first row is still uncommitted and invisible.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    recordingsInFlight.remove(key);
                }
            });
            return action.get();
        }
        try {
            return action.get();
        } finally {
            recordingsInFlight.remove(key);
        }
    }

    private String recordingKey(UUID groupId, UUID fromUserId, UUID toUserId, BigDecimal amount) {
        String scope = groupId == null ? "personal" : groupId.toString();
        return scope + '|' + fromUserId + '|' + toUserId + '|' + amount.toPlainString();
    }

    public List<SettlementHistoryResponse> getHistory(UUID groupId, UUID currentUserId) {
        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, currentUserId);
        return settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public List<SettlementHistoryResponse> getPersonalHistory(UUID currentUserId) {
        return settlementRepository.findPersonalHistoryForUser(currentUserId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public List<SettlementHistoryResponse> getFriendPersonalHistory(UUID currentUserId, UUID friendUserId) {
        authServiceClient.validateAcceptedFriendship(currentUserId, friendUserId);
        return settlementRepository.findPersonalHistoryBetweenUsers(currentUserId, friendUserId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private SettlementHistoryResponse toHistoryResponse(Settlement s) {
        return new SettlementHistoryResponse(
                s.getId(), s.getFromUserId(), s.getToUserId(),
                s.getAmount(), s.getStatus(), s.getNotes(), s.getCreatedAt(), s.getUpdatedAt());
    }

    /**
     * Internal use only.
     * Returns all COMPLETED settlements for the group, used by Balance Service
     * to adjust expense-derived net balances.
     * PENDING settlements are never included.
     */
    public List<InternalCompletedSettlementData> getCompletedSettlements(UUID groupId) {
        return settlementRepository
                .findByGroupIdAndStatus(groupId, SettlementStatus.COMPLETED)
                .stream()
                .map(s -> new InternalCompletedSettlementData(
                        s.getFromUserId(), s.getToUserId(), s.getAmount()))
                .collect(Collectors.toList());
    }

    public List<InternalCompletedSettlementData> getCompletedPersonalSettlements(UUID userId) {
        return settlementRepository
                .findPersonalByUserIdAndStatus(userId, SettlementStatus.COMPLETED)
                .stream()
                .map(s -> new InternalCompletedSettlementData(
                        s.getFromUserId(), s.getToUserId(), s.getAmount()))
                .collect(Collectors.toList());
    }

    private BigDecimal findOutstandingPersonalDebt(UUID fromUserId, UUID toUserId) {
        FriendBalanceResponse response = balanceCalculationService.calculateFriendBalance(fromUserId, toUserId);
        if (response == null || response.getCurrentUserNetBalance() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // If payer (fromUserId) has a negative net balance with recipient (toUserId), payer owes that debt
        if (response.getCurrentUserNetBalance().compareTo(BigDecimal.ZERO) < 0) {
            if (response.getAmount() != null) {
                return money(response.getAmount());
            }
            return money(response.getCurrentUserNetBalance().abs());
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private void sendSettlementNotifications(Settlement settlement) {
        try {
            boolean personal = settlement.getGroupId() == null;
            Map<String, Object> metadata = Map.of(
                    "settlementId", settlement.getId().toString(),
                    "amount", settlement.getAmount().toString(),
                    "fromUserId", settlement.getFromUserId().toString(),
                    "toUserId", settlement.getToUserId().toString(),
                    "personal", personal
            );
            
            // Only send notifications if fromUserId and toUserId are different
            if (!settlement.getFromUserId().equals(settlement.getToUserId())) {
                // Notify the payer (fromUserId)
                CreateNotificationRequest fromNotification = new CreateNotificationRequest(
                        settlement.getFromUserId(),
                        settlement.getGroupId(),
                        personal ? "PERSONAL_SETTLEMENT_RECORDED" : "SETTLEMENT_RECORDED",
                        personal ? "A personal settlement was recorded." : "A settlement was recorded.",
                        metadata
                );
                notificationServiceClient.createNotification(fromNotification);
                
                // Notify the recipient (toUserId)
                CreateNotificationRequest toNotification = new CreateNotificationRequest(
                        settlement.getToUserId(),
                        settlement.getGroupId(),
                        personal ? "PERSONAL_SETTLEMENT_RECORDED" : "SETTLEMENT_RECORDED",
                        personal ? "A personal settlement was recorded." : "A settlement was recorded.",
                        metadata
                );
                notificationServiceClient.createNotification(toNotification);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch settlement notification for settlement {}: {}", settlement.getId(), e.getMessage());
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

}
