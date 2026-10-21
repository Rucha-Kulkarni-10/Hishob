package com.hishob.auth.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.notification.enums.NotificationType;
import com.hishob.auth.notification.repository.NotificationRepository;
import com.hishob.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for NotificationController in consolidated Auth Service.
 *
 * Tests public API endpoints:
 *  - GET /api/notifications (get user's notifications)
 *  - GET /api/notifications/unread-count (get unread count)
 *  - PUT /api/notifications/{id}/read (mark as read)
 *  - PATCH /api/notifications/{id}/read (alternative mark as read)
 *
 * Security tests:
 *  - Unauthorized access without JWT
 *  - User cannot access another user's notification
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private MockMvc mockMvc;

    private static final String INTERNAL_TOKEN = "dev-internal-token-hishob-service-2026";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record TestUser(UUID id, String token) {}

    private TestUser createTestUser(String name, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(name, email, "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return new TestUser(response.getUser().getId(), response.getAccessToken());
    }

    private String createNotification(UUID userId, UUID groupId, NotificationType type, String message)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId.toString());
        if (groupId != null) {
            request.put("groupId", groupId.toString());
        }
        request.put("type", type.name());
        request.put("message", message);

        String response = mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        return (String) responseMap.get("id");
    }

    // ── test 1: get user notifications returns all notifications ─────────────

    @Test
    @DisplayName("1. Get user notifications returns all notifications ordered by creation time")
    void getUserNotifications_success() throws Exception {
        TestUser user = createTestUser("User One", "user1@example.com");
        UUID groupId = UUID.randomUUID();

        createNotification(user.id(), groupId, NotificationType.GROUP_MEMBER_ADDED, "You were added to group");
        createNotification(user.id(), groupId, NotificationType.EXPENSE_ADDED, "New expense added");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type", is(NotificationType.EXPENSE_ADDED.name())))
                .andExpect(jsonPath("$[0].message", is("New expense added")))
                .andExpect(jsonPath("$[0].read", is(false)))
                .andExpect(jsonPath("$[1].type", is(NotificationType.GROUP_MEMBER_ADDED.name())))
                .andExpect(jsonPath("$[1].message", is("You were added to group")))
                .andExpect(jsonPath("$[1].read", is(false)));
    }

    // ── test 2: get notifications returns empty list for new user ────────────

    @Test
    @DisplayName("2. Get notifications returns empty list when user has no notifications")
    void getUserNotifications_emptyList() throws Exception {
        TestUser user = createTestUser("User Two", "user2@example.com");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── test 3: get unread count returns correct count ───────────────────────

    @Test
    @DisplayName("3. Get unread count returns correct count")
    void getUnreadCount_success() throws Exception {
        TestUser user = createTestUser("User Three", "user3@example.com");
        UUID groupId = UUID.randomUUID();

        createNotification(user.id(), groupId, NotificationType.EXPENSE_ADDED, "Expense 1");
        createNotification(user.id(), groupId, NotificationType.EXPENSE_UPDATED, "Expense 2");
        createNotification(user.id(), groupId, NotificationType.SETTLEMENT_RECORDED, "Settlement 1");

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(3)));
    }

    // ── test 4: get unread count returns zero for new user ───────────────────

    @Test
    @DisplayName("4. Get unread count returns zero when no notifications")
    void getUnreadCount_zero() throws Exception {
        TestUser user = createTestUser("User Four", "user4@example.com");

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(0)));
    }

    // ── test 5: mark notification as read with PUT ───────────────────────────

    @Test
    @DisplayName("5. Mark notification as read with PUT updates isRead and readAt")
    void markAsRead_put_success() throws Exception {
        TestUser user = createTestUser("User Five", "user5@example.com");
        UUID groupId = UUID.randomUUID();

        String notificationId = createNotification(user.id(), groupId, NotificationType.EXPENSE_ADDED, "Test notification");

        mockMvc.perform(put("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(notificationId)))
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.readAt", notNullValue()));

        // Verify unread count decreased
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(0)));
    }

    // ── test 6: mark notification as read with PATCH ─────────────────────────

    @Test
    @DisplayName("6. Mark notification as read with PATCH also works")
    void markAsRead_patch_success() throws Exception {
        TestUser user = createTestUser("User Six", "user6@example.com");
        UUID groupId = UUID.randomUUID();

        String notificationId = createNotification(user.id(), groupId, NotificationType.SETTLEMENT_RECORDED, "Test");

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.readAt", notNullValue()));
    }

    // ── test 7: cannot mark another user's notification as read ──────────────

    @Test
    @DisplayName("7. Cannot mark another user's notification as read - returns 404")
    void markAsRead_wrongUser_notFound() throws Exception {
        TestUser userA = createTestUser("User Seven A", "user7a@example.com");
        TestUser userB = createTestUser("User Seven B", "user7b@example.com");
        UUID groupId = UUID.randomUUID();

        String notificationId = createNotification(userA.id(), groupId, NotificationType.EXPENSE_ADDED, "Test");

        mockMvc.perform(put("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Notification not found or does not belong to user")));
    }

    // ── test 8: mark non-existent notification returns 404 ───────────────────

    @Test
    @DisplayName("8. Mark non-existent notification as read returns 404")
    void markAsRead_notFound() throws Exception {
        TestUser user = createTestUser("User Eight", "user8@example.com");
        UUID fakeNotificationId = UUID.randomUUID();

        mockMvc.perform(put("/api/notifications/" + fakeNotificationId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // ── test 9: unauthorized access without JWT ──────────────────────────────

    @Test
    @DisplayName("9. GET /api/notifications without JWT returns 403")
    void getNotifications_unauthorized() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. GET /api/notifications/unread-count without JWT returns 403")
    void getUnreadCount_unauthorized() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. PUT /api/notifications/{id}/read without JWT returns 403")
    void markAsRead_unauthorized() throws Exception {
        mockMvc.perform(put("/api/notifications/" + UUID.randomUUID() + "/read"))
                .andExpect(status().isForbidden());
    }

    // ── test 12: notification ordering (newest first) ────────────────────────

    @Test
    @DisplayName("12. Notifications are ordered by creation time (newest first)")
    void notifications_orderedByCreatedAtDesc() throws Exception {
        TestUser user = createTestUser("User Twelve", "user12@example.com");
        UUID groupId = UUID.randomUUID();

        createNotification(user.id(), groupId, NotificationType.EXPENSE_ADDED, "First");
        Thread.sleep(10); // Ensure different timestamps
        createNotification(user.id(), groupId, NotificationType.EXPENSE_UPDATED, "Second");
        Thread.sleep(10);
        createNotification(user.id(), groupId, NotificationType.EXPENSE_DELETED, "Third");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].message", is("Third")))
                .andExpect(jsonPath("$[1].message", is("Second")))
                .andExpect(jsonPath("$[2].message", is("First")));
    }

    // ── test 13: marking already read notification is idempotent ─────────────

    @Test
    @DisplayName("13. Marking already read notification is idempotent")
    void markAsRead_idempotent() throws Exception {
        TestUser user = createTestUser("User Thirteen", "user13@example.com");
        UUID groupId = UUID.randomUUID();

        String notificationId = createNotification(user.id(), groupId, NotificationType.EXPENSE_ADDED, "Test");

        // Mark as read first time
        String firstResponse = mockMvc.perform(put("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> firstMap = objectMapper.readValue(firstResponse, Map.class);
        String firstReadAt = (String) firstMap.get("readAt");

        // Mark as read second time
        mockMvc.perform(put("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(result -> {
                    Map<String, Object> secondMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
                    String secondReadAt = (String) secondMap.get("readAt");
                    Duration difference = Duration.between(Instant.parse(firstReadAt), Instant.parse(secondReadAt)).abs();
                    assertThat(difference.toMillis(), is(0L));
                });
    }

}
