package com.hishob.settlement.service;

import com.hishob.settlement.algorithm.DebtSimplificationAlgorithm;
import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.ExpenseServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.client.NotificationServiceClient;
import com.hishob.settlement.dto.BalanceStatus;
import com.hishob.settlement.dto.FriendBalanceResponse;
import com.hishob.settlement.dto.InternalExpenseData;
import com.hishob.settlement.dto.InternalSplitData;
import com.hishob.settlement.dto.PersonalBalanceResponse;
import com.hishob.settlement.dto.PersonalBalanceSummaryResponse;
import com.hishob.settlement.dto.RecordPersonalSettlementRequest;
import com.hishob.settlement.dto.SettlementHistoryResponse;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 21B-2. The amount a personal balance surface offers to settle and the amount the backend
 * accepts must be one number: the pairwise standing between the current user and that friend.
 *
 * The real BalanceCalculationService is wired into the real SettlementService here, so the card
 * figure and the validation cap are exercised through the same rule. Nothing is persisted — the
 * settlement repository is a mock.
 */
@ExtendWith(MockitoExtension.class)
class PersonalSettlementAmountConsistencyTest {

    @Mock
    private GroupServiceClient groupServiceClient;

    @Mock
    private ExpenseServiceClient expenseServiceClient;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    private BalanceCalculationService balanceCalculationService;
    private SettlementService settlementService;

    private UUID me;
    private UUID friend;
    private UUID stranger;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        balanceCalculationService = new BalanceCalculationService(
                groupServiceClient, expenseServiceClient, settlementRepository, authServiceClient);
        settlementService = new SettlementService(groupServiceClient, balanceCalculationService,
                authServiceClient, new DebtSimplificationAlgorithm(), settlementRepository, notificationServiceClient);

        me = UUID.randomUUID();
        friend = UUID.randomUUID();
        stranger = UUID.randomUUID();
        groupId = UUID.randomUUID();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private InternalSplitData split(UUID userId, String amount) {
        InternalSplitData s = new InternalSplitData();
        s.setUserId(userId);
        s.setAmount(new BigDecimal(amount));
        return s;
    }

    private InternalExpenseData personalExpense(UUID paidBy, String total, InternalSplitData... splits) {
        InternalExpenseData e = new InternalExpenseData();
        e.setExpenseId(UUID.randomUUID());
        e.setGroupId(null);
        e.setPaidBy(paidBy);
        e.setTotalAmount(new BigDecimal(total));
        e.setSplits(List.of(splits));
        return e;
    }

    private Settlement personalSettlement(UUID fromUserId, UUID toUserId, String amount) {
        return new Settlement(null, fromUserId, toUserId, new BigDecimal(amount), SettlementStatus.COMPLETED);
    }

    private RecordPersonalSettlementRequest request(UUID toUserId, String amount) {
        RecordPersonalSettlementRequest req = new RecordPersonalSettlementRequest();
        req.setToUserId(toUserId);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    private void allowRecording() {
        when(settlementRepository.findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
    }

    /**
     * Two three-way expenses. The friend fronts 500.00 of my share and I front 166.67 of theirs,
     * so I owe them 333.33 — while their position across the whole graph is 833.33, because a
     * stranger also owes them 500.00.
     */
    private void stubThreePartyGraph() {
        when(expenseServiceClient.getPersonalExpensesForUser(me)).thenReturn(List.of(
                personalExpense(me, "500.00",
                        split(me, "166.67"), split(friend, "166.67"), split(stranger, "166.66")),
                personalExpense(friend, "1500.00",
                        split(me, "500.00"), split(friend, "500.00"), split(stranger, "500.00"))
        ));
    }

    private PersonalBalanceSummaryResponse summary() {
        return balanceCalculationService.calculatePersonalBalances(me);
    }

    private PersonalBalanceResponse row(PersonalBalanceSummaryResponse summary, UUID userId) {
        return summary.getBalances().stream()
                .filter(b -> b.getUserId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Friend card shows the pairwise debt, not the friend's graph-wide position")
    void friendRowIsPairwise() {
        stubThreePartyGraph();

        PersonalBalanceSummaryResponse summary = summary();

        assertThat(row(summary, friend).getNetBalance()).isEqualByComparingTo("333.33");
        assertThat(row(summary, friend).getStatus()).isEqualTo(BalanceStatus.GETS);
        // the stranger's 500.00 is owed to the friend, not to me
        assertThat(row(summary, stranger).getNetBalance()).isEqualByComparingTo("-166.66");
        assertThat(row(summary, me).getNetBalance()).isEqualByComparingTo("-166.67");

        assertThat(summary.getBalances().stream()
                .map(PersonalBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("The amount shown on the friend card is accepted by settlement validation")
    void shownAmountIsAccepted() {
        stubThreePartyGraph();
        allowRecording();

        BigDecimal shown = row(summary(), friend).getNetBalance();
        assertThat(shown).isEqualByComparingTo("333.33");

        SettlementHistoryResponse recorded = settlementService.recordPersonalSettlement(me, request(friend, "333.33"));

        assertThat(recorded.getAmount()).isEqualByComparingTo(shown);
        assertThat(recorded.getFromUserId()).isEqualTo(me);
        assertThat(recorded.getToUserId()).isEqualTo(friend);
        verify(settlementRepository).save(any(Settlement.class));
    }

    @Test
    @DisplayName("One paisa more than the shown amount is rejected — the card cannot over-offer")
    void shownAmountPlusOnePaisaIsRejected() {
        stubThreePartyGraph();

        BigDecimal overOffer = row(summary(), friend).getNetBalance().add(new BigDecimal("0.01"));

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(me, request(friend, overOffer.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds payer's outstanding personal debt of 333.33");
        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    @Test
    @DisplayName("Partial settlement: the card drops to the remainder and that remainder is accepted")
    void partialSettlementRemainsConsistent() {
        stubThreePartyGraph();
        when(settlementRepository.findPersonalByUserIdAndStatus(me, SettlementStatus.COMPLETED))
                .thenReturn(List.of(personalSettlement(me, friend, "100.00")));
        allowRecording();

        PersonalBalanceSummaryResponse summary = summary();

        assertThat(row(summary, friend).getNetBalance()).isEqualByComparingTo("233.33");
        assertThat(row(summary, me).getNetBalance()).isEqualByComparingTo("-66.67");

        FriendBalanceResponse pair = balanceCalculationService.calculateFriendBalance(me, friend);
        assertThat(pair.getAmount()).isEqualByComparingTo(row(summary, friend).getNetBalance());

        SettlementHistoryResponse recorded = settlementService.recordPersonalSettlement(me, request(friend, "233.33"));
        assertThat(recorded.getAmount()).isEqualByComparingTo("233.33");
    }

    @Test
    @DisplayName("Zero debt: a fully settled friend row offers nothing and any amount is rejected")
    void zeroDebtRejectsSettlement() {
        stubThreePartyGraph();
        when(settlementRepository.findPersonalByUserIdAndStatus(me, SettlementStatus.COMPLETED))
                .thenReturn(List.of(personalSettlement(me, friend, "333.33")));

        PersonalBalanceSummaryResponse summary = summary();

        assertThat(row(summary, friend).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(row(summary, friend).getStatus()).isEqualTo(BalanceStatus.SETTLED);
        assertThat(row(summary, me).getNetBalance()).isEqualByComparingTo("166.66");

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(me, request(friend, "10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no outstanding personal debt");
    }

    @Test
    @DisplayName("Reversed direction: when the friend owes me, the card is a debit and I cannot settle it")
    void reversedDirectionKeepsMirrorImages() {
        when(expenseServiceClient.getPersonalExpensesForUser(me)).thenReturn(List.of(
                personalExpense(me, "1000.00", split(me, "500.00"), split(friend, "500.00"))));

        PersonalBalanceSummaryResponse summary = summary();

        assertThat(row(summary, friend).getNetBalance()).isEqualByComparingTo("-500.00");
        assertThat(row(summary, me).getNetBalance()).isEqualByComparingTo("500.00");

        FriendBalanceResponse pair = balanceCalculationService.calculateFriendBalance(me, friend);
        assertThat(pair.getCurrentUserNetBalance()).isEqualByComparingTo("500.00");
        assertThat(pair.getFriendNetBalance()).isEqualByComparingTo("-500.00");
        assertThat(pair.getFromUserId()).isEqualTo(friend);
        assertThat(pair.getToUserId()).isEqualTo(me);
        assertThat(pair.getAmount()).isEqualByComparingTo("500.00");
        assertThat(row(summary, friend).getNetBalance()).isEqualByComparingTo(pair.getAmount().negate());

        assertThatThrownBy(() -> settlementService.recordPersonalSettlement(me, request(friend, "500.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no outstanding personal debt");
    }

    @Test
    @DisplayName("Group expenses never enter a personal balance or a personal settlement")
    void groupExpensesStayOutOfPersonalBalances() {
        InternalExpenseData groupExpense = personalExpense(me, "1000.00", split(me, "500.00"), split(friend, "500.00"));
        groupExpense.setGroupId(groupId);
        when(expenseServiceClient.getPersonalExpensesForUser(me)).thenReturn(List.of(groupExpense));

        PersonalBalanceSummaryResponse summary = summary();
        FriendBalanceResponse pair = balanceCalculationService.calculateFriendBalance(me, friend);

        assertThat(summary.getBalances()).isEmpty();
        assertThat(pair.getAmount()).isEqualByComparingTo("0.00");
        assertThat(pair.getFromUserId()).isNull();
        assertThat(pair.getToUserId()).isNull();
    }
}
