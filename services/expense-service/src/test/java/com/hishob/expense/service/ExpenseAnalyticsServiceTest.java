package com.hishob.expense.service;

import com.hishob.expense.client.GroupServiceClient;
import com.hishob.expense.client.SettlementServiceClient;
import com.hishob.expense.client.SettlementServiceClient.CompletedSettlementData;
import com.hishob.expense.dto.CategorySpendingSummary;
import com.hishob.expense.dto.GroupAnalyticsResponse;
import com.hishob.expense.dto.MemberSpendingSummary;
import com.hishob.expense.dto.MonthlySpendingSummary;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.exception.ServiceUnavailableException;
import com.hishob.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseAnalyticsServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupServiceClient groupServiceClient;

    @Mock
    private SettlementServiceClient settlementServiceClient;

    private ExpenseAnalyticsService analyticsService;

    private UUID groupId;
    private UUID user1;
    private UUID user2;

    @BeforeEach
    void setUp() {
        analyticsService = new ExpenseAnalyticsService(
                expenseRepository,
                groupServiceClient,
                settlementServiceClient
        );

        groupId = UUID.randomUUID();
        user1 = UUID.randomUUID();
        user2 = UUID.randomUUID();
    }

    private Expense createTestExpense(UUID id, UUID gId, String desc, BigDecimal totalAmount,
                                      UUID paidBy, Instant createdAt, List<ExpenseSplit> splits) {
        Expense expense = new Expense(gId, desc, totalAmount, paidBy, paidBy, SplitType.EQUAL);
        setField(expense, "id", id);
        setField(expense, "createdAt", createdAt);
        setField(expense, "updatedAt", createdAt);
        if (splits != null) {
            for (ExpenseSplit split : splits) {
                expense.addSplit(split);
            }
        }
        return expense;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("1. Analytics for valid group member succeeds")
    void testAnalyticsForValidGroupMember() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1, user2));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of());

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getGroupId()).isEqualTo(groupId);
        verify(groupServiceClient).validateMember(groupId, user1, "Current user");
    }

    @Test
    @DisplayName("2. Non-member receives 403 (PermissionDeniedException)")
    void testNonMemberReceivesPermissionDenied() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("Current user is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, user1, "Current user");

        assertThatThrownBy(() -> analyticsService.getGroupAnalytics(groupId, user1, null, null))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("3, 4, 5. Total spending, expense count, and average calculation")
    void testSpendingCountAndAverageCalculation() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1, user2));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());

        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        Expense e1 = createTestExpense(UUID.randomUUID(), groupId, "Dinner", new BigDecimal("300.00"), user1, now, List.of(
                new ExpenseSplit(user1, new BigDecimal("150.00"), SplitType.EQUAL, null),
                new ExpenseSplit(user2, new BigDecimal("150.00"), SplitType.EQUAL, null)
        ));
        Expense e2 = createTestExpense(UUID.randomUUID(), groupId, "Lunch", new BigDecimal("150.00"), user2, now, List.of(
                new ExpenseSplit(user1, new BigDecimal("75.00"), SplitType.EQUAL, null),
                new ExpenseSplit(user2, new BigDecimal("75.00"), SplitType.EQUAL, null)
        ));

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(e1, e2));

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response.getTotalSpending()).isEqualByComparingTo("450.00");
        assertThat(response.getExpenseCount()).isEqualTo(2);
        assertThat(response.getAverageExpense()).isEqualByComparingTo("225.00");
    }

    @Test
    @DisplayName("6 & 7. Member totalPaid, totalShare, and netPosition calculation")
    void testMemberSpendingBreakdown() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1, user2));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());

        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        Expense e1 = createTestExpense(UUID.randomUUID(), groupId, "Groceries", new BigDecimal("100.00"), user1, now, List.of(
                new ExpenseSplit(user1, new BigDecimal("40.00"), SplitType.EXACT, null),
                new ExpenseSplit(user2, new BigDecimal("60.00"), SplitType.EXACT, null)
        ));

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(e1));

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        MemberSpendingSummary u1Summary = response.getMemberSpending().stream()
                .filter(m -> m.getUserId().equals(user1)).findFirst().orElseThrow();
        assertThat(u1Summary.getTotalPaid()).isEqualByComparingTo("100.00");
        assertThat(u1Summary.getTotalShare()).isEqualByComparingTo("40.00");
        assertThat(u1Summary.getNetPosition()).isEqualByComparingTo("60.00");
        assertThat(u1Summary.getStatus()).isEqualTo("OWED");

        MemberSpendingSummary u2Summary = response.getMemberSpending().stream()
                .filter(m -> m.getUserId().equals(user2)).findFirst().orElseThrow();
        assertThat(u2Summary.getTotalPaid()).isEqualByComparingTo("0.00");
        assertThat(u2Summary.getTotalShare()).isEqualByComparingTo("60.00");
        assertThat(u2Summary.getNetPosition()).isEqualByComparingTo("-60.00");
        assertThat(u2Summary.getStatus()).isEqualTo("OWES");
    }

    @Test
    @DisplayName("8. Monthly aggregation uses createdAt only")
    void testMonthlyAggregationUsesCreatedAt() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());

        Instant sepDate = Instant.parse("2026-09-15T10:00:00Z");
        Instant octDate = Instant.parse("2026-10-02T15:30:00Z");

        Expense sepExp = createTestExpense(UUID.randomUUID(), groupId, "Trip", new BigDecimal("200.00"), user1, sepDate, List.of());
        Expense octExp = createTestExpense(UUID.randomUUID(), groupId, "Coffee", new BigDecimal("50.00"), user1, octDate, List.of());

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(octExp, sepExp));

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response.getMonthlyTrends()).hasSize(2);
        MonthlySpendingSummary sepSummary = response.getMonthlyTrends().stream()
                .filter(m -> m.getYearMonth().equals("2026-09")).findFirst().orElseThrow();
        assertThat(sepSummary.getLabel()).isEqualTo("September 2026");
        assertThat(sepSummary.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(sepSummary.getExpenseCount()).isEqualTo(1);

        MonthlySpendingSummary octSummary = response.getMonthlyTrends().stream()
                .filter(m -> m.getYearMonth().equals("2026-10")).findFirst().orElseThrow();
        assertThat(octSummary.getLabel()).isEqualTo("October 2026");
        assertThat(octSummary.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(octSummary.getExpenseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("9. Date filtering routes to repository between query")
    void testDateFiltering() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());

        when(expenseRepository.findByGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq(groupId), any(), any()))
                .thenReturn(List.of());

        analyticsService.getGroupAnalytics(groupId, user1, "2026-10-01", "2026-10-31");

        verify(expenseRepository).findByGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq(groupId), any(), any());
    }

    @Test
    @DisplayName("10. Empty group / no expenses returns valid zero values")
    void testEmptyGroupReturnsZeroValues() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of());

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response.getTotalSpending()).isEqualByComparingTo("0.00");
        assertThat(response.getExpenseCount()).isEqualTo(0);
        assertThat(response.getAverageExpense()).isEqualByComparingTo("0.00");
        assertThat(response.getTotalSettledVolume()).isEqualByComparingTo("0.00");
        assertThat(response.getMonthlyTrends()).isEmpty();
        assertThat(response.getMemberSpending()).hasSize(1);
        assertThat(response.getMemberSpending().get(0).getStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("11. CSV response generates formatted content with quotes escaping")
    void testCsvExport() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");

        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        Expense e = createTestExpense(UUID.randomUUID(), groupId, "Dinner with \"special\" guest",
                new BigDecimal("500.00"), user1, now, List.of(
                        new ExpenseSplit(user1, new BigDecimal("250.00"), SplitType.EQUAL, null),
                        new ExpenseSplit(user2, new BigDecimal("250.00"), SplitType.EQUAL, null)
                ));

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(e));

        String csv = analyticsService.exportExpensesCsv(groupId, user1, null, null);

        assertThat(csv).contains("Date,Description,Category,Total Amount (INR),Paid By,Split Type,Participants Count");
        assertThat(csv).contains("\"Dinner with \"\"special\"\" guest\"");
        assertThat(csv).contains("\"Other\"");
        assertThat(csv).contains("500.00");
        assertThat(csv).contains("User-" + user1.toString().substring(0, 8));
        assertThat(csv).contains("EQUAL,2");
    }

    @Test
    @DisplayName("12. Settlement service communication failure propagates ServiceUnavailableException")
    void testSettlementServiceFailureHandling() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of());

        when(settlementServiceClient.getCompletedSettlements(groupId))
                .thenThrow(new ServiceUnavailableException("Settlement Service unavailable"));

        assertThatThrownBy(() -> analyticsService.getGroupAnalytics(groupId, user1, null, null))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Settlement Service unavailable");
    }

    @Test
    @DisplayName("13. Total settled volume sums completed settlements")
    void testTotalSettledVolumeCalculation() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1));
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of());

        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of(
                new CompletedSettlementData(user2, user1, new BigDecimal("100.00")),
                new CompletedSettlementData(user2, user1, new BigDecimal("45.50"))
        ));

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response.getTotalSettledVolume()).isEqualByComparingTo("145.50");
    }

    @Test
    @DisplayName("14. Group analytics returns category breakdown with correct amounts and percentages")
    void testCategorySpendingBreakdown() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());

        Expense e1 = createTestExpense(UUID.randomUUID(), groupId, "Lunch", new BigDecimal("300.00"), user1, Instant.now(), List.of(
                new ExpenseSplit(user1, new BigDecimal("300.00"), SplitType.EQUAL, null)
        ));
        e1.setCategory(ExpenseCategory.FOOD);

        Expense e2 = createTestExpense(UUID.randomUUID(), groupId, "Cab", new BigDecimal("100.00"), user1, Instant.now(), List.of(
                new ExpenseSplit(user1, new BigDecimal("100.00"), SplitType.EQUAL, null)
        ));
        e2.setCategory(ExpenseCategory.TRAVEL);

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(e1, e2));

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response.getCategorySpending()).hasSize(2);

        CategorySpendingSummary food = response.getCategorySpending().stream()
                .filter(c -> c.getCategory() == ExpenseCategory.FOOD).findFirst().orElseThrow();
        assertThat(food.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(food.getExpenseCount()).isEqualTo(1);
        assertThat(food.getPercentageOfTotal()).isEqualTo(75.0);

        CategorySpendingSummary travel = response.getCategorySpending().stream()
                .filter(c -> c.getCategory() == ExpenseCategory.TRAVEL).findFirst().orElseThrow();
        assertThat(travel.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(travel.getExpenseCount()).isEqualTo(1);
        assertThat(travel.getPercentageOfTotal()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("15. Empty group returns empty category breakdown with zero safe percentage")
    void testEmptyGroupCategoryBreakdown() {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, user1, "Current user");
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(user1));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of());

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(groupId, user1, null, null);

        assertThat(response.getCategorySpending()).isEmpty();
    }
}
