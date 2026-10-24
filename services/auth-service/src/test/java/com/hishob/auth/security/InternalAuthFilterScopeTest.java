package com.hishob.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.dto.ValidateAcceptedFriendshipsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class InternalAuthFilterScopeTest {

    private static final String WRONG_TOKEN = "definitely-not-the-internal-token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InternalAuthFilter internalAuthFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${hishob.internal.token}")
    private String internalToken;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Internal token does not authenticate GET /api/auth/me — 403")
    void testInternalTokenDoesNotAuthenticateMeEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal token does not authenticate GET /api/friends — 403")
    void testInternalTokenDoesNotAuthenticateFriendshipEndpoint() throws Exception {
        mockMvc.perform(get("/api/friends")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal token does not authenticate a mapped protected endpoint — 403")
    void testInternalTokenDoesNotAuthenticateUserLookupEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/users/" + UUID.randomUUID())
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal token does not authenticate the consolidated GET /api/notifications — 403")
    void testInternalTokenDoesNotAuthenticateNotificationsEndpoint() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Legitimate internal call still succeeds with the internal token — 200")
    void testLegitimateInternalCallStillWorks() throws Exception {
        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validValidateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Internal call without a token is still rejected — 401")
    void testInternalCallWithoutTokenStillRejected() throws Exception {
        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validValidateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal call with a wrong token is still rejected — 401")
    void testInternalCallWithWrongTokenStillRejected() throws Exception {
        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, WRONG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validValidateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal call with a blank token is still rejected — 401")
    void testInternalCallWithBlankTokenStillRejected() throws Exception {
        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validValidateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/users/batch accepts the internal token — 200")
    void testBatchEndpointStillAcceptsInternalToken() throws Exception {
        mockMvc.perform(post("/api/auth/users/batch")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(UUID.randomUUID()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/auth/users/batch without the internal token is rejected — 401")
    void testBatchEndpointWithoutInternalTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/users/batch with a wrong internal token is rejected — 401")
    void testBatchEndpointWithWrongInternalTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/users/batch")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, WRONG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    private ValidateAcceptedFriendshipsRequest validValidateRequest() {
        return new ValidateAcceptedFriendshipsRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));
    }
}
