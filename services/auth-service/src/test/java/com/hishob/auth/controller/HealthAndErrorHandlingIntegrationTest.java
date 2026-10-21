package com.hishob.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.repository.UserRepository;
import com.hishob.auth.security.InternalAuthFilter;
import com.hishob.auth.service.FriendshipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 regression tests: health endpoint reachability and HTTP error semantics.
 * Auth Service resolves JWT subjects against persisted users, so these tests register a
 * throwaway user in the H2 test datasource to obtain a real token.
 */
@SpringBootTest
class HealthAndErrorHandlingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FriendshipService friendshipService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
    }

    private String registerAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@health.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Health Check User", email, "password123"))))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return "Bearer " + response.getAccessToken();
    }

    @Test
    @DisplayName("GET /health is anonymous and reports UP")
    void health_IsAnonymousAndReportsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("auth-service"));
    }

    @Test
    @DisplayName("Actuator endpoints are not reachable anonymously")
    void actuatorEndpoints_AreNotPublic() throws Exception {
        for (String path : new String[]{
                "/actuator", "/actuator/health", "/actuator/info",
                "/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/mappings"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Unknown path returns 404 instead of 500")
    void unknownPath_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/not-a-real-endpoint")
                        .header("Authorization", registerAndLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("Unsupported HTTP method returns 405 instead of 500")
    void unsupportedMethod_ReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/auth/register"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));

        mockMvc.perform(delete("/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Unsupported Content-Type returns 415 instead of 500")
    void unsupportedContentType_ReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"Health Check User\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("Request content type not supported"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(content().string(not(containsString("HttpMediaTypeNotSupported"))));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.name").isNotEmpty());
    }

    @Test
    @DisplayName("Invalid body keeps the 400 validation contract")
    void invalidBody_KeepsValidationContract() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());
    }

    @Test
    @DisplayName("Malformed JSON body returns 400 instead of 500")
    void malformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Non-UUID path variable returns 400 instead of 500")
    void invalidPathType_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/users/not-a-uuid/exists"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(content().string(not(containsString("MethodArgumentTypeMismatch"))));
    }

    @Test
    @DisplayName("Missing authentication keeps 403 and internal paths keep 401")
    void authnAndAuthzStatusCodes_Preserved() throws Exception {
        mockMvc.perform(get("/api/friends/requests/incoming"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/friends/requests/incoming")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/not-mapped"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/not-mapped")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unexpected exception returns a safe 500 without internal details")
    void unexpectedException_ReturnsSafeServerError() throws Exception {
        given(friendshipService.getIncomingRequests(anyString()))
                .willThrow(new RuntimeException("friendship-query-secret"));

        mockMvc.perform(get("/api/friends/requests/incoming")
                        .header("Authorization", registerAndLogin()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(content().string(not(containsString("friendship-query-secret"))));
    }
}
