package com.hishob.settlement.service;

import com.hishob.settlement.algorithm.DebtSimplificationAlgorithm;
import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.client.NotificationServiceClient;
import com.hishob.settlement.dto.CreateNotificationRequest;
import com.hishob.settlement.dto.FriendBalanceResponse;
import com.hishob.settlement.dto.GroupBalanceResponse;
import com.hishob.settlement.dto.PersonalDebtSimplificationResponse;
import com.hishob.settlement.dto.PersonalDebtSimplificationSuggestion;
import com.hishob.settlement.dto.RecordPersonalSettlementRequest;
import com.hishob.settlement.dto.RecordSettlementRequest;
import com.hishob.settlement.dto.SettlementHistoryResponse;
import com.hishob.settlement.dto.UserBalanceResponse;
import com.hishob.settlement.service.BalanceCalculationService;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.exception.InvalidRecipientException;
import com.hishob.settlement.repository.SettlementRepository;
import com.hishob.settlement.dto.InternalBalanceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private GroupServiceClient groupServiceClient;
    
    @Mock
    private BalanceCalculationService balanceCalculationService;

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
        settlementService = new SettlementService(groupServiceClient, balanceCalculationService, authServiceClient,
                algorithm, settlementRepository, notificationServiceClient);
        groupId = UUID.randomUUID();
        fromUserId = UUID.randomUUID();
        toUserId = UUID.randomUUID();
    }

    private UserBalanceResponse createBalance(UUID userId, BigDecimal netBalance) {
        return new UserBalanceResponse(userId, BigDecimal.ZERO, BigDecimal.ZERO, netBalance, null);
    }

    private GroupBalanceResponse groupBalances(UserBalanceResponse... balances) {
        return new GroupBalanceResponse(groupId, List.of(balances));
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

    private FriendBalanceResponse friendBalance(UUID currentUserId, UUID friendUserId, BigDecimal currentUserNetBalance) {
        FriendBalanceResponse r = new FriendBalanceResponse();
        r.setCurrentUserId(currentUserId);
        r.setFriendUserId(friendUserId);
        r.setCurrentUserNetBalance(currentUserNetBalance);
        r.setFriendNetBalance(currentUserNetBalance.negate());
        if (currentUserNetBalance.compareTo(BigDecimal.ZERO) < 0) {
            r.setCurrentUserStatus("OWE");
            r.setFriendStatus("GET_BACK");
            r.setFromUserId(currentUserId);
            r.setToUserId(friendUserId);
            r.setAmount(currentUserNetBalance.abs());
        } else if (currentUserNetBalance.compareTo(BigDecimal.ZERO) > 0) {
            r.setCurrentUserStatus("GET_BACK");
            r.setFriendStatus("OWE");
            r.setFromUserId(friendUserId);
            r.setToUserId(currentUserId);
            r.setAmount(currentUserNetBalance);
        } else {
            r.setCurrentUserStatus("SETTLED");
            r.setFriendStatus("SETTLED");
            r.setFromUserId(null);
            r.setToUserId(null);
            r.setAmount(BigDecimal.ZERO);
        }
        return r;
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

        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
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
        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
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
        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
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

        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
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
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-150.00")));
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
    @DisplayName("Record personal settlement: payer directly owes ₹333.33 and exact payment of ₹333.33 succeeds")
    void testRecordPersonalSettlement_Owes333_ExactPayment_Success() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("333.33"));
        req.setNotes("Settling exact debt");

        Settlement saved = new Settlement(null, fromUserId, toUserId, new BigDecimal("333.33"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setNotes("Settling exact debt");
        saved.setCreatedAt(Instant.now());

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-333.33")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        SettlementHistoryResponse resp = settlementService.recordPersonalSettlement(fromUserId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getAmount()).isEqualByComparingTo("333.33");
        assertThat(resp.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        verify(settlementRepository).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record personal settlement: payer directly owes ₹333.33 and partial payment of ₹100.00 succeeds")
    void testRecordPersonalSettlement_Owes333_PartialPayment_Success() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));
        req.setNotes("Partial payment");

        Settlement saved = new Settlement(null, fromUserId, toUserId, new BigDecimal("100.00"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-333.33")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        SettlementHistoryResponse resp = settlementService.recordPersonalSettlement(fromUserId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getAmount()).isEqualByComparingTo("100.00");
        verify(settlementRepository).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record personal settlement: payer owes friend even though global debt simplification does not suggest paying this friend")
    void testRecordPersonalSettlement_PayerOwesFriendEvenThoughGlobalSimplificationDoesNotSuggest() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("333.33"));

        Settlement saved = new Settlement(null, fromUserId, toUserId, new BigDecimal("333.33"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        // Direct friend balance shows debt of 333.33 to toUserId
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-333.33")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        SettlementHistoryResponse resp = settlementService.recordPersonalSettlement(fromUserId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getAmount()).isEqualByComparingTo("333.33");
        // Global simplification is never consulted for pairwise friend settlement
        verify(balanceCalculationService, never()).simplifyPersonalBalances(any());
    }

    @Test
    @DisplayName("Record personal settlement: overpayment exceeding debt of ₹333.33 is rejected")
    void testRecordPersonalSettlement_Overpayment_Rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("333.34"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-333.33")));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds payer's outstanding personal debt of 333.33");

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Record personal settlement: payer and recipient cannot be the same user (ID manipulation)")
    void testRecordPersonalSettlement_PayerEqualsRecipient_Rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(fromUserId); // manipulating toUserId to be self
        req.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(InvalidRecipientException.class)
                .hasMessageContaining("Payer and recipient cannot be the same user");

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(balanceCalculationService, never()).calculateFriendBalance(any(), any());
    }

    @Test
    @DisplayName("Record personal settlement: payer is creditor (friend owes payer) -> rejected")
    void testRecordPersonalSettlement_PayerIsCreditor_Rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("50.00"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        // Positive net balance means toUserId owes fromUserId, fromUserId owes nothing
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("100.00")));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payer has no outstanding personal debt to this recipient");

        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record personal settlement: amount exceeding recipient-specific debt rejected")
    void recordPersonalSettlement_exceedsRecipientSpecificDebt_rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("200.00"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-150.00")));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds payer's outstanding personal debt");

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Record personal settlement: wrong recipient rejected when payer has no debt with that recipient")
    void recordPersonalSettlement_wrongRecipient_rejected() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, BigDecimal.ZERO));

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
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-100.00")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.of(new Settlement(null, fromUserId, toUserId, new BigDecimal("50.00"), SettlementStatus.COMPLETED)));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(fromUserId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same details was just recorded");

        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record settlement: notification failure does not break primary settlement operation")
    void testRecordSettlement_NotificationFailure_SettlementSucceeds() {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));

        Settlement saved = new Settlement(groupId, fromUserId, toUserId, new BigDecimal("100.00"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);

        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
                createBalance(fromUserId, new BigDecimal("-150.00")),
                createBalance(toUserId, new BigDecimal("150.00"))
        ));
        when(settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        doThrow(new RuntimeException("Notification service unreachable"))
                .when(notificationServiceClient).createNotification(any());

        SettlementHistoryResponse resp = settlementService.recordSettlement(groupId, fromUserId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(saved.getId());
        assertThat(resp.getAmount()).isEqualByComparingTo("100.00");
        verify(settlementRepository).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record personal settlement: notification failure does not break primary settlement operation")
    void testRecordPersonalSettlement_NotificationFailure_SettlementSucceeds() {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("50.00"));

        Settlement saved = new Settlement(null, fromUserId, toUserId, new BigDecimal("50.00"), SettlementStatus.COMPLETED);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());

        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-100.00")));
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

        doThrow(new RuntimeException("Notification service network failure"))
                .when(notificationServiceClient).createNotification(any());

        SettlementHistoryResponse resp = settlementService.recordPersonalSettlement(fromUserId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(saved.getId());
        assertThat(resp.getAmount()).isEqualByComparingTo("50.00");
        verify(settlementRepository).save(any(Settlement.class));
    }

    // ── #139 group settlement notes + duplicate-submit guard ──────────────────

    @Test
    @DisplayName("Record group settlement: notes from the request are stored on the settlement and echoed back")
    void testRecordSettlement_NotesArePersistedWithTheSettlement() {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));
        req.setNotes("Cash given at the office");

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);
        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
                createBalance(fromUserId, new BigDecimal("-150.00")),
                createBalance(toUserId, new BigDecimal("150.00"))
        ));
        when(settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        SettlementHistoryResponse resp = settlementService.recordSettlement(groupId, fromUserId, req);

        // The group path used to have no notes field on RecordSettlementRequest, so Jackson
        // silently dropped the value the UI sent.
        assertThat(resp.getNotes()).isEqualTo("Cash given at the office");
        verify(settlementRepository).save(argThat(settlement ->
                "Cash given at the office".equals(settlement.getNotes())));
    }

    @Test
    @DisplayName("Record group settlement: identical request already in flight is rejected before a second row is saved")
    void testRecordSettlement_IdenticalRequestInFlight_SecondRejected() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);
        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
                createBalance(fromUserId, new BigDecimal("-150.00")),
                createBalance(toUserId, new BigDecimal("150.00"))
        ));
        // Both requests see an empty database: the first row is still uncommitted, so the
        // 5-second query cannot be the thing that stops the retry.
        when(settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());

        List<String> retriedOutcome = new ArrayList<>();
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            retriedOutcome.add(retryGroupSettlement());
            return toSave;
        });

        settlementService.recordSettlement(groupId, fromUserId, groupSettlementRequest("100.00"));

        assertThat(retriedOutcome).singleElement()
                .asString().contains("already being recorded");
        verify(settlementRepository, times(1)).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Record personal settlement: identical request already in flight is rejected before a second row is saved")
    void testRecordPersonalSettlement_IdenticalRequestInFlight_SecondRejected() {
        doNothing().when(authServiceClient).validateAcceptedFriendship(fromUserId, toUserId);
        when(balanceCalculationService.calculateFriendBalance(fromUserId, toUserId))
                .thenReturn(friendBalance(fromUserId, toUserId, new BigDecimal("-150.00")));
        when(settlementRepository
                .findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        List<String> retriedOutcome = new ArrayList<>();
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            RecordPersonalSettlementRequest retry = new RecordPersonalSettlementRequest();
            retry.setToUserId(toUserId);
            retry.setAmount(new BigDecimal("100.00"));
            try {
                settlementService.recordPersonalSettlement(fromUserId, retry);
                retriedOutcome.add("accepted");
            } catch (IllegalArgumentException e) {
                retriedOutcome.add(e.getMessage());
            }
            return toSave;
        });

        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal("100.00"));
        settlementService.recordPersonalSettlement(fromUserId, req);

        assertThat(retriedOutcome).singleElement()
                .asString().contains("already being recorded");
        verify(settlementRepository, times(1)).save(any(Settlement.class));
    }

    @Test
    @DisplayName("In-flight guard: held while the transaction is open, released once it completes")
    void testRecordSettlement_InFlightKeyReleasedOnlyAfterTransactionCompletion() {
        RecordSettlementRequest req = groupSettlementRequest("100.00");

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);
        when(balanceCalculationService.calculateGroupBalancesInternal(groupId)).thenReturn(groupBalances(
                createBalance(fromUserId, new BigDecimal("-150.00")),
                createBalance(toUserId, new BigDecimal("150.00"))
        ));
        when(settlementRepository.findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        AtomicInteger saveCount = new AtomicInteger();
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            saveCount.incrementAndGet();
            return toSave;
        });

        // Stands in for the synchronization Spring's transaction manager installs around
        // SettlementService.recordSettlement.
        TransactionSynchronizationManager.initSynchronization();
        try {
            settlementService.recordSettlement(groupId, fromUserId, req);
            assertThat(saveCount).hasValue(1);

            assertThatThrownBy(() -> settlementService.recordSettlement(groupId, fromUserId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already being recorded");
            assertThat(saveCount).hasValue(1);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }

            // The first row is committed and visible to the 5-second guard by now, so the in-flight
            // key must no longer block a genuinely new recording.
            settlementService.recordSettlement(groupId, fromUserId, req);
            assertThat(saveCount).hasValue(2);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private RecordSettlementRequest groupSettlementRequest(String amount) {
        RecordSettlementRequest req = new RecordSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    private String retryGroupSettlement() {
        try {
            settlementService.recordSettlement(groupId, fromUserId, groupSettlementRequest("100.00"));
            return "accepted";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

}
