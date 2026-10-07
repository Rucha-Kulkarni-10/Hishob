package com.hishob.settlement.service;

import com.hishob.settlement.algorithm.DebtSimplificationAlgorithm;
import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.BalanceServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.client.NotificationServiceClient;
import com.hishob.settlement.dto.CreateNotificationRequest;
import com.hishob.settlement.dto.PersonalDebtSimplificationResponse;
import com.hishob.settlement.dto.PersonalDebtSimplificationSuggestion;
import com.hishob.settlement.dto.RecordPersonalSettlementRequest;
import com.hishob.settlement.dto.RecordSettlementRequest;
import com.hishob.settlement.dto.SettlementHistoryResponse;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.repository.SettlementRepository;
import com.hishob.settlement.dto.InternalBalanceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private GroupServiceClient groupServiceClient;
    
    @Mock
    private BalanceServiceClient balanceServiceClient;

    @Mock
    private AuthServiceClient authServiceClient;
    
    @Mock
    private DebtSimplificationAlgorithm algorithm;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    private SettlementService settlementService;

    private UUID groupId;
    private UUID fromUserId;
    private UUID toUserId;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(groupServiceClient, balanceServiceClient, authServiceClient,
                algorithm, settlementRepository, notificationServiceClient);
        groupId = UUID.randomUUID();
        fromUserId = UUID.randomUUID();
        toUserId = UUID.randomUUID();
    }

    private InternalBalanceData createBalance(UUID userId, BigDecimal netBalance) {
        InternalBalanceData b = new InternalBalanceData();
        b.setUserId(userId);
        b.setNetBalance(netBalance);
        return b;
    }

    private PersonalDebtSimplificationResponse personalSimplification(PersonalDebtSimplificationSuggestion... suggestions) {
        PersonalDebtSimplificationResponse response = new PersonalDebtSimplificationResponse();
        response.setUserId(fromUserId);
        response.setSuggestions(List.of(suggestions));
        return response;
    }

    private PersonalDebtSimplificationSuggestion personalSuggestion(UUID from, UUID to, String amount) {
        PersonalDebtSimplificationSuggestion suggestion = new PersonalDebtSimplificationSuggestion();
        suggestion.setFromUserId(from);
        suggestion.setToUserId(to);
        suggestion.setAmount(new BigDecimal(amount));
        return suggestion;
    }

    @Test
    @DisplayName("Record settlement: successful settlement within outstanding debt sends notifications")
    void testRecordSettlement_Success() {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));

        Settlement saved = new Settlement(groupId, fromUserId, toUserId, new BigDecimal("100.00"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);

        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                createBalance(fromUserId, new BigDecimal("-150.00")),
                createBalance(toUserId, new BigDecimal("150.00"))
        ));
        when(settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        SettlementHistoryResponse resp = settlementService.recordSettlement(groupId, fromUserId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getFromUserId()).isEqualTo(fromUserId);
        assertThat(resp.getToUserId()).isEqualTo(toUserId);
        assertThat(resp.getAmount()).isEqualByComparingTo("100.00");
        
        verify(settlementRepository).save(any(Settlement.class));
        
        // Verify notifications are sent (2 calls expected for both users)
        verify(notificationServiceClient, org.mockito.Mockito.times(2))
                .createNotification(argThat(notification ->
                        notification.getMessage().equals("A settlement was recorded.")
                                && notification.getType().equals("SETTLEMENT_RECORDED")
                                && notification.getMetadata().containsKey("settlementId")
                                && notification.getMetadata().containsKey("amount")));
    }

    @Test
    @DisplayName("Record settlement: amount exceeds outstanding debt -> rejected, no DB save, no notification")
    void testRecordSettlement_ExceedsOutstandingDebt_ThrowsException() {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("200.00"));

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);

        // Payer only owes 100.00
        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                createBalance(fromUserId, new BigDecimal("-100.00")),
                createBalance(toUserId, new BigDecimal("100.00"))
        ));

        assertThatThrownBy(() -> settlementService.recordSettlement(groupId, fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds payer's outstanding debt");

        // Verify DB save was NOT called
        verify(settlementRepository, never()).save(any(Settlement.class));

        // Verify notification was NOT sent
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Record settlement: payer is not a debtor -> rejected")
    void testRecordSettlement_PayerNotDebtor_ThrowsException() {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("50.00"));

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);

        // Payer has 0.00 debt
        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                createBalance(fromUserId, BigDecimal.ZERO),
                createBalance(toUserId, BigDecimal.ZERO)
        ));

        assertThatThrownBy(() -> settlementService.recordSettlement(groupId, fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payer has no outstanding debt");

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Record settlement: duplicate submission within 5 seconds -> rejected")
    void testRecordSettlement_DuplicateSubmission_ThrowsException() {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("50.00"));

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);

        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                createBalance(fromUserId, new BigDecimal("-100.00")),
                createBalance(toUserId, new BigDecimal("100.00"))
        ));

        Settlement existing = new Settlement(groupId, fromUserId, toUserId, new BigDecimal("50.00"), SettlementStatus.COMPLETED);
        when(settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> settlementService.recordSettlement(groupId, fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A settlement with the same details was just recorded");

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Record personal settlement: valid debt records groupId null, completed status, notes, and notifications")
    void recordPersonalSettlement_success() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));
        req.setNotes("Paid via UPI");

        Settlement saved = new Settlement(null, fromUserId, toUserId, new BigDecimal("100.00"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setNotes("Paid via UPI");
        saved.setCreatedAt(Instant.now());

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceServiceClient.getPersonalSimplification(fromUserId))
                .thenReturn(personalSimplification(personalSuggestion(fromUserId, toUserId, "150.00")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        SettlementHistoryResponse resp = settlementService.recordPersonalSettlement(fromUserId, req);

        assertThat(resp.getFromUserId()).isEqualTo(fromUserId);
        assertThat(resp.getToUserId()).isEqualTo(toUserId);
        assertThat(resp.getAmount()).isEqualByComparingTo("100.00");
        assertThat(resp.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(resp.getNotes()).isEqualTo("Paid via UPI");

        verify(settlementRepository).save(argThat(settlement ->
                settlement.getGroupId() == null
                        && settlement.getFromUserId().equals(fromUserId)
                        && settlement.getToUserId().equals(toUserId)
                        && settlement.getAmount().compareTo(new BigDecimal("100.00")) == 0
                        && settlement.getStatus() == SettlementStatus.COMPLETED
                        && settlement.getNotes().equals("Paid via UPI")));
        verify(notificationServiceClient, org.mockito.Mockito.times(2))
                .createNotification(argThat(notification ->
                        notification.getGroupId() == null
                                && notification.getType().equals("PERSONAL_SETTLEMENT_RECORDED")
                                && notification.getMessage().equals("A personal settlement was recorded.")
                                && Boolean.TRUE.equals(notification.getMetadata().get("personal"))));
    }

    @Test
    @DisplayName("Record personal settlement: amount exceeding recipient-specific debt rejected")
    void recordPersonalSettlement_exceedsRecipientSpecificDebt_rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("200.00"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceServiceClient.getPersonalSimplification(fromUserId))
                .thenReturn(personalSimplification(personalSuggestion(fromUserId, toUserId, "150.00")));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds payer's outstanding personal debt");

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Record personal settlement: wrong recipient rejected even when payer owes someone else")
    void recordPersonalSettlement_wrongRecipient_rejected() {
        UUID otherRecipient = UUID.randomUUID();
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceServiceClient.getPersonalSimplification(fromUserId))
                .thenReturn(personalSimplification(personalSuggestion(fromUserId, otherRecipient, "100.00")));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no outstanding personal debt");

        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record personal settlement: rapid duplicate rejected")
    void recordPersonalSettlement_duplicate_rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("50.00"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceServiceClient.getPersonalSimplification(fromUserId))
                .thenReturn(personalSimplification(personalSuggestion(fromUserId, toUserId, "100.00")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.of(new Settlement(null, fromUserId, toUserId, new BigDecimal("50.00"), SettlementStatus.COMPLETED)));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same details was just recorded");

        verify(settlementRepository, never()).save(any(Settlement.class));
    }

}
