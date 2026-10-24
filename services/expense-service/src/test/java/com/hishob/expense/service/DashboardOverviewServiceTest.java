package com.hishob.expense.service;

import com.hishob.expense.dto.DashboardOverviewResponse;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardOverviewServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupService groupService;

    private DashboardOverviewService service;

    private UUID user;
    private UUID peer;

    @BeforeEach
    void setUp() {
        service = new DashboardOverviewService(expenseRepository, groupService);
        user = UUID.randomUUID();
        peer = UUID.randomUUID();
    }

    private Expense expense(UUID groupId, String total, UUID paidBy, Object[]... splits) {
        Expense e = new Expense(groupId, "d", new BigDecimal(total), paidBy, paidBy, SplitType.EQUAL);
        e.setCategory(ExpenseCategory.OTHER);
        for (Object[] s : splits) {
            e.addSplit(new ExpenseSplit((UUID) s[0], (BigDecimal) s[1], SplitType.EQUAL, null));
        }
        return e;
    }

    @Test
    @DisplayName("Combined scope is the disjoint sum of group and personal; share and paid tracked per scope")
    void combinesDisjointScopes() {
        Expense personal = expense(null, "200.00", user,
                new Object[]{user, new BigDecimal("100.00")},
                new Object[]{peer, new BigDecimal("100.00")});
        Expense group = expense(UUID.randomUUID(), "100.00", user,
                new Object[]{user, new BigDecimal("50.00")},
                new Object[]{peer, new BigDecimal("50.00")});

        UUID groupId = group.getGroupId();
        when(expenseRepository.findPersonalExpensesForUser(user)).thenReturn(List.of(personal));
        when(groupService.listGroupIdsForMember(user)).thenReturn(List.of(groupId));
        when(expenseRepository.findByGroupIdIn(List.of(groupId))).thenReturn(List.of(group));

        DashboardOverviewResponse resp = service.getOverview(user);

        assertThat(resp.getPersonal().getExpenseCount()).isEqualTo(1);
        assertThat(resp.getPersonal().getRecordedSpend()).isEqualByComparingTo("200.00");
        assertThat(resp.getPersonal().getYourShare()).isEqualByComparingTo("100.00");
        assertThat(resp.getPersonal().getYouPaid()).isEqualByComparingTo("200.00");

        assertThat(resp.getGroup().getExpenseCount()).isEqualTo(1);
        assertThat(resp.getGroup().getRecordedSpend()).isEqualByComparingTo("100.00");
        assertThat(resp.getGroup().getYourShare()).isEqualByComparingTo("50.00");
        assertThat(resp.getGroup().getYouPaid()).isEqualByComparingTo("100.00");

        // Combined counts each expense once, never double-counting across the disjoint scopes.
        assertThat(resp.getCombined().getExpenseCount()).isEqualTo(2);
        assertThat(resp.getCombined().getRecordedSpend()).isEqualByComparingTo("300.00");
        assertThat(resp.getCombined().getYourShare()).isEqualByComparingTo("150.00");
        assertThat(resp.getCombined().getYouPaid()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("A group member who is not a split participant contributes recorded spend but zero share and zero paid")
    void memberWithoutSplitHasNoDebt() {
        Expense group = expense(UUID.randomUUID(), "90.00", peer,
                new Object[]{peer, new BigDecimal("45.00")},
                new Object[]{UUID.randomUUID(), new BigDecimal("45.00")});

        when(expenseRepository.findPersonalExpensesForUser(user)).thenReturn(List.of());
        when(groupService.listGroupIdsForMember(user)).thenReturn(List.of(group.getGroupId()));
        when(expenseRepository.findByGroupIdIn(anyList())).thenReturn(List.of(group));

        DashboardOverviewResponse resp = service.getOverview(user);

        assertThat(resp.getGroup().getExpenseCount()).isEqualTo(1);
        assertThat(resp.getGroup().getRecordedSpend()).isEqualByComparingTo("90.00");
        assertThat(resp.getGroup().getYourShare()).isEqualByComparingTo("0.00");
        assertThat(resp.getGroup().getYouPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("No group membership short-circuits the group query and yields zero group totals")
    void noGroupsSkipsGroupQuery() {
        when(expenseRepository.findPersonalExpensesForUser(user)).thenReturn(List.of());
        when(groupService.listGroupIdsForMember(user)).thenReturn(List.of());

        DashboardOverviewResponse resp = service.getOverview(user);

        verify(expenseRepository, never()).findByGroupIdIn(any());
        assertThat(resp.getGroup().getExpenseCount()).isZero();
        assertThat(resp.getGroup().getRecordedSpend()).isEqualByComparingTo("0.00");
        assertThat(resp.getCombined().getExpenseCount()).isZero();
    }
}
