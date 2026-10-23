package com.hishob.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for NotificationInternalController.
 *
 * Tests internal API endpoint:
 *  - POST /internal/notifications (create notification)
 *
 * Security tests:
 *  - Unauthorized access without internal token
 *  - Wrong internal token
 *  - Blank internal token
 *
 * Validation tests:
 *  - Missing required fields
 *  - Invalid notification type
 *  - Message too long
 *
 * @DirtiesContext ensures H2 database is cleaned between test classes.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationInternalControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String INTERNAL_TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final String WRONG_TOKEN = "definitely-wrong-token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ── test 1: successfully create notification ─────────────────────────────

    @Test
    @DisplayName("1. Create notification via internal API succeeds with valid request")
    void createNotification_success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "groupId", groupId.toString(),
                "type", NotificationType.GROUP_MEMBER_ADDED.name(),
                "message", "You were added to the group"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.groupId", is(groupId.toString())))
                .andExpect(jsonPath("$.type", is(NotificationType.GROUP_MEMBER_ADDED.name())))
                .andExpect(jsonPath("$.message", is("You were added to the group")))
                .andExpect(jsonPath("$.read", is(false)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.readAt", nullValue()));
    }

    // ── test 2: create notification with metadata ───────────────────────────

    @Test
    @DisplayName("2. Create notification with metadata succeeds")
    void createNotification_withMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        Map<String, Object> metadata = Map.of(
                "expenseId", UUID.randomUUID().toString(),
                "amount", "150.00",
                "action", "created"
        );

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "groupId", groupId.toString(),
                "type", NotificationType.EXPENSE_ADDED.name(),
                "message", "New expense added: $150.00",
                "metadata", metadata
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is(NotificationType.EXPENSE_ADDED.name())))
                .andExpect(jsonPath("$.metadata.expenseId", is(metadata.get("expenseId"))))
                .andExpect(jsonPath("$.metadata.amount", is("150.00")))
                .andExpect(jsonPath("$.metadata.action", is("created")));
    }

    // ── test 3: create notification without groupId (null) ──────────────────

    @Test
    @DisplayName("3. Create notification without groupId succeeds")
    void createNotification_nullGroupId() throws Exception {
        UUID userId = UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "type", NotificationType.EXPENSE_DELETED.name(),
                "message", "Your expense was deleted"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId", nullValue()))
                .andExpect(jsonPath("$.type", is(NotificationType.EXPENSE_DELETED.name())));
    }

    // ── test 4: validation errors for missing required fields ───────────────

    @Test
    @DisplayName("4. Missing userId returns 400 Bad Request")
    void createNotification_missingUserId() throws Exception {
        Map<String, Object> request = Map.of(
                "type", NotificationType.GROUP_MEMBER_ADDED.name(),
                "message", "Test message"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("5. Missing type returns 400 Bad Request")
    void createNotification_missingType() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "message", "Test message"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Missing message returns 400 Bad Request")
    void createNotification_missingMessage() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "type", NotificationType.EXPENSE_ADDED.name()
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── test 7: validation error for message too long ───────────────────────

    @Test
    @DisplayName("7. Message longer than 500 characters returns 400 Bad Request")
    void createNotification_messageTooLong() throws Exception {
        String longMessage = "A".repeat(501); // 501 characters

        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "type", NotificationType.EXPENSE_UPDATED.name(),
                "message", longMessage
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    // ── test 8: invalid notification type ────────────────────────────────────

    @Test
    @DisplayName("8. Invalid notification type returns 400 Bad Request")
    void createNotification_invalidType() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "type", "INVALID_TYPE",
                "message", "Test message"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── test 9: missing internal token returns 401 ──────────────────────────

    @Test
    @DisplayName("9. Missing internal token returns 401 Unauthorized")
    void createNotification_missingToken() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "type", NotificationType.EXPENSE_ADDED.name(),
                "message", "Test message"
        );

        mockMvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── test 10: wrong internal token returns 401 ───────────────────────────

    @Test
    @DisplayName("10. Wrong internal token returns 401 Unauthorized")
    void createNotification_wrongToken() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "type", NotificationType.SETTLEMENT_RECORDED.name(),
                "message", "Test message"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", WRONG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── test 11: blank internal token returns 401 ───────────────────────────

    @Test
    @DisplayName("11. Blank internal token returns 401 Unauthorized")
    void createNotification_blankToken() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", UUID.randomUUID().toString(),
                "type", NotificationType.EXPENSE_UPDATED.name(),
                "message", "Test message"
        );

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── test 12: all notification types work ────────────────────────────────

    @Test
    @DisplayName("12. All notification types can be created successfully")
    void createNotification_allTypes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        for (NotificationType type : NotificationType.values()) {
            Map<String, Object> request = Map.of(
                    "userId", userId.toString(),
                    "groupId", groupId.toString(),
                    "type", type.name(),
                    "message", "Test message for " + type.name()
            );

            mockMvc.perform(post("/internal/notifications")
                            .header("X-Internal-Token", INTERNAL_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type", is(type.name())));
        }
    }

    // ── test 13: malformed JSON returns 400 ─────────────────────────────────

    @Test
    @DisplayName("13. Malformed JSON returns 400 Bad Request")
    void createNotification_malformedJson() throws Exception {
        String malformedJson = "{ \"userId\": \"invalid-uuid\", \"type\": ";

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    // ── test 15: personal notifications with null groupId ───────────────────

    @Test
    @DisplayName("15. Personal notification types with null groupId can be created successfully")
    void createNotification_personalTypesNullGroupId() throws Exception {
        UUID userId = UUID.randomUUID();

        NotificationType[] personalTypes = {
                NotificationType.PERSONAL_EXPENSE_ADDED,
                NotificationType.PERSONAL_EXPENSE_UPDATED,
                NotificationType.PERSONAL_EXPENSE_DELETED,
                NotificationType.EXPENSE_COMMENT_ADDED,
                NotificationType.PERSONAL_SETTLEMENT_RECORDED
        };

        for (NotificationType type : personalTypes) {
            Map<String, Object> request = Map.of(
                    "userId", userId.toString(),
                    "type", type.name(),
                    "message", "Personal event: " + type.name(),
                    "metadata", Map.of("personal", "true")
            );

            mockMvc.perform(post("/internal/notifications")
                            .header("X-Internal-Token", INTERNAL_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type", is(type.name())))
                    .andExpect(jsonPath("$.groupId", nullValue()));
        }
    }

}