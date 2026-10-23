package com.hishob.expense.controller;

import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.service.GroupService;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.client.SettlementServiceClient;
import com.hishob.expense.security.InternalAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ExpenseInternalControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @MockitoBean
    private SettlementServiceClient settlementServiceClient;

    @MockitoBean
    private NotificationServiceClient notificationServiceClient;

    private MockMvc mockMvc;

    private static final String VALID_TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final String WRONG_TOKEN = "wrong-token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Internal groupExpenses — valid token returns 200")
    void testGroupExpenses_ValidToken_Success() throws Exception {
        UUID groupId = UUID.randomUUID();
        mockMvc.perform(get("/internal/groups/" + groupId + "/expenses")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Internal groupExpenses — missing token returns 401")
    void testGroupExpenses_MissingToken_Unauthorized() throws Exception {
        UUID groupId = UUID.randomUUID();
        mockMvc.perform(get("/internal/groups/" + groupId + "/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal groupExpenses — wrong token returns 401")
    void testGroupExpenses_WrongToken_Unauthorized() throws Exception {
        UUID groupId = UUID.randomUUID();
        mockMvc.perform(get("/internal/groups/" + groupId + "/expenses")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, WRONG_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal groupExpenses — blank token returns 401")
    void testGroupExpenses_BlankToken_Unauthorized() throws Exception {
        UUID groupId = UUID.randomUUID();
        mockMvc.perform(get("/internal/groups/" + groupId + "/expenses")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal personalExpenses — valid token returns 200")
    void testPersonalExpenses_ValidToken_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/internal/personal-expenses/users/" + userId)
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Internal personalExpenses — missing token returns 401")
    void testPersonalExpenses_MissingToken_Unauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/internal/personal-expenses/users/" + userId))
                .andExpect(status().isUnauthorized());
    }
}
