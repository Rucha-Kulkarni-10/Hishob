package com.hishob.expense.controller;

import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.Group;
import com.hishob.expense.entity.GroupMember;
import com.hishob.expense.entity.MemberRole;
import com.hishob.expense.entity.SplitType;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for the combined dashboard overview, exercised against the real
 * H2 schema so the group-scoped and personal-scoped queries and membership scoping are
 * proven, not mocked. Encodes the accounting rules: a group expense lives only in its
 * own group, a personal expense lives only in the personal scope, and the combined
 * overview sums the two disjoint scopes exactly once.
 */
@SpringBootTest
class DashboardOverviewControllerIntegrationTest {

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

    private MockMvc mockMvc;

    private final String jwtToken() {
        return buildToken(userId);
    }

    private UUID userId;
    private UUID peerId;
    private UUID strangerId;

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
        peerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
    }

    private String buildToken(UUID subjectUserId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("overview_" + subjectUserId + "@example.com")
                .claim("userId", subjectUserId.toString())
                .claim("name", "Overview Tester")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    private Group newGroup(String name) {
        Group g = new Group(name, null, userId);
        return groupRepository.save(g);
    }

    private void addMember(UUID groupId, UUID memberUserId, MemberRole role) {
        groupMemberRepository.save(new GroupMember(groupId, memberUserId, role));
    }

    private Expense saveExpense(UUID groupId, String desc, String total, UUID paidBy, UUID createdBy,
                                Object[]... splits) {
        Expense e = new Expense(groupId, desc, new BigDecimal(total), paidBy, createdBy, SplitType.EQUAL);
        e.setCategory(ExpenseCategory.OTHER);
        for (Object[] s : splits) {
            e.addSplit(new ExpenseSplit((UUID) s[0], (BigDecimal) s[1], SplitType.EQUAL, null));
        }
        return expenseRepository.save(e);
    }

    @Test
    @DisplayName("Combined overview sums group + personal once, scopes by membership, and never counts a non-participant member's share")
    void overviewCombinesGroupAndPersonalWithoutDoubleCounting() throws Exception {
        // Group A: user is a member and a split participant.
        Group a = newGroup("Group A");
        addMember(a.getId(), userId, MemberRole.OWNER);
        addMember(a.getId(), peerId, MemberRole.MEMBER);
        // recorded +100, user share +50, user paid +100
        saveExpense(a.getId(), "Trip", "100.00", userId, userId,
                new Object[]{userId, new BigDecimal("50.00")},
                new Object[]{peerId, new BigDecimal("50.00")});

        // Group C: user is a member but NOT a split participant (rule 9: membership is not debt).
        Group c = newGroup("Group C");
        addMember(c.getId(), userId, MemberRole.MEMBER);
        // recorded +90, user share +0, user paid +0
        saveExpense(c.getId(), "Party", "90.00", peerId, peerId,
                new Object[]{peerId, new BigDecimal("45.00")},
                new Object[]{strangerId, new BigDecimal("45.00")});

        // Group B: user is NOT a member — its expenses must never appear (rule 10, cross-group isolation).
        Group b = newGroup("Group B");
        addMember(b.getId(), peerId, MemberRole.OWNER);
        saveExpense(b.getId(), "Private", "500.00", peerId, peerId,
                new Object[]{peerId, new BigDecimal("500.00")});

        // Personal expense involving the user.
        // recorded +200, user share +100, user paid +200
        saveExpense(null, "Dinner", "200.00", userId, userId,
                new Object[]{userId, new BigDecimal("100.00")},
                new Object[]{peerId, new BigDecimal("100.00")});

        // Personal expense NOT involving the user — must never leak into the user's views.
        saveExpense(null, "Other dinner", "100.00", peerId, peerId,
                new Object[]{peerId, new BigDecimal("60.00")},
                new Object[]{strangerId, new BigDecimal("40.00")});

        // Expected: group count 2 recorded 190 share 50 paid 100; personal count 1 recorded 200 share 100 paid 200;
        // combined count 3 recorded 390 share 150 paid 300.
        mockMvc.perform(get("/api/expenses/overview")
                        .header("Authorization", "Bearer " + jwtToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.expenseCount", is(2)))
                .andExpect(jsonPath("$.group.recordedSpend", is(190.00)))
                .andExpect(jsonPath("$.group.yourShare", is(50.00)))
                .andExpect(jsonPath("$.group.youPaid", is(100.00)))
                .andExpect(jsonPath("$.personal.expenseCount", is(1)))
                .andExpect(jsonPath("$.personal.recordedSpend", is(200.00)))
                .andExpect(jsonPath("$.personal.yourShare", is(100.00)))
                .andExpect(jsonPath("$.personal.youPaid", is(200.00)))
                .andExpect(jsonPath("$.combined.expenseCount", is(3)))
                .andExpect(jsonPath("$.combined.recordedSpend", is(390.00)))
                .andExpect(jsonPath("$.combined.yourShare", is(150.00)))
                .andExpect(jsonPath("$.combined.youPaid", is(300.00)));
    }

    @Test
    @DisplayName("Empty dashboard returns all-zero scopes without error")
    void overviewWithNoData() throws Exception {
        mockMvc.perform(get("/api/expenses/overview")
                        .header("Authorization", "Bearer " + jwtToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.expenseCount", is(0)))
                .andExpect(jsonPath("$.group.recordedSpend", is(0.00)))
                .andExpect(jsonPath("$.personal.expenseCount", is(0)))
                .andExpect(jsonPath("$.combined.expenseCount", is(0)))
                .andExpect(jsonPath("$.combined.recordedSpend", is(0.00)));
    }

    @Test
    @DisplayName("GET /api/expenses/overview - 403 without Bearer token")
    void overviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/expenses/overview").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
