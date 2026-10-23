package com.hishob.balance.controller;

import com.hishob.balance.client.AuthServiceClient;
import com.hishob.balance.client.ExpenseServiceClient;
import com.hishob.balance.client.GroupServiceClient;
import com.hishob.balance.client.SettlementServiceClient;
import com.hishob.balance.security.InternalAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BalanceInternalControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private GroupServiceClient groupServiceClient;

    @MockitoBean
    private ExpenseServiceClient expenseServiceClient;

    @MockitoBean
    private SettlementServiceClient settlementServiceClient;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private static final String VALID_TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final String WRONG_TOKEN = "wrong-token";

    private UUID groupId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        groupId = UUID.randomUUID();
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        when(groupServiceClient.getGroupMemberIds(groupId)).thenReturn(List.of(UUID.randomUUID()));
        when(expenseServiceClient.getGroupExpenses(groupId)).thenReturn(List.of());
        when(settlementServiceClient.getCompletedSettlements(groupId)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Internal groupBalances — valid token returns 200")
    void testGroupBalances_ValidToken_Success() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/balances")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Internal groupBalances — missing token returns 401")
    void testGroupBalances_MissingToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/balances"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal groupBalances — wrong token returns 401")
    void testGroupBalances_WrongToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/balances")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, WRONG_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal groupBalances — blank token returns 401")
    void testGroupBalances_BlankToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/balances")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Normal user endpoint rejecting internal token without JWT")
    void testUserEndpoint_InternalTokenWithoutJwt_Forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isForbidden());
    }
}
