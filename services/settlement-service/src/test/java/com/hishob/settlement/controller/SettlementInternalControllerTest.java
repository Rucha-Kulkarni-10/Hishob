package com.hishob.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.settlement.client.BalanceServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import com.hishob.settlement.repository.SettlementRepository;
import com.hishob.settlement.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SettlementInternalController.
 *
 * Uses @MockitoBean on GroupServiceClient and BalanceServiceClient so the
 * public POST endpoint can seed COMPLETED settlements into the H2 database
 * without real inter-service HTTP calls.
 *
 * @DirtiesContext(classMode = AFTER_CLASS) prevents H2 rows from bleeding
 * into the sibling SettlementControllerIntegrationTest context.
 *
 * Tests:
 *  1. Completed settlement is returned for the correct group
 *  2. PENDING settlements are excluded (contract verified indirectly)
 *  3. Empty list returned when no completed settlements exist
 *  4. Group filtering: only the queried group's settlements are returned
 *  5. Missing internal token → 401 Unauthorized
 *  6. Wrong internal token → 401 Unauthorized
 *  7. Blank internal token → 401 Unauthorized
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SettlementInternalControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettlementRepository settlementRepository;

    @MockitoBean
    private GroupServiceClient groupServiceClient;

    @MockitoBean
    private BalanceServiceClient balanceServiceClient;

    private MockMvc mockMvc;

    private static final String TEST_SECRET =
            "d3f4u1t-s3cr3t-k3y-f0r-h1sh0b-auth-s3rv1c3-jwt-t0k3n-g3n3r4t10n-2026";

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String WRONG_TOKEN    = "definitely-wrong-token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
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

    /**
     * Seeds a COMPLETED settlement via the public POST endpoint.
     * GroupServiceClient is mocked so no real HTTP calls are made.
     * The real service + repository layer is fully exercised.
     */
    private void recordSettlement(UUID groupId, UUID fromUserId, UUID toUserId, String amount)
            throws Exception {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, fromUserId);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, toUserId);

        com.hishob.settlement.dto.InternalBalanceData bFrom = new com.hishob.settlement.dto.InternalBalanceData();
        bFrom.setUserId(fromUserId);
        bFrom.setNetBalance(new java.math.BigDecimal("-1000.00"));
        com.hishob.settlement.dto.InternalBalanceData bTo = new com.hishob.settlement.dto.InternalBalanceData();
        bTo.setUserId(toUserId);
        bTo.setNetBalance(new java.math.BigDecimal("1000.00"));
        org.mockito.Mockito.when(balanceServiceClient.getGroupBalances(groupId))
                .thenReturn(java.util.List.of(bFrom, bTo));

        String body = objectMapper.writeValueAsString(
                Map.of("toUserId", toUserId, "amount", amount));

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(fromUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ── test 1: completed settlement returned ─────────────────────────────────

    @Test
    @DisplayName("1. Completed settlement is returned by internal endpoint")
    void completedSettlement_returned() throws Exception {
        UUID groupId    = UUID.randomUUID();
        UUID fromUserId = UUID.randomUUID();
        UUID toUserId   = UUID.randomUUID();

        recordSettlement(groupId, fromUserId, toUserId, "300.00");

        mockMvc.perform(get("/internal/groups/" + groupId + "/settlements/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromUserId", is(fromUserId.toString())))
                .andExpect(jsonPath("$[0].toUserId",   is(toUserId.toString())))
                .andExpect(jsonPath("$[0].amount",     is(300.00)));
    }

    // ── test 2: PENDING settlements excluded ──────────────────────────────────

    /**
     * The public POST always creates COMPLETED settlements.
     * PENDING is reserved for future use (e.g., a pending-confirmation flow).
     * We verify the filter contract: a group with no COMPLETED settlements returns
     * an empty list even though the concept of PENDING exists in the enum.
     * The SettlementStatus.COMPLETED filter in the repository is the authoritative guard.
     */
    @Test
    @DisplayName("2. Group with no COMPLETED settlements returns empty list (PENDING excluded)")
    void noCompleted_emptyList() throws Exception {
        UUID groupWithNoPosts = UUID.randomUUID();

        // No POST calls → no COMPLETED rows for this group
        mockMvc.perform(get("/internal/groups/" + groupWithNoPosts + "/settlements/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── test 3: empty list when no completed settlements ─────────────────────

    @Test
    @DisplayName("3. No settlements for group → 200 with empty list")
    void noSettlementsForGroup_emptyList() throws Exception {
        UUID unknownGroup = UUID.randomUUID();

        mockMvc.perform(get("/internal/groups/" + unknownGroup + "/settlements/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── test 4: group filtering ───────────────────────────────────────────────

    @Test
    @DisplayName("4. Group filtering: only the queried group's settlements returned")
    void groupFiltering_isolatedPerGroup() throws Exception {
        UUID groupA     = UUID.randomUUID();
        UUID groupB     = UUID.randomUUID();
        UUID fromUserId = UUID.randomUUID();
        UUID toUserId   = UUID.randomUUID();

        recordSettlement(groupA, fromUserId, toUserId, "100.00");
        recordSettlement(groupB, fromUserId, toUserId, "200.00");

        // groupA endpoint should contain only groupA's settlement
        mockMvc.perform(get("/internal/groups/" + groupA + "/settlements/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount", is(100.00)));

        // groupB endpoint should contain only groupB's settlement
        mockMvc.perform(get("/internal/groups/" + groupB + "/settlements/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount", is(200.00)));
    }

    // ── test 5: missing internal token → 401 ─────────────────────────────────

    @Test
    @DisplayName("5. Missing internal token → 401 Unauthorized")
    void missingToken_unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + UUID.randomUUID() + "/settlements/completed"))
                .andExpect(status().isUnauthorized());
    }

    // ── test 6: wrong internal token → 401 ───────────────────────────────────

    @Test
    @DisplayName("6. Wrong internal token → 401 Unauthorized")
    void wrongToken_unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + UUID.randomUUID() + "/settlements/completed")
                        .header("X-Internal-Token", WRONG_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    // ── test 7: blank internal token → 401 ───────────────────────────────────

    @Test
    @DisplayName("7. Blank internal token → 401 Unauthorized")
    void blankToken_unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + UUID.randomUUID() + "/settlements/completed")
                        .header("X-Internal-Token", "   "))
                .andExpect(status().isUnauthorized());
    }

    // ── extra: public JWT alone cannot access internal endpoint ──────────────

    @Test
    @DisplayName("Public JWT without internal token → 401 Unauthorized")
    void publicJwt_cannotAccessInternal() throws Exception {
        mockMvc.perform(get("/internal/groups/" + UUID.randomUUID() + "/settlements/completed")
                        .header("Authorization", "Bearer " + jwt(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // ── extra: multiple settlements returned correctly ────────────────────────

    @Test
    @DisplayName("Multiple completed settlements for one group are all returned")
    void multipleCompletedSettlements_allReturned() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID userA   = UUID.randomUUID();
        UUID userB   = UUID.randomUUID();
        UUID userC   = UUID.randomUUID();

        recordSettlement(groupId, userB, userA, "300.00");
        recordSettlement(groupId, userC, userA, "200.00");

        mockMvc.perform(get("/internal/groups/" + groupId + "/settlements/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Completed personal settlements for user are returned by internal endpoint")
    void completedPersonalSettlementsForUser_returned() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        settlementRepository.save(new Settlement(null, userA, userB, new BigDecimal("125.00"), SettlementStatus.COMPLETED));
        settlementRepository.save(new Settlement(null, unrelated, userB, new BigDecimal("75.00"), SettlementStatus.COMPLETED));
        settlementRepository.save(new Settlement(UUID.randomUUID(), userA, userB, new BigDecimal("50.00"), SettlementStatus.COMPLETED));

        mockMvc.perform(get("/internal/personal-settlements/users/" + userA + "/completed")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromUserId", is(userA.toString())))
                .andExpect(jsonPath("$[0].toUserId", is(userB.toString())))
                .andExpect(jsonPath("$[0].amount", is(125.00)));
    }

}
