package com.hishob.expense.controller;

import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.Group;
import com.hishob.expense.entity.GroupMember;
import com.hishob.expense.entity.MemberRole;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.repository.GroupMemberRepository;
import com.hishob.expense.repository.GroupRepository;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-user and cross-group authorization coverage for the personal (individual) expense
 * reads, exercised against the real H2 schema and the real GroupService membership checks.
 *
 * Encodes the separation rules: an individual expense exists only where groupId IS NULL and
 * only for its payer/participants, and a group expense is never reachable through an
 * individual-expense route no matter who the caller is.
 */
@SpringBootTest
class PersonalExpenseScopeIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private UUID userId;
    private UUID friendId;
    private UUID strangerId;
    private Group group;

    private static final String TEST_SECRET = "hishob-test-jwt-secret-for-automated-tests-only-0123456789abcdef";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();

        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        userId = UUID.randomUUID();
        friendId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        group = groupRepository.save(new Group("Shared flat", null, userId));
        groupMemberRepository.save(new GroupMember(group.getId(), userId, MemberRole.OWNER));
        groupMemberRepository.save(new GroupMember(group.getId(), friendId, MemberRole.MEMBER));
    }

    private String jwt(UUID subjectUserId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("scope_" + subjectUserId + "@example.com")
                .claim("userId", subjectUserId.toString())
                .claim("name", "Scope Tester")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    private Expense save(UUID groupId, String description, String total, UUID paidBy, UUID createdBy,
                         UUID... participants) {
        Expense expense = new Expense(groupId, description, new BigDecimal(total), paidBy, createdBy, SplitType.EQUAL);
        expense.setCategory(ExpenseCategory.OTHER);
        BigDecimal each = new BigDecimal(total)
                .divide(BigDecimal.valueOf(participants.length), 2, java.math.RoundingMode.HALF_UP);
        for (UUID participant : participants) {
            expense.addSplit(new ExpenseSplit(participant, each, SplitType.EQUAL, null));
        }
        return expenseRepository.save(expense);
    }

    @Test
    @DisplayName("GET /api/personal-expenses — only groupId-null expenses involving the caller; the caller's group expenses stay in the group")
    void personalExpenseListIsIndividualScopeOnly() throws Exception {
        Expense mine = save(null, "Coffee with friend", "200.00", userId, userId, userId, friendId);
        save(null, "Friend's own dinner", "100.00", friendId, friendId, friendId);
        save(group.getId(), "Groceries", "500.00", userId, userId, userId, friendId);

        mockMvc.perform(get("/api/personal-expenses")
                        .header("Authorization", "Bearer " + jwt(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(mine.getId().toString())))
                .andExpect(jsonPath("$[0].groupId").doesNotExist());

        mockMvc.perform(get("/api/personal-expenses")
                        .header("Authorization", "Bearer " + jwt(strangerId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/personal-expenses/{id} — participants read it, a stranger gets 404, and a group expense is never readable here")
    void personalExpenseDetailRejectsNonParticipantsAndGroupExpenses() throws Exception {
        Expense personal = save(null, "Taxi", "100.00", userId, userId, userId, friendId);
        Expense groupExpense = save(group.getId(), "Rent", "900.00", userId, userId, userId, friendId);

        mockMvc.perform(get("/api/personal-expenses/" + personal.getId())
                        .header("Authorization", "Bearer " + jwt(friendId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(personal.getId().toString())));

        mockMvc.perform(get("/api/personal-expenses/" + personal.getId())
                        .header("Authorization", "Bearer " + jwt(strangerId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // A group member — including the creator — cannot read a group expense through the individual route.
        mockMvc.perform(get("/api/personal-expenses/" + groupExpense.getId())
                        .header("Authorization", "Bearer " + jwt(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/expenses/{id} — group expenses require group membership; the individual route and this route are mutually exclusive")
    void groupExpenseDetailRequiresMembership() throws Exception {
        Expense groupExpense = save(group.getId(), "Internet", "300.00", userId, userId, userId, friendId);

        mockMvc.perform(get("/api/expenses/" + groupExpense.getId())
                        .header("Authorization", "Bearer " + jwt(friendId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId", is(group.getId().toString())));

        mockMvc.perform(get("/api/expenses/" + groupExpense.getId())
                        .header("Authorization", "Bearer " + jwt(strangerId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // A personal expense is not a group expense, so the group route reports it as absent.
        Expense personal = save(null, "Metro card", "50.00", userId, userId, userId);
        mockMvc.perform(get("/api/expenses/" + personal.getId())
                        .header("Authorization", "Bearer " + jwt(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/groups/{id}/expenses — a non-member of another group cannot read this group's expenses")
    void groupExpenseListIsScopedToMembers() throws Exception {
        save(group.getId(), "Cleaning", "400.00", userId, userId, userId, friendId);
        Group other = groupRepository.save(new Group("Other crew", null, friendId));
        groupMemberRepository.save(new GroupMember(other.getId(), friendId, MemberRole.OWNER));

        mockMvc.perform(get("/api/groups/" + group.getId() + "/expenses")
                        .header("Authorization", "Bearer " + jwt(strangerId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups/" + other.getId() + "/expenses")
                        .header("Authorization", "Bearer " + jwt(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups/" + group.getId() + "/expenses")
                        .header("Authorization", "Bearer " + jwt(friendId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/friends/{id}/expenses — only expenses between the pair, group expenses excluded")
    void friendExpenseListExcludesGroupExpenses() throws Exception {
        Expense shared = save(null, "Concert", "600.00", friendId, friendId, userId, friendId);
        save(null, "Friend with a third person", "300.00", friendId, friendId, friendId, strangerId);
        save(group.getId(), "Flat rent", "1200.00", userId, userId, userId, friendId);
        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(userId), any());

        mockMvc.perform(get("/api/friends/" + friendId + "/expenses")
                        .header("Authorization", "Bearer " + jwt(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(shared.getId().toString())))
                .andExpect(jsonPath("$[0].groupId").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/friends/{id}/expenses — unaccepted friendship is denied")
    void friendExpenseListRequiresAcceptedFriendship() throws Exception {
        save(null, "Concert", "600.00", friendId, friendId, userId, friendId);
        doThrow(new PermissionDeniedException("All personal expense users must be accepted friends"))
                .when(authServiceClient).validateAcceptedFriendships(any(), any());

        mockMvc.perform(get("/api/friends/" + strangerId + "/expenses")
                        .header("Authorization", "Bearer " + jwt(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Individual and group expense lists are disjoint — no expense appears in both scopes")
    void scopesNeverOverlap() throws Exception {
        save(null, "Personal only", "100.00", userId, userId, userId);
        save(group.getId(), "Group only", "200.00", userId, userId, userId);

        var personalIds = expenseRepository.findPersonalExpensesForUser(userId)
                .stream().map(Expense::getId).toList();
        var groupIds = expenseRepository.findByGroupIdIn(List.of(group.getId()))
                .stream().map(Expense::getId).toList();

        org.assertj.core.api.Assertions.assertThat(personalIds)
                .as("an expense is either individual or group-scoped, never both")
                .doesNotContainAnyElementsOf(groupIds);
    }
}
