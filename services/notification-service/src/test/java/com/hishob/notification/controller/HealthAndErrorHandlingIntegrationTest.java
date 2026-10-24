package com.hishob.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.notification.security.InternalAuthFilter;
import com.hishob.notification.service.NotificationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 regression tests: health endpoint reachability and HTTP error semantics.
 * Covers the Notification Service /health fix — the endpoint must be anonymous while
 * every other protected route keeps its previous authorization behaviour.
 */
@SpringBootTest
class HealthAndErrorHandlingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${hishob.internal.token}")
    private String internalToken;

    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String jwt() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId + "@test.local")
                .claim("userId", userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000L))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("GET /health is anonymous and reports UP")
    void health_IsAnonymousAndReportsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("notification-service"));
    }

    @Test
    @DisplayName("Health needs no JWT and no internal token")
    void health_RequiresNoCredentials() throws Exception {
        mockMvc.perform(get("/health")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isOk());
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
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("Unsupported HTTP method returns 405 instead of 500")
    void unsupportedMethod_ReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));

        mockMvc.perform(delete("/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Invalid body keeps the 400 validation contract")
    void invalidBody_KeepsValidationContract() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("required")));
    }

    @Test
    @DisplayName("Unsupported Content-Type returns 415 instead of 500")
    void unsupportedContentType_ReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"message\":\"phase 1\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("Request content type not supported"))
                .andExpect(content().string(not(containsString("HttpMediaTypeNotSupported"))));

        mockMvc.perform(post("/internal/notifications")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("required")));
    }

    @Test
    @DisplayName("Malformed JSON body returns 400 instead of 500")
    void malformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Non-UUID path variable returns 400 instead of 500")
    void invalidPathType_ReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/notifications/not-a-uuid/read")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(content().string(not(containsString("MethodArgumentTypeMismatch"))));
    }

    @Test
    @DisplayName("Only /health is public: user and internal routes keep 403/401")
    void authnAndAuthzStatusCodes_Preserved() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/not-mapped"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/not-mapped")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/not-mapped")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Unexpected exception returns a safe 500 without internal details")
    void unexpectedException_ReturnsSafeServerError() throws Exception {
        given(notificationService.getUserNotifications(any()))
                .willThrow(new RuntimeException("repository-query-secret"));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(content().string(not(containsString("repository-query-secret"))));
    }
}
