package com.hishob.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.settlement.client.AuthServiceClient;
import com.hishob.settlement.client.BalanceServiceClient;
import com.hishob.settlement.client.GroupServiceClient;
import com.hishob.settlement.dto.InternalBalanceData;
import com.hishob.settlement.dto.PersonalDebtSimplificationResponse;
import com.hishob.settlement.dto.PersonalDebtSimplificationSuggestion;
import com.hishob.settlement.exception.GroupNotFoundException;
import com.hishob.settlement.exception.InvalidRecipientException;
import com.hishob.settlement.exception.PermissionDeniedException;
import com.hishob.settlement.exception.ServiceUnavailableException;
import com.hishob.settlement.security.JwtService;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SettlementControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupServiceClient groupServiceClient;

    @MockitoBean
    private BalanceServiceClient balanceServiceClient;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private static final String TEST_SECRET =
            "d3f4u1t-s3cr3t-k3y-f0r-h1sh0b-auth-s3rv1c3-jwt-t0k3n-g3n3r4t10n-2026";

    private UUID groupId;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        groupId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

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

    private InternalBalanceData balance(UUID userId, String net) {
        InternalBalanceData d = new InternalBalanceData();
        d.setUserId(userId);
        d.setNetBalance(new BigDecimal(net));
        return d;
    }

    private PersonalDebtSimplificationResponse personalSimplification(UUID from, UUID to, String amount) {
        PersonalDebtSimplificationSuggestion suggestion = new PersonalDebtSimplificationSuggestion();
        suggestion.setFromUserId(from);
        suggestion.setToUserId(to);
        suggestion.setAmount(new BigDecimal(amount));

        PersonalDebtSimplificationResponse response = new PersonalDebtSimplificationResponse();
        response.setUserId(from);
        response.setSuggestions(List.of(suggestion));
        return response;
    }

    private void stubHappyPath(UUID caller) {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, caller);
        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                balance(userA, "-500.00"),
                balance(userB, "500.00")
        ));
    }

    // ── 14. Invalid JWT ───────────────────────────────────────────────────────

    @Test
    @DisplayName("14. Invalid JWT → 403 Forbidden")
    void invalidJwt_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No JWT → 403 Forbidden")
    void noJwt_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/settlements"))
                .andExpect(status().isForbidden());
    }

    // ── 15. Non-member accessing group ────────────────────────────────────────

    @Test
    @DisplayName("15. Non-member → 403 Forbidden")
    void nonMember_forbidden() throws Exception {
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("User is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, userA);

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isForbidden());
    }

    // ── 16. Invalid group ─────────────────────────────────────────────────────

    @Test
    @DisplayName("16. Invalid group → 404 Not Found")
    void invalidGroup_notFound() throws Exception {
        doThrow(new GroupNotFoundException("Group not found: " + groupId))
                .when(groupServiceClient).validateGroupExists(groupId);

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isNotFound());
    }

    // ── 17. Invalid recipient ─────────────────────────────────────────────────

    @Test
    @DisplayName("17. Invalid recipient → 400 Bad Request")
    void invalidRecipient_badRequest() throws Exception {
        stubHappyPath(userA);
        doThrow(new InvalidRecipientException("Recipient is not a member of this group"))
                .when(groupServiceClient).validateRecipientMember(groupId, userB);

        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "100.00"));
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── 18. Negative amount ───────────────────────────────────────────────────

    @Test
    @DisplayName("18. Negative amount → 400 Bad Request")
    void negativeAmount_badRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "-50.00"));
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── 19. Zero amount ───────────────────────────────────────────────────────

    @Test
    @DisplayName("19. Zero amount → 400 Bad Request")
    void zeroAmount_badRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "0.00"));
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── 20. Balance Service unavailable ───────────────────────────────────────

    @Test
    @DisplayName("20. Balance Service unavailable → 503 Service Unavailable")
    void balanceServiceUnavailable_503() throws Exception {
        stubHappyPath(userA);
        doThrow(new ServiceUnavailableException("Balance Service unavailable"))
                .when(balanceServiceClient).getGroupBalances(groupId);

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isServiceUnavailable());
    }

    // ── 21. Group Service unavailable ─────────────────────────────────────────

    @Test
    @DisplayName("21. Group Service unavailable → 503 Service Unavailable")
    void groupServiceUnavailable_503() throws Exception {
        doThrow(new ServiceUnavailableException("Group Service unavailable"))
                .when(groupServiceClient).validateGroupExists(groupId);

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isServiceUnavailable());
    }

    // ── 22. Settlement recording ──────────────────────────────────────────────

    @Test
    @DisplayName("22. Record settlement → 201 Created with settlement data")
    void recordSettlement_created() throws Exception {
        stubHappyPath(userA);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, userB);

        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "300.00"));
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fromUserId", is(userA.toString())))
                .andExpect(jsonPath("$.toUserId", is(userB.toString())))
                .andExpect(jsonPath("$.amount", is(300.00)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("Payer == recipient → 400 Bad Request")
    void selfSettlement_badRequest() throws Exception {
        stubHappyPath(userA);

        String body = objectMapper.writeValueAsString(Map.of("toUserId", userA, "amount", "100.00"));
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── 23. Settlement history ────────────────────────────────────────────────

    @Test
    @DisplayName("23. Settlement history → 200 with list")
    void settlementHistory_ok() throws Exception {
        // First record a settlement
        stubHappyPath(userA);
        doNothing().when(groupServiceClient).validateRecipientMember(groupId, userB);
        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "150.00"));
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Then fetch history
        stubHappyPath(userA);
        mockMvc.perform(get("/api/groups/" + groupId + "/settlements/history")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].fromUserId").exists())
                .andExpect(jsonPath("$[0].toUserId").exists())
                .andExpect(jsonPath("$[0].amount").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    // ── 24. Unauthorized access ───────────────────────────────────────────────

    @Test
    @DisplayName("24. Unauthorized access to history → 403 Forbidden")
    void unauthorizedHistory_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/settlements/history"))
                .andExpect(status().isForbidden());
    }

    // ── Suggestions happy path ────────────────────────────────────────────────

    @Test
    @DisplayName("Suggestions: valid member with balances → 200 with suggestions")
    void getSuggestions_ok() throws Exception {
        stubHappyPath(userA);
        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                balance(userA, "300.00"),
                balance(userB, "-300.00")
        ));

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].fromUserId", is(userB.toString())))
                .andExpect(jsonPath("$[0].toUserId", is(userA.toString())))
                .andExpect(jsonPath("$[0].amount", is(300.00)));
    }

    @Test
    @DisplayName("Suggestions: all settled → 200 with empty list")
    void getSuggestions_allSettled() throws Exception {
        stubHappyPath(userA);
        when(balanceServiceClient.getGroupBalances(groupId)).thenReturn(List.of(
                balance(userA, "0.00"),
                balance(userB, "0.00")
        ));

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Personal settlement: valid JWT records completed personal payment")
    void personalSettlement_ok() throws Exception {
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(balanceServiceClient.getPersonalSimplification(userA))
                .thenReturn(personalSimplification(userA, userB, "500.00"));

        String body = objectMapper.writeValueAsString(Map.of(
                "toUserId", userB,
                "amount", "200.00",
                "notes", "Paid via UPI"
        ));

        mockMvc.perform(post("/api/personal-settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromUserId", is(userA.toString())))
                .andExpect(jsonPath("$.toUserId", is(userB.toString())))
                .andExpect(jsonPath("$.amount", is(200.00)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.notes", is("Paid via UPI")));
    }

    @Test
    @DisplayName("Personal settlement: wrong recipient rejected")
    void personalSettlement_wrongRecipient_badRequest() throws Exception {
        UUID userC = UUID.randomUUID();
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(balanceServiceClient.getPersonalSimplification(userA))
                .thenReturn(personalSimplification(userA, userC, "500.00"));

        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "200.00"));

        mockMvc.perform(post("/api/personal-settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Personal settlement history: returns only personal settlements involving current user")
    void personalSettlementHistory_ok() throws Exception {
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(balanceServiceClient.getPersonalSimplification(userA))
                .thenReturn(personalSimplification(userA, userB, "500.00"));

        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "200.00"));
        mockMvc.perform(post("/api/personal-settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/personal-settlements/history")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromUserId", is(userA.toString())))
                .andExpect(jsonPath("$[0].toUserId", is(userB.toString())));
    }

    @Test
    @DisplayName("Friend settlement history: accepted friend sees pairwise personal history")
    void friendSettlementHistory_ok() throws Exception {
        doNothing().when(authServiceClient).validateAcceptedFriendship(userA, userB);
        when(balanceServiceClient.getPersonalSimplification(userA))
                .thenReturn(personalSimplification(userA, userB, "500.00"));

        String body = objectMapper.writeValueAsString(Map.of("toUserId", userB, "amount", "200.00"));
        mockMvc.perform(post("/api/personal-settlements")
                        .header("Authorization", "Bearer " + jwt(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/friends/" + userB + "/settlements")
                        .header("Authorization", "Bearer " + jwt(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromUserId", is(userA.toString())))
                .andExpect(jsonPath("$[0].toUserId", is(userB.toString())));
    }

}
