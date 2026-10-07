package com.hishob.balance.service;

import com.hishob.balance.client.AuthServiceClient;
import com.hishob.balance.client.ExpenseServiceClient;
import com.hishob.balance.client.GroupServiceClient;
import com.hishob.balance.client.SettlementServiceClient;
import com.hishob.balance.dto.*;
import com.hishob.balance.exception.BalanceCalculationException;
import com.hishob.balance.exception.GroupNotFoundException;
import com.hishob.balance.exception.PermissionDeniedException;
import com.hishob.balance.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceCalculationServiceTest {

    @Mock
    private GroupServiceClient groupServiceClient;

    @Mock
    private ExpenseServiceClient expenseServiceClient;

    @Mock
    private SettlementServiceClient settlementServiceClient;

    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private BalanceCalculationService service;

    private UUID groupId;
    private UUID userA;
    private UUID userB;
    private UUID userC;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private InternalSplitData split(UUID userId, String amount) {
        InternalSplitData s = new InternalSplitData();
        s.setUserId(userId);
        s.setAmount(new BigDecimal(amount));
        return s;
    }

    private InternalExpenseData expense(UUID paidBy, String total, InternalSplitData... splits) {
        InternalExpenseData e = new InternalExpenseData();
        e.setExpenseId(UUID.randomUUID());
        e.setGroupId(groupId);
        e.setPaidBy(paidBy);
        e.setTotalAmount(new BigDecimal(total));
        e.setSplits(List.of(splits));
        return e;
    }

    private InternalExpenseData personalExpense(UUID paidBy, String total, InternalSplitData... splits) {
        InternalExpenseData e = expense(paidBy, total, splits);
        e.setGroupId(null);
        return e;
    }

    private CompletedSettlementData settlement(UUID fromUserId, UUID toUserId, String amount) {
        CompletedSettlementData s = new CompletedSettlementData();
        s.setFromUserId(fromUserId);
        s.setToUserId(toUserId);
        s.setAmount(new BigDecimal(amount));
        return s;
    }

    /** Stubs happy path with group/member validation, expenses, and settlements. */
    private void stubGroup(List<UUID> members,
                           List<InternalExpenseData> expenses,
                           List<CompletedSettlementData> settlements) {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, userA);
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(members);
        when(expenseServiceClient.getGroupExpenses(groupId)).thenReturn(expenses);
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(settlements);
    }

    private UserBalanceResponse findBalance(GroupBalanceResponse resp, UUID userId) {
        return resp.getBalances().stream()
                .filter(b -> b.getUserId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    private PersonalBalanceResponse findPersonalBalance(PersonalBalanceSummaryResponse resp, UUID userId) {
        return resp.getBalances().stream()
                .filter(b -> b.getUserId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXISTING TESTS — behaviour unchanged when settlements list is empty
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Single expense: A pays 1000, A=500 B=500 → A GETS +500, B OWES -500")
    void singleExpense_twoUsers() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        UserBalanceResponse a = findBalance(resp, userA);
        UserBalanceResponse b = findBalance(resp, userB);

        assertThat(a.getTotalPaid()).isEqualByComparingTo("1000.00");
        assertThat(a.getTotalShare()).isEqualByComparingTo("500.00");
        assertThat(a.getNetBalance()).isEqualByComparingTo("500.00");
        assertThat(a.getStatus()).isEqualTo(BalanceStatus.GETS);

        assertThat(b.getTotalPaid()).isEqualByComparingTo("0.00");
        assertThat(b.getTotalShare()).isEqualByComparingTo("500.00");
        assertThat(b.getNetBalance()).isEqualByComparingTo("-500.00");
        assertThat(b.getStatus()).isEqualTo(BalanceStatus.OWES);
    }

    @Test
    @DisplayName("Multiple expenses: A pays 1000, B pays 600 → A +200, B -200")
    void multipleExpenses() {
        stubGroup(List.of(userA, userB), List.of(
                expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00")),
                expense(userB, "600.00", split(userA, "300.00"), split(userB, "300.00"))
        ), List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("200.00");
        assertThat(findBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.GETS);
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-200.00");
        assertThat(findBalance(resp, userB).getStatus()).isEqualTo(BalanceStatus.OWES);
    }

    @Test
    @DisplayName("Three users: A pays 900, 3-way split → A +600, B -300, C -300")
    void threeUsers() {
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "900.00",
                        split(userA, "300.00"), split(userB, "300.00"), split(userC, "300.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("600.00");
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-300.00");
        assertThat(findBalance(resp, userC).getNetBalance()).isEqualByComparingTo("-300.00");
    }

    @Test
    @DisplayName("No expenses: all members SETTLED with zero balances")
    void noExpenses_allSettled() {
        stubGroup(List.of(userA, userB, userC), List.of(), List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(resp.getBalances()).hasSize(3);
        resp.getBalances().forEach(b -> {
            assertThat(b.getNetBalance()).isEqualByComparingTo("0.00");
            assertThat(b.getStatus()).isEqualTo(BalanceStatus.SETTLED);
        });
    }

    @Test
    @DisplayName("Group member C has no expenses → C appears with zero balance SETTLED")
    void memberWithNoExpenses_appearsSettled() {
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "100.00", split(userA, "50.00"), split(userB, "50.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userC).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findBalance(resp, userC).getStatus()).isEqualTo(BalanceStatus.SETTLED);
    }

    @Test
    @DisplayName("Self-payment: A pays 500, A share = 500 → A net = 0, SETTLED")
    void selfPayment_netZero() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "500.00", split(userA, "500.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.SETTLED);
    }

    @Test
    @DisplayName("Multiple payers — conservation holds (no settlements)")
    void multiplePayers_conservationHolds() {
        stubGroup(List.of(userA, userB, userC), List.of(
                expense(userA, "1000.00", split(userA, "400.00"), split(userB, "300.00"), split(userC, "300.00")),
                expense(userB, "500.00",  split(userA, "200.00"), split(userB, "150.00"), split(userC, "150.00")),
                expense(userC, "300.00",  split(userA, "100.00"), split(userB, "100.00"), split(userC, "100.00"))
        ), List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        BigDecimal sum = resp.getBalances().stream()
                .map(UserBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Rounding: stored splits used as-is, not recalculated")
    void roundingPreservation() {
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "100.00",
                        split(userA, "33.34"), split(userB, "33.33"), split(userC, "33.33"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("66.66");
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-33.33");
        assertThat(findBalance(resp, userC).getNetBalance()).isEqualByComparingTo("-33.33");
    }

    @Test
    @DisplayName("BigDecimal: no floating-point errors in aggregation")
    void bigDecimalPrecision() {
        stubGroup(List.of(userA, userB), List.of(
                expense(userA, "0.10", split(userA, "0.05"), split(userB, "0.05")),
                expense(userA, "0.10", split(userA, "0.05"), split(userB, "0.05")),
                expense(userA, "0.10", split(userA, "0.05"), split(userB, "0.05"))
        ), List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getTotalPaid()).isEqualByComparingTo("0.30");
        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("0.15");
    }

    @Test
    @DisplayName("Equal split amounts used directly")
    void equalSplitAmounts() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "100.00", split(userA, "50.00"), split(userB, "50.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("50.00");
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("SETTLED status when net balance is exactly zero via compareTo")
    void settledStatus_exactZero() {
        stubGroup(List.of(userA),
                List.of(expense(userA, "100.00", split(userA, "100.00"))),
                List.of());

        assertThat(findBalance(service.calculateGroupBalances(groupId, userA), userA).getStatus())
                .isEqualTo(BalanceStatus.SETTLED);
    }

    @Test
    @DisplayName("GETS status when net balance is positive")
    void getsStatus_positive() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "200.00", split(userA, "100.00"), split(userB, "100.00"))),
                List.of());

        assertThat(findBalance(service.calculateGroupBalances(groupId, userA), userA).getStatus())
                .isEqualTo(BalanceStatus.GETS);
    }

    @Test
    @DisplayName("OWES status when net balance is negative")
    void owesStatus_negative() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "200.00", split(userA, "100.00"), split(userB, "100.00"))),
                List.of());

        assertThat(findBalance(service.calculateGroupBalances(groupId, userA), userB).getStatus())
                .isEqualTo(BalanceStatus.OWES);
    }

    @Test
    @DisplayName("Group not found → GroupNotFoundException propagated")
    void groupNotFound_throws() {
        doThrow(new GroupNotFoundException("Group not found: " + groupId))
                .when(groupServiceClient).validateGroupExists(groupId);

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    @DisplayName("Non-member → PermissionDeniedException propagated")
    void nonMember_throws() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("User is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, userA);

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("Expense Service unavailable → ServiceUnavailableException propagated")
    void expenseServiceUnavailable_throws() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, userA);
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(userA, userB));
        doThrow(new ServiceUnavailableException("Expense Service unavailable"))
                .when(expenseServiceClient).getGroupExpenses(groupId);

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("Group Service unavailable → ServiceUnavailableException propagated")
    void groupServiceUnavailable_throws() {
        doThrow(new ServiceUnavailableException("Group Service unavailable"))
                .when(groupServiceClient).validateGroupExists(groupId);

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("Multiple users: response contains all group members")
    void multipleUsers_allPresent() {
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "300.00",
                        split(userA, "100.00"), split(userB, "100.00"), split(userC, "100.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(resp.getBalances()).hasSize(3);
        assertThat(resp.getGroupId()).isEqualTo(groupId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEW TESTS — settlement integration
    // ═══════════════════════════════════════════════════════════════════════════

    // ── BST-1: no settlements ─────────────────────────────────────────────────

    @Test
    @DisplayName("BST-1. No settlements: expense-derived balances unchanged")
    void noSettlements_balancesUnchanged() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of());

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("500.00");
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-500.00");
    }

    // ── BST-2: one completed settlement ───────────────────────────────────────

    @Test
    @DisplayName("BST-2. Full settlement: B owes A 500, B pays A 500 → both SETTLED")
    void oneCompletedSettlement_fullPayment() {
        // expense: A +500, B -500
        // settlement: B → A = 500
        // expected: A = 0, B = 0
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of(settlement(userB, userA, "500.00")));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.SETTLED);
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findBalance(resp, userB).getStatus()).isEqualTo(BalanceStatus.SETTLED);
    }

    // ── BST-3: partial settlement ─────────────────────────────────────────────

    @Test
    @DisplayName("BST-3. Partial settlement: B owes A 500, B pays 200 → A +300, B -300")
    void partialSettlement_remainingDebtVisible() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of(settlement(userB, userA, "200.00")));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("300.00");
        assertThat(findBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.GETS);
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-300.00");
        assertThat(findBalance(resp, userB).getStatus()).isEqualTo(BalanceStatus.OWES);
    }

    // ── BST-4: multiple settlements ───────────────────────────────────────────

    @Test
    @DisplayName("BST-4. Multiple settlements: B → A 600, C → A 400 → all SETTLED")
    void multipleSettlements_allSettled() {
        // expense: A +1000, B -600, C -400
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "1000.00",
                        split(userA, "0.00"), split(userB, "600.00"), split(userC, "400.00"))),
                List.of(
                        settlement(userB, userA, "600.00"),
                        settlement(userC, userA, "400.00")
                ));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findBalance(resp, userC).getNetBalance()).isEqualByComparingTo("0.00");
    }

    // ── BST-5: multiple creditors and debtors ─────────────────────────────────

    @Test
    @DisplayName("BST-5. Multiple creditors and debtors: partial settlements applied correctly")
    void multipleCreditorDebtors_partialSettlements() {
        // expenses: A +600, B +400, C -500, D -500
        UUID userD = UUID.randomUUID();
        stubGroup(List.of(userA, userB, userC, userD),
                List.of(
                        expense(userA, "600.00", split(userA, "0.00"), split(userB, "0.00"),
                                split(userC, "300.00"), split(userD, "300.00")),
                        expense(userB, "400.00", split(userA, "0.00"), split(userB, "0.00"),
                                split(userC, "200.00"), split(userD, "200.00"))
                ),
                List.of(
                        settlement(userC, userA, "300.00"),   // C pays A 300 of their 500 debt
                        settlement(userD, userB, "200.00")    // D pays B 200 of their 500 debt
                ));

        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(userA, userB, userC, userD));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        // A: +600 - 300 = +300
        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("300.00");
        // B: +400 - 200 = +200
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("200.00");
        // C: -500 + 300 = -200
        assertThat(findBalance(resp, userC).getNetBalance()).isEqualByComparingTo("-200.00");
        // D: -500 + 200 = -300
        assertThat(findBalance(resp, userD).getNetBalance()).isEqualByComparingTo("-300.00");

        // Conservation still holds
        BigDecimal sum = resp.getBalances().stream()
                .map(UserBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.00");
    }

    // ── BST-6: PENDING settlement does not affect balance ─────────────────────

    @Test
    @DisplayName("BST-6. PENDING settlement: NOT returned by Settlement Service → balances unchanged")
    void pendingSettlement_doesNotAffectBalance() {
        // Settlement Service only returns COMPLETED settlements. PENDING ones
        // are filtered at source. We verify: if getCompletedSettlements returns
        // empty (as it would for a group with only PENDING settlements), balances
        // are unchanged from the expense-derived values.
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of() // empty = Settlement Service returned no COMPLETED settlements
        );

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        // Balances must reflect expense state only (no adjustment)
        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("500.00");
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-500.00");
    }

    // ── BST-7: completed settlement involving every member ────────────────────

    @Test
    @DisplayName("BST-7. Settlement involving every member: all reach SETTLED")
    void settlementEveryMember_allSettled() {
        // A pays 900, split equally: A +600, B -300, C -300
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "900.00",
                        split(userA, "300.00"), split(userB, "300.00"), split(userC, "300.00"))),
                List.of(
                        settlement(userB, userA, "300.00"),
                        settlement(userC, userA, "300.00")
                ));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        resp.getBalances().forEach(b ->
                assertThat(b.getNetBalance()).isEqualByComparingTo("0.00"));
    }

    // ── BST-8: settlement conservation ────────────────────────────────────────

    @Test
    @DisplayName("BST-8. Conservation holds after applying completed settlements")
    void settlementConservation() {
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "900.00",
                        split(userA, "300.00"), split(userB, "300.00"), split(userC, "300.00"))),
                List.of(settlement(userB, userA, "200.00")));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        BigDecimal sum = resp.getBalances().stream()
                .map(UserBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(BigDecimal.ZERO)).isZero();
    }

    // ── BST-9: Settlement Service unavailable ─────────────────────────────────

    @Test
    @DisplayName("BST-9. Settlement Service unavailable → ServiceUnavailableException (503)")
    void settlementServiceUnavailable_throws() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, userA);
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(userA, userB));
        when(expenseServiceClient.getGroupExpenses(groupId))
                .thenReturn(List.of(expense(userA, "500.00", split(userA, "250.00"), split(userB, "250.00"))));
        doThrow(new ServiceUnavailableException("Settlement Service unavailable"))
                .when(settlementServiceClient).getCompletedSettlements(groupId);

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Settlement Service unavailable");
    }

    // ── BST-10: invalid settlement user reference ─────────────────────────────

    @Test
    @DisplayName("BST-10. Settlement references unknown payer → BalanceCalculationException")
    void settlement_unknownPayer_throws() {
        UUID unknownUser = UUID.randomUUID();
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "500.00", split(userA, "250.00"), split(userB, "250.00"))),
                List.of(settlement(unknownUser, userA, "100.00")));

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(BalanceCalculationException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("BST-10b. Settlement references unknown receiver → BalanceCalculationException")
    void settlement_unknownReceiver_throws() {
        UUID unknownUser = UUID.randomUUID();
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "500.00", split(userA, "250.00"), split(userB, "250.00"))),
                List.of(settlement(userB, unknownUser, "100.00")));

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(BalanceCalculationException.class)
                .hasMessageContaining("not a member");
    }

    // ── BST-11: BigDecimal rounding ───────────────────────────────────────────

    @Test
    @DisplayName("BST-11. BigDecimal rounding: fractional settlement applied correctly")
    void settlement_fractionalAmount_appliedCorrectly() {
        // A +66.66, B -33.33, C -33.33
        stubGroup(List.of(userA, userB, userC),
                List.of(expense(userA, "100.00",
                        split(userA, "33.34"), split(userB, "33.33"), split(userC, "33.33"))),
                List.of(settlement(userB, userA, "33.33")));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        // A: +66.66 - 33.33 = +33.33
        assertThat(findBalance(resp, userA).getNetBalance()).isEqualByComparingTo("33.33");
        // B: -33.33 + 33.33 = 0
        assertThat(findBalance(resp, userB).getNetBalance()).isEqualByComparingTo("0.00");
        // C: -33.33 unchanged
        assertThat(findBalance(resp, userC).getNetBalance()).isEqualByComparingTo("-33.33");

        // Conservation
        BigDecimal sum = resp.getBalances().stream()
                .map(UserBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(BigDecimal.ZERO)).isZero();
    }

    // ── BST-12: zero-value settlement rejected ────────────────────────────────

    @Test
    @DisplayName("BST-12. Zero-value completed settlement → BalanceCalculationException")
    void zeroValueSettlement_rejected() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "500.00", split(userA, "250.00"), split(userB, "250.00"))),
                List.of(settlement(userB, userA, "0.00")));

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(BalanceCalculationException.class)
                .hasMessageContaining("invalid amount");
    }

    // ── BST-edge: over-settlement rejected ────────────────────────────────────

    @Test
    @DisplayName("Over-settlement: B owes 500 but B → A = 600 → BalanceCalculationException")
    void overSettlement_rejected() {
        // expense: A +500, B -500
        // settlement: B → A = 600 (exceeds B's debt of 500)
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of(settlement(userB, userA, "600.00")));

        assertThatThrownBy(() -> service.calculateGroupBalances(groupId, userA))
                .isInstanceOf(BalanceCalculationException.class)
                .hasMessageContaining("exceeds payer")
                .hasMessageContaining("inconsistent");
    }

    // ── BST-balance conservation check ────────────────────────────────────────

    @Test
    @DisplayName("Balance conservation: sum == 0 after settlements")
    void balanceConservation_afterSettlements() {
        stubGroup(List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of(settlement(userB, userA, "300.00")));

        GroupBalanceResponse resp = service.calculateGroupBalances(groupId, userA);

        BigDecimal sum = resp.getBalances().stream()
                .map(UserBalanceResponse::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(BigDecimal.ZERO)).isZero();
    }

    @Test
    @DisplayName("Personal balance: two-person equal expense")
    void personalBalance_twoPersonEqual() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userA, "500.00"), split(userB, "500.00"))));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("500.00");
        assertThat(findPersonalBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.GETS);
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-500.00");
        assertThat(findPersonalBalance(resp, userB).getStatus()).isEqualTo(BalanceStatus.OWES);
    }

    @Test
    @DisplayName("Personal balance: three-person equal expense")
    void personalBalance_threePersonEqual() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1200.00",
                        split(userA, "400.00"), split(userB, "400.00"), split(userC, "400.00"))));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("800.00");
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-400.00");
        assertThat(findPersonalBalance(resp, userC).getNetBalance()).isEqualByComparingTo("-400.00");
    }

    @Test
    @DisplayName("Personal balance: payer excluded from split")
    void personalBalance_payerExcluded() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userB, "600.00"), split(userC, "400.00"))));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(findPersonalBalance(resp, userA).getTotalShare()).isEqualByComparingTo("0.00");
        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("1000.00");
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-600.00");
        assertThat(findPersonalBalance(resp, userC).getNetBalance()).isEqualByComparingTo("-400.00");
    }

    @Test
    @DisplayName("Personal balance: multiple expenses cancel out")
    void personalBalance_oppositeExpensesCancel() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA)).thenReturn(List.of(
                personalExpense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00")),
                personalExpense(userB, "1000.00", split(userA, "500.00"), split(userB, "500.00"))
        ));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findPersonalBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.SETTLED);
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Personal balance: group expenses from invalid internal response are ignored")
    void personalBalance_groupExpensesExcluded() {
        InternalExpenseData groupExpense = expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"));
        when(expenseServiceClient.getPersonalExpensesForUser(userA)).thenReturn(List.of(
                groupExpense,
                personalExpense(userA, "100.00", split(userA, "50.00"), split(userB, "50.00"))
        ));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(resp.getBalances()).hasSize(2);
        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("50.00");
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("Personal balance: no personal expenses returns empty balances")
    void personalBalance_noExpensesEmpty() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA)).thenReturn(List.of());

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(resp.getBalances()).isEmpty();
    }

    @Test
    @DisplayName("Personal balance: completed settlement fully clears debt")
    void personalBalance_completedSettlementClearsDebt() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userA, "500.00"), split(userB, "500.00"))));
        when(settlementServiceClient.getCompletedPersonalSettlements(userA))
                .thenReturn(List.of(settlement(userB, userA, "500.00")));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findPersonalBalance(resp, userA).getStatus()).isEqualTo(BalanceStatus.SETTLED);
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("0.00");
        assertThat(findPersonalBalance(resp, userB).getStatus()).isEqualTo(BalanceStatus.SETTLED);
    }

    @Test
    @DisplayName("Personal balance: partial settlement leaves remaining debt")
    void personalBalance_partialSettlementLeavesDebt() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userA, "500.00"), split(userB, "500.00"))));
        when(settlementServiceClient.getCompletedPersonalSettlements(userA))
                .thenReturn(List.of(settlement(userB, userA, "200.00")));

        PersonalBalanceSummaryResponse resp = service.calculatePersonalBalances(userA);

        assertThat(findPersonalBalance(resp, userA).getNetBalance()).isEqualByComparingTo("300.00");
        assertThat(findPersonalBalance(resp, userB).getNetBalance()).isEqualByComparingTo("-300.00");
    }

    @Test
    @DisplayName("Personal simplify: completed settlement removes stale suggestion")
    void personalSimplify_completedSettlementRemovesSuggestion() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userA, "500.00"), split(userB, "500.00"))));
        when(settlementServiceClient.getCompletedPersonalSettlements(userA))
                .thenReturn(List.of(settlement(userB, userA, "500.00")));

        PersonalDebtSimplificationResponse resp = service.simplifyPersonalBalances(userA);

        assertThat(resp.getSuggestions()).isEmpty();
    }

    @Test
    @DisplayName("Personal simplify: multiple debtors to one creditor")
    void personalSimplify_multipleDebtorsOneCreditor() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1200.00",
                        split(userA, "400.00"), split(userB, "400.00"), split(userC, "400.00"))));

        PersonalDebtSimplificationResponse resp = service.simplifyPersonalBalances(userA);

        assertThat(resp.getSuggestions()).hasSize(2);
        assertThat(resp.getSuggestions())
                .extracting(PersonalDebtSimplificationSuggestion::getFromUserId)
                .containsExactlyElementsOf(Stream.of(userB, userC)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList());
        assertThat(resp.getSuggestions())
                .allSatisfy(suggestion -> {
                    assertThat(suggestion.getToUserId()).isEqualTo(userA);
                    assertThat(suggestion.getAmount()).isEqualByComparingTo("400.00");
                });
    }

    @Test
    @DisplayName("Personal simplify: already settled produces no suggestions")
    void personalSimplify_alreadySettled() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA)).thenReturn(List.of(
                personalExpense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00")),
                personalExpense(userB, "1000.00", split(userA, "500.00"), split(userB, "500.00"))
        ));

        PersonalDebtSimplificationResponse resp = service.simplifyPersonalBalances(userA);

        assertThat(resp.getSuggestions()).isEmpty();
    }

    @Test
    @DisplayName("Personal simplify: deterministic output")
    void personalSimplify_deterministic() {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1200.00",
                        split(userA, "400.00"), split(userB, "400.00"), split(userC, "400.00"))));

        PersonalDebtSimplificationResponse first = service.simplifyPersonalBalances(userA);
        PersonalDebtSimplificationResponse second = service.simplifyPersonalBalances(userA);

        assertThat(first.getSuggestions())
                .extracting(PersonalDebtSimplificationSuggestion::getFromUserId)
                .containsExactlyElementsOf(second.getSuggestions().stream()
                        .map(PersonalDebtSimplificationSuggestion::getFromUserId)
                        .toList());
    }

    @Test
    @DisplayName("Friend balance: accepted friendship required and three-person expense contributes pairwise share")
    void friendBalance_acceptedFriend_threePersonContribution() {
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1200.00",
                        split(userA, "400.00"), split(userB, "400.00"), split(userC, "400.00"))));
        when(settlementServiceClient.getCompletedPersonalSettlements(userA))
                .thenReturn(List.of(settlement(userB, userA, "100.00")));

        FriendBalanceResponse resp = service.calculateFriendBalance(userA, userB);

        assertThat(resp.getCurrentUserNetBalance()).isEqualByComparingTo("300.00");
        assertThat(resp.getFriendNetBalance()).isEqualByComparingTo("-300.00");
        assertThat(resp.getFromUserId()).isEqualTo(userB);
        assertThat(resp.getToUserId()).isEqualTo(userA);
        assertThat(resp.getAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Friend balance: unrelated or blocked friend rejected before expense lookup")
    void friendBalance_unacceptedRejected() {
        doThrow(new PermissionDeniedException("Accepted friendship is required"))
                .when(authServiceClient).validateAcceptedFriendship(userA, userB);

        assertThatThrownBy(() -> service.calculateFriendBalance(userA, userB))
                .isInstanceOf(PermissionDeniedException.class);
        verify(expenseServiceClient, never()).getPersonalExpensesForUser(any());
    }

    @Test
    @DisplayName("Personal balance: Expense Service unavailable propagated")
    void personalBalance_expenseUnavailable() {
        doThrow(new ServiceUnavailableException("Expense Service unavailable"))
                .when(expenseServiceClient).getPersonalExpensesForUser(userA);

        assertThatThrownBy(() -> service.calculatePersonalBalances(userA))
                .isInstanceOf(ServiceUnavailableException.class);
    }

}
