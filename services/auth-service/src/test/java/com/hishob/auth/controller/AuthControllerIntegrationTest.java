package com.hishob.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.LoginRequest;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/register - Should register user and return 201 Created with JWT")
    void testRegister_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Test User"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register - Should fail with 400 when validation constraints fail")
    void testRegister_ValidationFailure() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest("", "not-an-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 409 Conflict when registering duplicate email")
    void testRegister_DuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("User One", "duplicate@example.com", "password123");

        // First registration
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate registration
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email address is already registered: duplicate@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should authenticate and return 200 OK with token")
    void testLogin_Success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Login User", "login@example.com", "mypassword");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login@example.com", "mypassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should fail with 401 Unauthorized for incorrect password")
    void testLogin_InvalidPassword() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Login User", "login2@example.com", "mypassword");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login2@example.com", "wrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("GET /api/auth/me - Should return current user profile when authenticated with JWT")
    void testGetMe_Success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Auth User", "me@example.com", "mypassword");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String token = authResponse.getAccessToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.name").value("Auth User"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/me - Should reject unauthenticated requests with 403 Forbidden")
    void testGetMe_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/auth/me - Should reject invalid/forged JWT tokens with 403 Forbidden")
    void testGetMe_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.string"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/auth/users/{userId} - Returns identity only, never contact details")
    void testGetUserById_Success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Target User", "target@example.com", "mypassword");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String token = authResponse.getAccessToken();
        String targetUserId = authResponse.getUser().getId().toString();

        mockMvc.perform(get("/api/auth/users/" + targetUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUserId))
                .andExpect(jsonPath("$.name").value("Target User"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/users/{userId} - Should reject unauthenticated requests")
    void testGetUserById_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/users/" + java.util.UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/auth/users/{userId} - Should return 404 for non-existent user")
    void testGetUserById_NotFound() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Auth User", "authuser@example.com", "mypassword");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String token = authResponse.getAccessToken();

        mockMvc.perform(get("/api/auth/users/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/auth/users/batch - Service token returns names but never contact details")
    void testGetUsersBatch_WithInternalToken() throws Exception {
        RegisterRequest user1 = new RegisterRequest("Internal Target", "internal@example.com", "password123");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(res1.getResponse().getContentAsString(), AuthResponse.class);
        java.util.List<java.util.UUID> requestedIds = java.util.List.of(auth1.getUser().getId());

        mockMvc.perform(post("/api/auth/users/batch")
                        .header("X-Internal-Token", "hishob-test-internal-token-for-automated-tests-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestedIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Internal Target"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/users/batch - A user JWT alone cannot reach the service lookup")
    void testGetUsersBatch_WithJwtOnlyIsRejected() throws Exception {
        RegisterRequest user1 = new RegisterRequest("User One", "user1@example.com", "password123");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(res1.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(post("/api/auth/users/batch")
                        .header("Authorization", "Bearer " + auth1.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.List.of(auth1.getUser().getId()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/users/batch - Should reject unauthenticated requests")
    void testGetUsersBatch_Unauthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.List.of(java.util.UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

}
