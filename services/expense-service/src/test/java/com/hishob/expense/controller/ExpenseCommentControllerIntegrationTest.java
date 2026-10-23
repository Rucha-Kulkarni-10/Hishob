package com.hishob.expense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.service.GroupService;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.CreateExpenseCommentRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.UpdateExpenseCommentRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseComment;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseCommentRepository;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.repository.ExpenseSplitRepository;
import com.hishob.expense.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ExpenseCommentControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private ExpenseCommentRepository expenseCommentRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @MockitoBean
    private NotificationServiceClient notificationServiceClient;

    private MockMvc mockMvc;

    private static final String TEST_SECRET = "hishob-test-jwt-secret-for-automated-tests-only-0123456789abcdef";
    private static final long TEST_EXPIRATION = 3600000L;

    private UUID ownerId;
    private UUID memberId;
    private UUID nonMemberId;
    private UUID testGroupId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        expenseCommentRepository.deleteAll();
        expenseSplitRepository.deleteAll();
        expenseRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        nonMemberId = UUID.randomUUID();
        testGroupId = UUID.randomUUID();
    }

    private String jwt(UUID userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("name", "Test User")
                .issuedAt(new Date(now))
                .expiration(new Date(now + TEST_EXPIRATION))
                .signWith(key)
                .compact();
    }

    private void stubGroupMembers(UUID groupId) {
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(eq(groupId), eq(ownerId), anyString());
        doNothing().when(groupService).validateMember(eq(groupId), eq(memberId), anyString());
    }

    private Expense createGroupExpense() {
        Expense expense = new Expense(testGroupId, "Team Dinner", new BigDecimal("120.00"), ownerId, ownerId, SplitType.EQUAL, ExpenseCategory.FOOD);
        expense.addSplit(new ExpenseSplit(ownerId, new BigDecimal("60.00"), SplitType.EQUAL, null));
        expense.addSplit(new ExpenseSplit(memberId, new BigDecimal("60.00"), SplitType.EQUAL, null));
        return expenseRepository.save(expense);
    }

    private Expense createPersonalExpense() {
        Expense expense = new Expense(null, "Concert Tickets", new BigDecimal("80.00"), ownerId, ownerId, SplitType.EQUAL, ExpenseCategory.ENTERTAINMENT);
        expense.addSplit(new ExpenseSplit(ownerId, new BigDecimal("40.00"), SplitType.EQUAL, null));
        expense.addSplit(new ExpenseSplit(memberId, new BigDecimal("40.00"), SplitType.EQUAL, null));
        return expenseRepository.save(expense);
    }

    @Test
    @DisplayName("1. Authorized group member can list comments")
    void testListComments_GroupMember_Success() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment c1 = new ExpenseComment(expense, ownerId, "Welcome everyone");
        expenseCommentRepository.save(c1);

        String token = jwt(memberId, "member@test.com");

        mockMvc.perform(get("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content", is("Welcome everyone")))
                .andExpect(jsonPath("$[0].authorUserId", is(ownerId.toString())))
                .andExpect(jsonPath("$[0].expenseId", is(expense.getId().toString())))
                .andExpect(jsonPath("$[0].isEdited", is(false)));
    }

    @Test
    @DisplayName("2. Authorized group member can create a comment")
    void testCreateComment_GroupMember_Success() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        String token = jwt(memberId, "member@test.com");
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Receipt has been uploaded.");

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", is("Receipt has been uploaded.")))
                .andExpect(jsonPath("$.authorUserId", is(memberId.toString())))
                .andExpect(jsonPath("$.expenseId", is(expense.getId().toString())))
                .andExpect(jsonPath("$.isEdited", is(false)));

        assertEquals(1, expenseCommentRepository.count());
    }

    @Test
    @DisplayName("3. Authorized personal-expense participant can comment")
    void testCreateComment_PersonalExpenseParticipant_Success() throws Exception {
        Expense expense = createPersonalExpense();

        String token = jwt(memberId, "member@test.com");
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Thanks for booking!");

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", is("Thanks for booking!")))
                .andExpect(jsonPath("$.authorUserId", is(memberId.toString())))
                .andExpect(jsonPath("$.expenseId", is(expense.getId().toString())));
    }

    @Test
    @DisplayName("4. Stranger cannot access personal comments => 404")
    void testAccessPersonalComments_Stranger_NotFound() throws Exception {
        Expense expense = createPersonalExpense();
        ExpenseComment c1 = new ExpenseComment(expense, ownerId, "Secret note");
        expenseCommentRepository.save(c1);

        String token = jwt(nonMemberId, "stranger@test.com");

        mockMvc.perform(get("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Intruder");
        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. Non-member cannot access group comments => 403")
    void testAccessGroupComments_NonMember_Forbidden() throws Exception {
        Expense expense = createGroupExpense();

        doNothing().when(groupService).validateGroupExists(testGroupId);
        doThrow(new PermissionDeniedException("Caller is not a member of this group"))
                .when(groupService).validateMember(eq(testGroupId), eq(nonMemberId), anyString());

        String token = jwt(nonMemberId, "stranger@test.com");

        mockMvc.perform(get("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Author can edit their own comment")
    void testUpdateComment_Author_Success() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment comment = new ExpenseComment(expense, memberId, "Original text");
        comment.setCreatedAt(Instant.now().minusSeconds(10));
        comment.setUpdatedAt(comment.getCreatedAt());
        comment = expenseCommentRepository.save(comment);

        String token = jwt(memberId, "member@test.com");
        UpdateExpenseCommentRequest req = new UpdateExpenseCommentRequest("Edited text");

        mockMvc.perform(put("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Edited text")))
                .andExpect(jsonPath("$.isEdited", is(true)));
    }

    @Test
    @DisplayName("7. Another user cannot edit the comment => 403")
    void testUpdateComment_NonAuthor_Forbidden() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment comment = new ExpenseComment(expense, memberId, "Member's comment");
        comment = expenseCommentRepository.save(comment);

        String token = jwt(ownerId, "owner@test.com");
        UpdateExpenseCommentRequest req = new UpdateExpenseCommentRequest("Tampered text");

        mockMvc.perform(put("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Author can delete their comment => 204")
    void testDeleteComment_Author_Success() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment comment = new ExpenseComment(expense, memberId, "To delete");
        comment = expenseCommentRepository.save(comment);

        String token = jwt(memberId, "member@test.com");

        mockMvc.perform(delete("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, expenseCommentRepository.count());
    }

    @Test
    @DisplayName("9. Expense creator can delete another user's comment => 204")
    void testDeleteComment_ExpenseCreator_Success() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment comment = new ExpenseComment(expense, memberId, "Member comment");
        comment = expenseCommentRepository.save(comment);

        String token = jwt(ownerId, "owner@test.com");

        mockMvc.perform(delete("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, expenseCommentRepository.count());
    }

    @Test
    @DisplayName("10. Unrelated user cannot delete a comment => 403")
    void testDeleteComment_UnrelatedUser_Forbidden() throws Exception {
        stubGroupMembers(testGroupId);
        UUID otherMemberId = UUID.randomUUID();
        doNothing().when(groupService).validateMember(eq(testGroupId), eq(otherMemberId), anyString());

        Expense expense = createGroupExpense();

        ExpenseComment comment = new ExpenseComment(expense, memberId, "Member comment");
        comment = expenseCommentRepository.save(comment);

        String token = jwt(otherMemberId, "other@test.com");

        mockMvc.perform(delete("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertEquals(1, expenseCommentRepository.count());
    }

    @Test
    @DisplayName("11. Blank content rejected => 400")
    void testCreateComment_BlankContent_BadRequest() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        String token = jwt(memberId, "member@test.com");
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("    ");

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. Content over 1,000 characters rejected => 400")
    void testCreateComment_TooLongContent_BadRequest() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        String token = jwt(memberId, "member@test.com");
        String longContent = "A".repeat(1001);
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest(longContent);

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("13. Comments ordered oldest first")
    void testListComments_OrderedOldestFirst() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment c1 = new ExpenseComment(expense, ownerId, "First comment");
        c1.setCreatedAt(Instant.now().minusSeconds(100));
        c1.setUpdatedAt(c1.getCreatedAt());
        expenseCommentRepository.save(c1);

        ExpenseComment c2 = new ExpenseComment(expense, memberId, "Second comment");
        c2.setCreatedAt(Instant.now().minusSeconds(50));
        c2.setUpdatedAt(c2.getCreatedAt());
        expenseCommentRepository.save(c2);

        String token = jwt(ownerId, "owner@test.com");

        mockMvc.perform(get("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content", is("First comment")))
                .andExpect(jsonPath("$[1].content", is("Second comment")));
    }

    @Test
    @DisplayName("14. Missing expense returns 404")
    void testMissingExpense_ReturnsNotFound() throws Exception {
        UUID fakeExpenseId = UUID.randomUUID();
        String token = jwt(ownerId, "owner@test.com");

        mockMvc.perform(get("/api/expenses/" + fakeExpenseId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Hello");
        mockMvc.perform(post("/api/expenses/" + fakeExpenseId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("15. Comment ID from another expense cannot be modified => 404")
    void testUpdateComment_BelongsToDifferentExpense_NotFound() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense1 = createGroupExpense();
        Expense expense2 = createPersonalExpense();

        ExpenseComment commentInExpense2 = new ExpenseComment(expense2, ownerId, "Comment in exp 2");
        commentInExpense2 = expenseCommentRepository.save(commentInExpense2);

        String token = jwt(ownerId, "owner@test.com");
        UpdateExpenseCommentRequest req = new UpdateExpenseCommentRequest("Try updating via exp1");

        mockMvc.perform(put("/api/expenses/" + expense1.getId() + "/comments/" + commentInExpense2.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("16. Deleting an expense cascades to its comments")
    void testDeleteExpense_CascadesToComments() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment c1 = new ExpenseComment(expense, ownerId, "Comment 1");
        expense.addComment(c1);
        ExpenseComment c2 = new ExpenseComment(expense, memberId, "Comment 2");
        expense.addComment(c2);
        expenseRepository.save(expense);

        assertEquals(2, expenseCommentRepository.count());

        String token = jwt(ownerId, "owner@test.com");

        mockMvc.perform(delete("/api/expenses/" + expense.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, expenseRepository.count());
        assertEquals(0, expenseCommentRepository.count());
    }

    @Test
    @DisplayName("17. Creating group comment dispatches notifications excluding author")
    void testCreateComment_DispatchesNotificationToOtherMembers() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        String token = jwt(memberId, "member@test.com");
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Check the bill amount.");

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, times(1)).createNotification(captor.capture());

        CreateNotificationRequest notification = captor.getValue();
        assertEquals(ownerId, notification.getUserId());
        assertEquals(testGroupId, notification.getGroupId());
        assertEquals("EXPENSE_COMMENT_ADDED", notification.getType());
        assertEquals(expense.getId().toString(), notification.getMetadata().get("expenseId"));
        assertEquals("Check the bill amount.", notification.getMetadata().get("commentPreview"));
        assertEquals(false, notification.getMetadata().get("personal"));
        assertEquals(memberId.toString(), notification.getMetadata().get("actorUserId"));
    }

    @Test
    @DisplayName("18. Creating personal comment dispatches notification to the other friend")
    void testCreateComment_PersonalExpense_DispatchesNotification() throws Exception {
        Expense expense = createPersonalExpense();

        String token = jwt(memberId, "member@test.com");
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Paid my share via UPI");

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, times(1)).createNotification(captor.capture());

        CreateNotificationRequest notification = captor.getValue();
        assertEquals(ownerId, notification.getUserId());
        assertEquals(null, notification.getGroupId());
        assertEquals("EXPENSE_COMMENT_ADDED", notification.getType());
        assertEquals(true, notification.getMetadata().get("personal"));
        assertEquals(memberId.toString(), notification.getMetadata().get("actorUserId"));
    }

    @Test
    @DisplayName("19. Notification service failure does not fail comment creation")
    void testCreateComment_NotificationFailure_StillReturns201() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        doThrow(new RuntimeException("Notification service unreachable"))
                .when(notificationServiceClient).createNotification(any(CreateNotificationRequest.class));

        String token = jwt(memberId, "member@test.com");
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Comment with notification outage");

        mockMvc.perform(post("/api/expenses/" + expense.getId() + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", is("Comment with notification outage")));

        assertEquals(1, expenseCommentRepository.count());
    }

    @Test
    @DisplayName("20. Updating or deleting a comment does not dispatch notifications")
    void testUpdateOrDeleteComment_NoNotificationsDispatched() throws Exception {
        stubGroupMembers(testGroupId);
        Expense expense = createGroupExpense();

        ExpenseComment comment = new ExpenseComment(expense, memberId, "Original");
        comment = expenseCommentRepository.save(comment);

        String token = jwt(memberId, "member@test.com");

        // Update
        UpdateExpenseCommentRequest updateReq = new UpdateExpenseCommentRequest("Edited");
        mockMvc.perform(put("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Delete
        mockMvc.perform(delete("/api/expenses/" + expense.getId() + "/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verifyNoInteractions(notificationServiceClient);
    }
}
