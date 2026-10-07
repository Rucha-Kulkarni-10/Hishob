package com.hishob.settlement.service;

import com.hishob.settlement.algorithm.DebtSimplificationAlgorithm;
import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.BalanceServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.client.NotificationServiceClient;
import com.hishob.settlement.dto.*;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.exception.InvalidRecipientException;
import com.hishob.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SettlementService {

    private final GroupServiceClient groupServiceClient;
    private final BalanceServiceClient balanceServiceClient;
    private final AuthServiceClient authServiceClient;
    private final DebtSimplificationAlgorithm algorithm;
    private final SettlementRepository settlementRepository;
    private final NotificationServiceClient notificationServiceClient;

    public SettlementService(GroupServiceClient groupServiceClient,
                             BalanceServiceClient balanceServiceClient,
                             AuthServiceClient authServiceClient,
                             DebtSimplificationAlgorithm algorithm,
                             SettlementRepository settlementRepository,
                             NotificationServiceClient notificationServiceClient) {
        this.groupServiceClient = groupServiceClient;
        this.balanceServiceClient = balanceServiceClient;
        this.authServiceClient = authServiceClient;
        this.algorithm = algorithm;
        this.settlementRepository = settlementRepository;
        this.notificationServiceClient = notificationServiceClient;
    }

    public List<SettlementSuggestion> getSuggestions(UUID groupId, UUID currentUserId) {
        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, currentUserId);
        List<InternalBalanceData> balances = balanceServiceClient.getGroupBalances(groupId);
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

        // Pre-settlement validation: check payer's outstanding debt via Balance Service
        List<InternalBalanceData> balances = balanceServiceClient.getGroupBalances(groupId);
        InternalBalanceData payerBalance = balances.stream()
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

        // Duplicate submission prevention: reject identical settlement recorded in the last 5 seconds
        boolean isDuplicate = settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                groupId, fromUserId, toUserId, amount, Instant.now().minusSeconds(5)
        ).isPresent();
        if (isDuplicate) {
            throw new IllegalArgumentException("A settlement with the same details was just recorded. Please wait a moment.");
        }

        Settlement settlement = new Settlement(groupId, fromUserId, toUserId, amount, SettlementStatus.COMPLETED);
        settlement = settlementRepository.save(settlement);
        
        // Send notifications to both users after successful settlement recording
        sendSettlementNotifications(settlement);
        
        return toHistoryResponse(settlement);
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

        boolean isDuplicate = settlementRepository
                .findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                        fromUserId, toUserId, amount, Instant.now().minusSeconds(5))
                .isPresent();
        if (isDuplicate) {
            throw new IllegalArgumentException("A settlement with the same details was just recorded. Please wait a moment.");
        }

        Settlement settlement = new Settlement(null, fromUserId, toUserId, amount, SettlementStatus.COMPLETED);
        settlement.setNotes(request.getNotes());
        settlement = settlementRepository.save(settlement);

        sendSettlementNotifications(settlement);

        return toHistoryResponse(settlement);
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
        PersonalDebtSimplificationResponse response = balanceServiceClient.getPersonalSimplification(fromUserId);
        if (response.getSuggestions() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return response.getSuggestions().stream()
                .filter(s -> fromUserId.equals(s.getFromUserId()) && toUserId.equals(s.getToUserId()))
                .map(PersonalDebtSimplificationSuggestion::getAmount)
                .filter(a -> a != null)
                .map(this::money)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private void sendSettlementNotifications(Settlement settlement) {
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
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

}
