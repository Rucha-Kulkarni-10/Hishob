package com.hishob.expense.controller;

import com.hishob.expense.service.GroupService;
import com.hishob.expense.client.SettlementServiceClient;
import com.hishob.expense.client.SettlementServiceClient.CompletedSettlementData;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ExpenseAnalyticsControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private SettlementServiceClient settlementServiceClient;

    @Autowired
    private ExpenseRepository expenseRepository;

    private MockMvc mockMvc;

    private UUID userId;
    private UUID groupId;
    private String jwtToken;

    private static final String TEST_SECRET = "hishob-test-jwt-secret-for-automated-tests-only-0123456789abcdef";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        expenseRepository.deleteAll();

        userId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        jwtToken = Jwts.builder()
                .subject("tester@example.com")
                .claim("userId", userId.toString())
                .claim("name", "Tester")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses/analytics - 200 OK for valid member")
    void getAnalyticsSuccess() throws Exception {
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, userId, "Current user");
        when(groupService.getGroupMemberIds(groupId)).thenReturn(List.of(userId));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of(
                new CompletedSettlementData(UUID.randomUUID(), userId, new BigDecimal("50.00"))
        ));

        Expense exp = new Expense(groupId, "Team Dinner", new BigDecimal("100.00"), userId, userId, SplitType.EQUAL);
        exp.addSplit(new ExpenseSplit(userId, new BigDecimal("100.00"), SplitType.EQUAL, null));
        expenseRepository.save(exp);

        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId", is(groupId.toString())))
                .andExpect(jsonPath("$.totalSpending", is(100.00)))
                .andExpect(jsonPath("$.expenseCount", is(1)))
                .andExpect(jsonPath("$.averageExpense", is(100.00)))
                .andExpect(jsonPath("$.totalSettledVolume", is(50.00)));
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses/analytics - 403 Forbidden for non-member")
    void getAnalyticsForbidden() throws Exception {
        doNothing().when(groupService).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("Current user is not a member of this group"))
                .when(groupService).validateMember(groupId, userId, "Current user");

        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses/analytics - 403 Forbidden without Bearer token")
    void getAnalyticsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses/export/csv - 200 OK with CSV headers")
    void exportCsvSuccess() throws Exception {
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, userId, "Current user");

        Expense exp = new Expense(groupId, "Supplies", new BigDecimal("75.00"), userId, userId, SplitType.EQUAL);
        exp.addSplit(new ExpenseSplit(userId, new BigDecimal("75.00"), SplitType.EQUAL, null));
        expenseRepository.save(exp);

        mockMvc.perform(get("/api/groups/{groupId}/expenses/export/csv", groupId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"expenses-" + groupId + ".csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Date,Description,Category,Total Amount (INR),Paid By,Split Type,Participants Count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Supplies\",\"Other\",75.00")));
    }

    // ── #137 payer / split-type query parameters ─────────────────────────────

    private void persistExpense(String description, String amount, UUID payer, SplitType splitType) {
        Expense expense = new Expense(groupId, description, new BigDecimal(amount), payer, payer, splitType);
        expense.addSplit(new ExpenseSplit(payer, new BigDecimal(amount), splitType, null));
        expenseRepository.save(expense);
    }

    @Test
    @DisplayName("Analytics applies payerId and splitType query params to the totals it returns")
    void analyticsAppliesPayerAndSplitTypeParams() throws Exception {
        UUID otherPayer = UUID.randomUUID();
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, userId, "Current user");
        when(groupService.getGroupMemberIds(groupId)).thenReturn(List.of(userId, otherPayer));
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());

        persistExpense("Own lunch", "200.00", userId, SplitType.EQUAL);
        persistExpense("Friend cab", "50.00", otherPayer, SplitType.EXACT);

        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .param("payerId", userId.toString())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCount", is(1)))
                .andExpect(jsonPath("$.totalSpending", is(200.00)))
                .andExpect(jsonPath("$.monthlyTrends", hasSize(1)))
                .andExpect(jsonPath("$.categorySpending", hasSize(1)));

        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .param("splitType", "EXACT")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCount", is(1)))
                .andExpect(jsonPath("$.totalSpending", is(50.00)));

        // Both filters together match nothing, and the response says so instead of falling back
        // to the unfiltered totals.
        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .param("payerId", userId.toString())
                        .param("splitType", "EXACT")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCount", is(0)))
                .andExpect(jsonPath("$.totalSpending", is(0.00)))
                .andExpect(jsonPath("$.monthlyTrends", hasSize(0)))
                .andExpect(jsonPath("$.categorySpending", hasSize(0)));
    }

    @Test
    @DisplayName("CSV export applies the same payerId and splitType params as the on-page report")
    void csvExportAppliesPayerAndSplitTypeParams() throws Exception {
        UUID otherPayer = UUID.randomUUID();
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, userId, "Current user");

        persistExpense("Own lunch", "200.00", userId, SplitType.EQUAL);
        persistExpense("Friend cab", "50.00", otherPayer, SplitType.EXACT);

        mockMvc.perform(get("/api/groups/{groupId}/expenses/export/csv", groupId)
                        .param("payerId", userId.toString())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Own lunch")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Friend cab"))));

        mockMvc.perform(get("/api/groups/{groupId}/expenses/export/csv", groupId)
                        .param("splitType", "EXACT")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Friend cab")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Own lunch"))));
    }

    @Test
    @DisplayName("Unknown split type filter value → 400 Bad Request rather than an ignored filter")
    void analyticsRejectsUnknownSplitType() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/expenses/analytics", groupId)
                        .param("splitType", "SPLIT_THE_BILL")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest());
    }
}
