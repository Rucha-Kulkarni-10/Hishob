package com.hishob.expense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.service.GroupService;
import com.hishob.expense.dto.CreateExpenseRequest;
import com.hishob.expense.dto.ExpenseSplitRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.repository.ExpenseSplitRepository;
import com.hishob.expense.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ExpenseControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private static final String TEST_SECRET = "hishob-test-jwt-secret-for-automated-tests-only-0123456789abcdef";
    private static final String INTERNAL_TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final long TEST_EXPIRATION = 3600000L;

    private UUID ownerId;
    private UUID memberId;
    private UUID nonMemberId;
    private UUID testGroupId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        expenseSplitRepository.deleteAll();
        expenseRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        nonMemberId = UUID.randomUUID();
        testGroupId = UUID.randomUUID();
    }

    private String jwt(UUID userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("name", "Test User")
                .issuedAt(new Date(now))
                .expiration(new Date(now + TEST_EXPIRATION))
                .signWith(key)
                .compact();
    }

    private void asMember(UUID groupId, UUID userId) {
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(eq(groupId), eq(userId), anyString());
    }

    private void stubAllTwoMembers(UUID groupId) {
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(eq(groupId), eq(ownerId), anyString());
        doNothing().when(groupService).validateMember(eq(groupId), eq(memberId), anyString());
    }

    private Expense createPersistedExpense(UUID groupId, UUID createdBy, UUID paidBy,
                                            SplitType type, List<ExpenseSplit> splits) {
        Expense expense = new Expense(groupId, "Dinner", new BigDecimal("100.00"), paidBy, createdBy, type);
        for (ExpenseSplit s : splits) {
            expense.addSplit(s);
        }
        return expenseRepository.save(expense);
    }

    @Test
    @DisplayName("Internal personal expense endpoint returns only connected personal expenses")
    void testInternalPersonalExpensesForUser_GroupExpensesExcluded() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        createPersistedExpense(null, ownerId, ownerId, SplitType.EXACT,
                List.of(new ExpenseSplit(ownerId, new BigDecimal("60.00"), SplitType.EXACT, null),
                        new ExpenseSplit(memberId, new BigDecimal("40.00"), SplitType.EXACT, null)));
        createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EXACT,
                List.of(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EXACT, null),
                        new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EXACT, null)));
        createPersistedExpense(null, otherUserId, otherUserId, SplitType.EXACT,
                List.of(new ExpenseSplit(otherUserId, new BigDecimal("100.00"), SplitType.EXACT, null)));

        mockMvc.perform(get("/internal/personal-expenses/users/" + ownerId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupId").doesNotExist())
                .andExpect(jsonPath("$[0].paidBy", is(ownerId.toString())))
                .andExpect(jsonPath("$[0].splits", hasSize(2)));
    }

    @Test
    @DisplayName("Unauthenticated requests are rejected with 403")
    void testUnauthenticated_Forbidden() throws Exception {
        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("100"),
                ownerId, SplitType.EQUAL,
                List.of(new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(memberId, null, null)));

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups/" + testGroupId + "/expenses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/expenses — EQUAL happy path: 201, splits count correct, createdBy from JWT")
    void testCreateExpense_Equal_Success() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("100"),
                ownerId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(memberId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount", is(100)))
                .andExpect(jsonPath("$.createdBy", is(ownerId.toString())))
                .andExpect(jsonPath("$.splits", hasSize(2)))
                .andExpect(jsonPath("$.splitType", is("EQUAL")));
    }

    @Test
    @DisplayName("POST /api/expenses — EXACT happy path (600+400=1000)")
    void testCreateExpense_Exact_Success() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Hotel", new BigDecimal("1000"),
                ownerId, SplitType.EXACT,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, new BigDecimal("600"), null),
                        new ExpenseSplitRequest(memberId, new BigDecimal("400"), null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitType", is("EXACT")))
                .andExpect(jsonPath("$.splits[0].amount", is(600.0)))
                .andExpect(jsonPath("$.splits[1].amount", is(400.0)));
    }

    @Test
    @DisplayName("POST /api/expenses — PERCENTAGE happy path (50%+50% of 1000)")
    void testCreateExpense_Percentage_Success() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Food", new BigDecimal("1000"),
                ownerId, SplitType.PERCENTAGE,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, new BigDecimal("50")),
                        new ExpenseSplitRequest(memberId, null, new BigDecimal("50"))
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitType", is("PERCENTAGE")))
                .andExpect(jsonPath("$.splits[0].amount", is(500.0)))
                .andExpect(jsonPath("$.splits[1].amount", is(500.0)));
    }

    @Test
    @DisplayName("POST /api/expenses — EXACT sum mismatch 600+300 vs 1000 => 400")
    void testCreateExpense_Exact_SumMismatch() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Hotel", new BigDecimal("1000"),
                ownerId, SplitType.EXACT,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, new BigDecimal("600"), null),
                        new ExpenseSplitRequest(memberId, new BigDecimal("300"), null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/expenses — PERCENTAGE sum != 100 (50+49) => 400")
    void testCreateExpense_Percentage_SumNot100() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Food", new BigDecimal("1000"),
                ownerId, SplitType.PERCENTAGE,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, new BigDecimal("50")),
                        new ExpenseSplitRequest(memberId, null, new BigDecimal("49"))
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/expenses — negative totalAmount => 400")
    void testCreateExpense_NegativeTotal() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("-10"),
                ownerId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(memberId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/expenses — zero totalAmount => 400")
    void testCreateExpense_ZeroTotal() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("0"),
                ownerId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(memberId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/expenses — duplicate participants => 400")
    void testCreateExpense_DuplicateParticipants() throws Exception {
        stubAllTwoMembers(testGroupId);
        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("100"),
                ownerId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(ownerId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/expenses — payer not in group => 403")
    void testCreateExpense_PayerNotInGroup() throws Exception {
        doNothing().when(groupService).validateGroupExists(testGroupId);
        doNothing().when(groupService).validateMember(eq(testGroupId), eq(ownerId), anyString());
        doThrow(new PermissionDeniedException("Payer is not a member of this group"))
                .when(groupService).validateMember(eq(testGroupId), eq(nonMemberId), eq("Payer"));

        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("100"),
                nonMemberId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(memberId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/expenses — participant not in group => 403")
    void testCreateExpense_ParticipantNotInGroup() throws Exception {
        doNothing().when(groupService).validateGroupExists(testGroupId);
        doNothing().when(groupService).validateMember(eq(testGroupId), eq(ownerId), anyString());
        doThrow(new PermissionDeniedException("Participant is not a member of this group"))
                .when(groupService).validateMember(eq(testGroupId), eq(nonMemberId), eq("Participant"));

        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("100"),
                ownerId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(nonMemberId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/expenses — non-member caller => 403")
    void testCreateExpense_NonMemberCaller() throws Exception {
        doNothing().when(groupService).validateGroupExists(testGroupId);
        doThrow(new PermissionDeniedException("Creator is not a member of this group"))
                .when(groupService).validateMember(eq(testGroupId), eq(nonMemberId), eq("Creator"));

        String token = jwt(nonMemberId, "nonmember@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner", new BigDecimal("100"),
                ownerId, SplitType.EQUAL,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, null, null),
                        new ExpenseSplitRequest(memberId, null, null)
                ));

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/expenses/{id} — non-member => 403")
    void testGetExpenseDetail_NonMember_Forbidden() throws Exception {
        List<ExpenseSplit> splits = new ArrayList<>();
        splits.add(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        splits.add(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        Expense expense = createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EQUAL, splits);

        doThrow(new PermissionDeniedException("Caller is not a member of this group"))
                .when(groupService).validateMember(eq(testGroupId), eq(nonMemberId), eq("Caller"));

        String nonMemberToken = jwt(nonMemberId, "nonmember@test.com");

        mockMvc.perform(get("/api/expenses/" + expense.getId())
                        .header("Authorization", "Bearer " + nonMemberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses — member sees list")
    void testListGroupExpenses_MemberSeesList() throws Exception {
        stubAllTwoMembers(testGroupId);

        List<ExpenseSplit> splits1 = new ArrayList<>();
        splits1.add(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        splits1.add(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EQUAL, splits1);

        List<ExpenseSplit> splits2 = new ArrayList<>();
        splits2.add(new ExpenseSplit(ownerId, new BigDecimal("600.00"), SplitType.EXACT, null));
        splits2.add(new ExpenseSplit(memberId, new BigDecimal("400.00"), SplitType.EXACT, null));
        Expense e2 = new Expense(testGroupId, "Hotel", new BigDecimal("1000.00"), ownerId, ownerId, SplitType.EXACT);
        for (ExpenseSplit s : splits2) {
            e2.addSplit(s);
        }
        expenseRepository.save(e2);

        String ownerToken = jwt(ownerId, "owner@test.com");

        mockMvc.perform(get("/api/groups/" + testGroupId + "/expenses")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/expenses/{id} — creator update succeeds; old splits replaced")
    void testUpdateExpense_Creator_Success() throws Exception {
        stubAllTwoMembers(testGroupId);

        List<ExpenseSplit> splits = new ArrayList<>();
        splits.add(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        splits.add(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        Expense expense = createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EQUAL, splits);

        String token = jwt(ownerId, "owner@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner Updated", new BigDecimal("1000"),
                ownerId, SplitType.EXACT,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, new BigDecimal("600"), null),
                        new ExpenseSplitRequest(memberId, new BigDecimal("400"), null)
                ));

        mockMvc.perform(put("/api/expenses/" + expense.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType", is("EXACT")))
                .andExpect(jsonPath("$.description", is("Dinner Updated")));
    }

    @Test
    @DisplayName("PUT /api/expenses/{id} — non-creator (member but not creator) => 403")
    void testUpdateExpense_NonCreator_Forbidden() throws Exception {
        List<ExpenseSplit> splits = new ArrayList<>();
        splits.add(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        splits.add(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        Expense expense = createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EQUAL, splits);

        String memberToken = jwt(memberId, "member@test.com");

        CreateExpenseRequest req = new CreateExpenseRequest(
                testGroupId, "Dinner Updated", new BigDecimal("1000"),
                ownerId, SplitType.EXACT,
                Arrays.asList(
                        new ExpenseSplitRequest(ownerId, new BigDecimal("600"), null),
                        new ExpenseSplitRequest(memberId, new BigDecimal("400"), null)
                ));

        mockMvc.perform(put("/api/expenses/" + expense.getId())
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/expenses/{id} — creator => 204; rows gone")
    void testDeleteExpense_Creator_Success() throws Exception {
        List<ExpenseSplit> splits = new ArrayList<>();
        splits.add(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        splits.add(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        Expense expense = createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EQUAL, splits);

        String ownerToken = jwt(ownerId, "owner@test.com");

        mockMvc.perform(delete("/api/expenses/" + expense.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assert expenseRepository.count() == 0;
        assert expenseSplitRepository.count() == 0;
    }

    @Test
    @DisplayName("DELETE /api/expenses/{id} — non-creator => 403; rows still exist")
    void testDeleteExpense_NonCreator_Forbidden() throws Exception {
        List<ExpenseSplit> splits = new ArrayList<>();
        splits.add(new ExpenseSplit(ownerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        splits.add(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        Expense expense = createPersistedExpense(testGroupId, ownerId, ownerId, SplitType.EQUAL, splits);

        String memberToken = jwt(memberId, "member@test.com");

        mockMvc.perform(delete("/api/expenses/" + expense.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        assert expenseRepository.count() == 1;
    }

    @Test
    @DisplayName("GET /api/expenses — invalid expense id => 404 ExpenseNotFound")
    void testGetExpenseDetail_NotFound() throws Exception {
        UUID fakeExpenseId = UUID.randomUUID();
        String ownerToken = jwt(ownerId, "owner@test.com");

        mockMvc.perform(get("/api/expenses/" + fakeExpenseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

}
