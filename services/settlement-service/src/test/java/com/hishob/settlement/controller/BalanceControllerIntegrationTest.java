package com.hishob.settlement.controller;

import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.ExpenseServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.dto.InternalExpenseData;
import com.hishob.settlement.dto.InternalSplitData;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.exception.GroupNotFoundException;
import com.hishob.settlement.exception.PermissionDeniedException;
import com.hishob.settlement.exception.ServiceUnavailableException;
import com.hishob.settlement.repository.SettlementRepository;
import com.hishob.settlement.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class BalanceControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private GroupServiceClient groupServiceClient;

    @MockitoBean
    private ExpenseServiceClient expenseServiceClient;

    @MockitoBean
    private SettlementRepository settlementRepository;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private static final String TEST_SECRET =
            "d3f4u1t-s3cr3t-k3y-f0r-h1sh0b-auth-s3rv1c3-jwt-t0k3n-g3n3r4t10n-2026";

    private UUID groupId;
    private UUID userA;
    private UUID userB;
    private UUID userC;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        groupId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String jwt(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId + "@test.com")
                .claim("userId", userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000L))
                .signWith(key)
                .compact();
    }

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

    private Settlement settlement(UUID fromUserId, UUID toUserId, String amount) {
        return new Settlement(groupId, fromUserId, toUserId, new BigDecimal(amount), SettlementStatus.COMPLETED);
    }

    /** Stubs the full happy path for group/expense/settlement. */
    private void stubHappyPath(UUID caller,
                               List<UUID> members,
                               List<InternalExpenseData> expenses,
                               List<Settlement> settlements) {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, caller);
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(members);
        when(expenseServiceClient.getGroupExpenses(groupId)).thenReturn(expenses);
        when(settlementRepository.findByGroupIdAndStatus(groupId, SettlementStatus.COMPLETED)).thenReturn(settlements);
    }

    // ── authentication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("No JWT → 403 Forbidden")
    void noJwt_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/balances"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid JWT → 403 Forbidden")
    void invalidJwt_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isForbidden());
    }

    // ── authorization ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JWT + non-member → 403 Forbidden")
    void validJwt_nonMember_forbidden() throws Exception {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("User is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, userA);

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Valid JWT + invalid group → 404 Not Found")
    void validJwt_invalidGroup_notFound() throws Exception {
        doThrow(new GroupNotFoundException("Group not found: " + groupId))
                .when(groupServiceClient).validateGroupExists(groupId);

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isNotFound());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JWT + member → 200 with correct balances")
    void validJwt_member_returnsBalances() throws Exception {
        stubHappyPath(userA, List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId", is(groupId.toString())))
                .andExpect(jsonPath("$.balances", hasSize(2)));
    }

    @Test
    @DisplayName("No expenses → all members SETTLED with zero balances")
    void noExpenses_allSettled() throws Exception {
        stubHappyPath(userA, List.of(userA, userB, userC), List.of(), List.of());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances", hasSize(3)))
                .andExpect(jsonPath("$.balances[*].status", everyItem(is("SETTLED"))));
    }

    @Test
    @DisplayName("Single expense: correct paid/share/net/status in response")
    void singleExpense_correctResponse() throws Exception {
        stubHappyPath(userA, List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userA + "')].status",
                        hasItem("GETS")))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userB + "')].status",
                        hasItem("OWES")));
    }

    @Test
    @DisplayName("Multiple expenses: balances aggregate correctly")
    void multipleExpenses_aggregated() throws Exception {
        stubHappyPath(userA, List.of(userA, userB), List.of(
                expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00")),
                expense(userB, "600.00",  split(userA, "300.00"), split(userB, "300.00"))
        ), List.of());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userA + "')].netBalance",
                        hasItem(closeTo(200.0, 0.001))))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userB + "')].netBalance",
                        hasItem(closeTo(-200.0, 0.001))));
    }

    @Test
    @DisplayName("Member with no expenses appears with zero balance SETTLED")
    void memberWithNoExpenses_appearsSettled() throws Exception {
        stubHappyPath(userA, List.of(userA, userB, userC),
                List.of(expense(userA, "100.00", split(userA, "50.00"), split(userB, "50.00"))),
                List.of());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userC + "')].status",
                        hasItem("SETTLED")))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userC + "')].netBalance",
                        hasItem(closeTo(0.0, 0.001))));
    }

    // ── service unavailable ───────────────────────────────────────────────────

    @Test
    @DisplayName("Expense Service unavailable → 503 Service Unavailable")
    void expenseServiceUnavailable_503() throws Exception {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, userA);
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(userA, userB));
        doThrow(new ServiceUnavailableException("Expense Service unavailable"))
                .when(expenseServiceClient).getGroupExpenses(groupId);

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Group Service unavailable → 503 Service Unavailable")
    void groupServiceUnavailable_503() throws Exception {
        doThrow(new ServiceUnavailableException("Group Service unavailable"))
                .when(groupServiceClient).validateGroupExists(groupId);

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Balance calculation error → 500 Internal Server Error")
    void balanceCalculationError_500() throws Exception {
        stubHappyPath(userA, List.of(userA, userB),
                List.of(expense(userA, "500.00", split(userA, "250.00"), split(userB, "250.00"))),
                List.of(new Settlement(groupId, UUID.randomUUID(), userA, new BigDecimal("100.00"), SettlementStatus.COMPLETED)));

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isInternalServerError());
    }

    // ── response structure ────────────────────────────────────────────────────

    @Test
    @DisplayName("Response contains groupId and balances array")
    void responseStructure() throws Exception {
        stubHappyPath(userA, List.of(userA),
                List.of(expense(userA, "100.00", split(userA, "100.00"))),
                List.of());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").exists())
                .andExpect(jsonPath("$.balances").isArray())
                .andExpect(jsonPath("$.balances[0].userId").exists())
                .andExpect(jsonPath("$.balances[0].totalPaid").exists())
                .andExpect(jsonPath("$.balances[0].totalShare").exists())
                .andExpect(jsonPath("$.balances[0].netBalance").exists())
                .andExpect(jsonPath("$.balances[0].status").exists());
    }

    // ── settlement integration scenarios ─────────────────────────────────────

    @Test
    @DisplayName("Full settlement: B owes A 500, B pays A 500 → both SETTLED (200 OK)")
    void fullSettlement_bothSettled() throws Exception {
        stubHappyPath(userA, List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of(settlement(userB, userA, "500.00")));

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userA + "')].netBalance",
                        hasItem(closeTo(0.0, 0.001))))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userA + "')].status",
                        hasItem("SETTLED")))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userB + "')].netBalance",
                        hasItem(closeTo(0.0, 0.001))))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userB + "')].status",
                        hasItem("SETTLED")));
    }

    @Test
    @DisplayName("Partial settlement: B pays 200 of 500 → A +300, B -300")
    void partialSettlement_remainingDebt_200OK() throws Exception {
        stubHappyPath(userA, List.of(userA, userB),
                List.of(expense(userA, "1000.00", split(userA, "500.00"), split(userB, "500.00"))),
                List.of(settlement(userB, userA, "200.00")));

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userA + "')].netBalance",
                        hasItem(closeTo(300.0, 0.001))))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userB + "')].netBalance",
                        hasItem(closeTo(-300.0, 0.001))));
    }

    @Test
    @DisplayName("Multiple settlements: B → A 600, C → A 400 → all SETTLED")
    void multipleSettlements_allSettled() throws Exception {
        stubHappyPath(userA, List.of(userA, userB, userC),
                List.of(expense(userA, "1000.00",
                        split(userA, "0.00"), split(userB, "600.00"), split(userC, "400.00"))),
                List.of(
                        settlement(userB, userA, "600.00"),
                        settlement(userC, userA, "400.00")
                ));

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[*].status", everyItem(is("SETTLED"))));
    }

    @Test
    @DisplayName("Personal balances require JWT")
    void personalBalances_noJwt_forbidden() throws Exception {
        mockMvc.perform(get("/api/personal-balances"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Personal balances: valid JWT returns connected personal balances")
    void personalBalances_validJwt_success() throws Exception {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userA, "500.00"), split(userB, "500.00"))));

        mockMvc.perform(get("/api/personal-balances")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userA.toString())))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userA + "')].status", hasItem("GETS")))
                .andExpect(jsonPath("$.balances[?(@.userId == '" + userB + "')].status", hasItem("OWES")));
    }

    @Test
    @DisplayName("Personal simplify: valid JWT returns suggestions")
    void personalSimplify_validJwt_success() throws Exception {
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1200.00",
                        split(userA, "400.00"), split(userB, "400.00"), split(userC, "400.00"))));

        mockMvc.perform(get("/api/personal-balances/simplify")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(2)))
                .andExpect(jsonPath("$.suggestions[*].toUserId", everyItem(is(userA.toString()))));
    }

    @Test
    @DisplayName("Friend balance: accepted friend succeeds")
    void friendBalance_accepted_success() throws Exception {
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userA, "1000.00",
                        split(userA, "500.00"), split(userB, "500.00"))));

        mockMvc.perform(get("/api/friends/" + userB + "/balance")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserId", is(userA.toString())))
                .andExpect(jsonPath("$.friendUserId", is(userB.toString())))
                .andExpect(jsonPath("$.fromUserId", is(userB.toString())))
                .andExpect(jsonPath("$.toUserId", is(userA.toString())))
                .andExpect(jsonPath("$.amount", closeTo(500.0, 0.001)));
    }

    @Test
    @DisplayName("Friend balance: pending or blocked friend rejected")
    void friendBalance_unaccepted_forbidden() throws Exception {
        doThrow(new PermissionDeniedException("Accepted friendship is required"))
                .when(authServiceClient).validateAcceptedFriendship(userA, userB);

        mockMvc.perform(get("/api/friends/" + userB + "/balance")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal friend balance: valid internal token succeeds")
    void getFriendBalanceInternal_validToken_success() throws Exception {
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(expenseServiceClient.getPersonalExpensesForUser(userA))
                .thenReturn(List.of(personalExpense(userB, "600.00",
                        split(userA, "300.00"), split(userB, "300.00"))));

        mockMvc.perform(get("/internal/personal-balances/users/" + userA + "/friends/" + userB)
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserId", is(userA.toString())))
                .andExpect(jsonPath("$.friendUserId", is(userB.toString())))
                .andExpect(jsonPath("$.currentUserNetBalance", closeTo(-300.0, 0.001)))
                .andExpect(jsonPath("$.friendNetBalance", closeTo(300.0, 0.001)))
                .andExpect(jsonPath("$.fromUserId", is(userA.toString())))
                .andExpect(jsonPath("$.toUserId", is(userB.toString())))
                .andExpect(jsonPath("$.amount", closeTo(300.0, 0.001)));
    }

    @Test
    @DisplayName("Internal friend balance: missing internal token returns 401")
    void getFriendBalanceInternal_missingToken_unauthorized() throws Exception {
        mockMvc.perform(get("/internal/personal-balances/users/" + userA + "/friends/" + userB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal friend balance: wrong internal token returns 401")
    void getFriendBalanceInternal_wrongToken_unauthorized() throws Exception {
        mockMvc.perform(get("/internal/personal-balances/users/" + userA + "/friends/" + userB)
                        .header("X-Internal-Token", "wrong-internal-token"))
                .andExpect(status().isUnauthorized());
    }

}
