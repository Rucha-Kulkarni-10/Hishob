package com.hishob.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.dto.ValidateAcceptedFriendshipsRequest;
import com.hishob.auth.security.InternalAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class FriendshipInternalControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String VALID_TOKEN = "dev-internal-token-hishob-service-2026";
    private static final String WRONG_TOKEN = "wrong-internal-token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Internal validateAcceptedFriendships — valid token returns 200")
    void testValidateAcceptedFriendships_ValidToken_Success() throws Exception {
        ValidateAcceptedFriendshipsRequest request = new ValidateAcceptedFriendshipsRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID())
        );

        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Internal validateAcceptedFriendships — missing token returns 401")
    void testValidateAcceptedFriendships_MissingToken_Unauthorized() throws Exception {
        ValidateAcceptedFriendshipsRequest request = new ValidateAcceptedFriendshipsRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID())
        );

        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal validateAcceptedFriendships — wrong token returns 401")
    void testValidateAcceptedFriendships_WrongToken_Unauthorized() throws Exception {
        ValidateAcceptedFriendshipsRequest request = new ValidateAcceptedFriendshipsRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID())
        );

        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, WRONG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal validateAcceptedFriendships — blank token returns 401")
    void testValidateAcceptedFriendships_BlankToken_Unauthorized() throws Exception {
        ValidateAcceptedFriendshipsRequest request = new ValidateAcceptedFriendshipsRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID())
        );

        mockMvc.perform(post("/internal/friendships/accepted/validate")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
