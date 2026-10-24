package com.hishob.expense.service;

import com.hishob.expense.service.GroupService;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.CreateExpenseCommentRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.ExpenseCommentResponse;
import com.hishob.expense.dto.UpdateExpenseCommentRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseComment;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.ExpenseCommentNotFoundException;
import com.hishob.expense.exception.ExpenseNotFoundException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseCommentRepository;
import com.hishob.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCommentServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseCommentRepository expenseCommentRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    private ExpenseCommentService service;

    private UUID groupId;
    private UUID creatorId;
    private UUID payerId;
    private UUID memberId;
    private UUID strangerId;
    private UUID expenseId;
    private Expense groupExpense;
    private Expense personalExpense;

    @BeforeEach
    void setUp() {
        service = new ExpenseCommentService(
                expenseRepository,
                expenseCommentRepository,
                groupService,
                notificationServiceClient
        );

        groupId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        payerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        expenseId = UUID.randomUUID();

        groupExpense = new Expense(
                groupId,
                "Team Lunch",
                new BigDecimal("150.00"),
                payerId,
                creatorId,
                SplitType.EQUAL,
                ExpenseCategory.FOOD
        );
        groupExpense.setId(expenseId);
        groupExpense.addSplit(new ExpenseSplit(creatorId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        groupExpense.addSplit(new ExpenseSplit(payerId, new BigDecimal("50.00"), SplitType.EQUAL, null));
        groupExpense.addSplit(new ExpenseSplit(memberId, new BigDecimal("50.00"), SplitType.EQUAL, null));

        personalExpense = new Expense(
                null,
                "Movie tickets",
                new BigDecimal("40.00"),
                payerId,
                creatorId,
                SplitType.EQUAL,
                ExpenseCategory.ENTERTAINMENT
        );
        personalExpense.setId(expenseId);
        personalExpense.addSplit(new ExpenseSplit(creatorId, new BigDecimal("20.00"), SplitType.EQUAL, null));
        personalExpense.addSplit(new ExpenseSplit(payerId, new BigDecimal("20.00"), SplitType.EQUAL, null));
    }

    @Test
    @DisplayName("1. Authorized group member can list comments in chronological order")
    void testListComments_GroupMember_Success() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        ExpenseComment c1 = new ExpenseComment(groupExpense, creatorId, "First comment");
        c1.setId(UUID.randomUUID());
        c1.setCreatedAt(Instant.now().minusSeconds(100));
        c1.setUpdatedAt(c1.getCreatedAt());

        ExpenseComment c2 = new ExpenseComment(groupExpense, memberId, "Second comment");
        c2.setId(UUID.randomUUID());
        c2.setCreatedAt(Instant.now().minusSeconds(50));
        c2.setUpdatedAt(c2.getCreatedAt());

        when(expenseCommentRepository.findByExpenseIdOrderByCreatedAtAsc(expenseId)).thenReturn(List.of(c1, c2));

        List<ExpenseCommentResponse> responses = service.listComments(expenseId, memberId);

        assertEquals(2, responses.size());
        assertEquals("First comment", responses.get(0).getContent());
        assertEquals("Second comment", responses.get(1).getContent());
        assertFalse(responses.get(0).isEdited());
    }

    @Test
    @DisplayName("2. Non-group member cannot list comments (PermissionDeniedException)")
    void testListComments_NonGroupMember_Forbidden() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("Caller is not a member of this group"))
                .when(groupService).validateMember(groupId, strangerId, "Caller");

        assertThrows(PermissionDeniedException.class, () -> service.listComments(expenseId, strangerId));
    }

    @Test
    @DisplayName("3. Authorized personal expense participant can list comments")
    void testListComments_PersonalParticipant_Success() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(personalExpense));

        ExpenseComment c1 = new ExpenseComment(personalExpense, creatorId, "Personal note");
        c1.setId(UUID.randomUUID());
        c1.setCreatedAt(Instant.now());
        c1.setUpdatedAt(c1.getCreatedAt());

        when(expenseCommentRepository.findByExpenseIdOrderByCreatedAtAsc(expenseId)).thenReturn(List.of(c1));

        List<ExpenseCommentResponse> responses = service.listComments(expenseId, payerId);

        assertEquals(1, responses.size());
        assertEquals("Personal note", responses.get(0).getContent());
    }

    @Test
    @DisplayName("4. Stranger cannot list personal expense comments (ExpenseNotFoundException)")
    void testListComments_PersonalStranger_NotFound() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(personalExpense));

        assertThrows(ExpenseNotFoundException.class, () -> service.listComments(expenseId, strangerId));
    }

    @Test
    @DisplayName("5. Authorized group member creates comment and notifies other participants")
    void testCreateComment_GroupMember_NotifiesOthers() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        when(expenseCommentRepository.save(any(ExpenseComment.class))).thenAnswer(inv -> {
            ExpenseComment entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            return entity;
        });

        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Split the tip too!");
        ExpenseCommentResponse resp = service.createComment(expenseId, req, memberId);

        assertNotNull(resp);
        assertEquals("Split the tip too!", resp.getContent());
        assertEquals(memberId, resp.getAuthorUserId());

        // Participants in groupExpense are creatorId, payerId, memberId.
        // Author is memberId -> notifications sent to creatorId and payerId (2 recipients).
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, times(2)).createNotification(captor.capture());

        List<CreateNotificationRequest> requests = captor.getAllValues();
        List<UUID> recipientIds = requests.stream().map(CreateNotificationRequest::getUserId).toList();
        assertTrue(recipientIds.contains(creatorId));
        assertTrue(recipientIds.contains(payerId));
        assertFalse(recipientIds.contains(memberId)); // Author excluded

        CreateNotificationRequest sample = requests.get(0);
        assertEquals("EXPENSE_COMMENT_ADDED", sample.getType());
        assertEquals(groupId, sample.getGroupId());
        assertEquals(expenseId.toString(), sample.getMetadata().get("expenseId"));
        assertEquals("Split the tip too!", sample.getMetadata().get("commentPreview"));
        assertEquals(false, sample.getMetadata().get("personal"));
        assertEquals(memberId.toString(), sample.getMetadata().get("actorUserId"));
    }

    @Test
    @DisplayName("6. Authorized personal expense participant creates comment and notifies other participant")
    void testCreateComment_PersonalParticipant_NotifiesOther() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(personalExpense));

        when(expenseCommentRepository.save(any(ExpenseComment.class))).thenAnswer(inv -> {
            ExpenseComment entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            return entity;
        });

        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Thanks for booking");
        ExpenseCommentResponse resp = service.createComment(expenseId, req, payerId);

        assertNotNull(resp);
        assertEquals("Thanks for booking", resp.getContent());

        // Personal expense involves creatorId and payerId. Payer is author -> creator is notified.
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, times(1)).createNotification(captor.capture());

        CreateNotificationRequest notification = captor.getValue();
        assertEquals(creatorId, notification.getUserId());
        assertEquals("EXPENSE_COMMENT_ADDED", notification.getType());
        assertEquals(true, notification.getMetadata().get("personal"));
        assertEquals(payerId.toString(), notification.getMetadata().get("actorUserId"));
    }

    @Test
    @DisplayName("7. Safe comment preview truncates content longer than 80 characters")
    void testCreateComment_LongPreviewTruncated() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        when(expenseCommentRepository.save(any(ExpenseComment.class))).thenAnswer(inv -> {
            ExpenseComment entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            return entity;
        });

        String longContent = "A".repeat(120);
        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest(longContent);
        service.createComment(expenseId, req, memberId);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, times(2)).createNotification(captor.capture());

        String preview = (String) captor.getAllValues().get(0).getMetadata().get("commentPreview");
        assertEquals(83, preview.length()); // 80 chars + "..."
        assertTrue(preview.endsWith("..."));
    }

    @Test
    @DisplayName("8. Notification service failure does not fail comment creation")
    void testCreateComment_NotificationFailure_CommentStillCreated() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        when(expenseCommentRepository.save(any(ExpenseComment.class))).thenAnswer(inv -> {
            ExpenseComment entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            return entity;
        });

        doThrow(new RuntimeException("Notification service connection refused"))
                .when(notificationServiceClient).createNotification(any(CreateNotificationRequest.class));

        CreateExpenseCommentRequest req = new CreateExpenseCommentRequest("Hello team");
        ExpenseCommentResponse resp = service.createComment(expenseId, req, memberId);

        assertNotNull(resp);
        assertEquals("Hello team", resp.getContent());
    }

    @Test
    @DisplayName("9. Author can edit their own comment without triggering notifications")
    void testUpdateComment_Author_Success_NoNotification() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        UUID commentId = UUID.randomUUID();
        ExpenseComment existing = new ExpenseComment(groupExpense, memberId, "Original text");
        existing.setId(commentId);
        Instant originalCreated = Instant.now().minusSeconds(60);
        existing.setCreatedAt(originalCreated);
        existing.setUpdatedAt(originalCreated);

        when(expenseCommentRepository.findById(commentId)).thenReturn(Optional.of(existing));
        when(expenseCommentRepository.saveAndFlush(any(ExpenseComment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseCommentRequest req = new UpdateExpenseCommentRequest("Updated text");
        ExpenseCommentResponse resp = service.updateComment(expenseId, commentId, req, memberId);

        assertEquals("Updated text", resp.getContent());
        assertTrue(resp.isEdited());
        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    @DisplayName("10. Non-author cannot edit comment even if group member")
    void testUpdateComment_NonAuthor_Forbidden() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Caller");

        UUID commentId = UUID.randomUUID();
        ExpenseComment existing = new ExpenseComment(groupExpense, memberId, "Original text");
        existing.setId(commentId);

        when(expenseCommentRepository.findById(commentId)).thenReturn(Optional.of(existing));

        UpdateExpenseCommentRequest req = new UpdateExpenseCommentRequest("Hacked text");
        assertThrows(PermissionDeniedException.class, () -> service.updateComment(expenseId, commentId, req, creatorId));
        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    @DisplayName("11. Updating comment ID from another expense throws ExpenseCommentNotFoundException")
    void testUpdateComment_MismatchedExpense_NotFound() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        Expense otherExpense = new Expense(groupId, "Other", new BigDecimal("50"), payerId, creatorId, SplitType.EQUAL);
        otherExpense.setId(UUID.randomUUID());

        UUID commentId = UUID.randomUUID();
        ExpenseComment existing = new ExpenseComment(otherExpense, memberId, "Original text");
        existing.setId(commentId);

        when(expenseCommentRepository.findById(commentId)).thenReturn(Optional.of(existing));

        UpdateExpenseCommentRequest req = new UpdateExpenseCommentRequest("Updated");
        assertThrows(ExpenseCommentNotFoundException.class, () -> service.updateComment(expenseId, commentId, req, memberId));
    }

    @Test
    @DisplayName("12. Author can delete their own comment without triggering notifications")
    void testDeleteComment_Author_Success_NoNotification() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, memberId, "Caller");

        UUID commentId = UUID.randomUUID();
        ExpenseComment existing = new ExpenseComment(groupExpense, memberId, "To delete");
        existing.setId(commentId);

        when(expenseCommentRepository.findById(commentId)).thenReturn(Optional.of(existing));

        service.deleteComment(expenseId, commentId, memberId);

        verify(expenseCommentRepository).delete(existing);
        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    @DisplayName("13. Expense creator can delete another user's comment (moderation)")
    void testDeleteComment_ExpenseCreator_Success() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Caller");

        UUID commentId = UUID.randomUUID();
        ExpenseComment existing = new ExpenseComment(groupExpense, memberId, "Member comment");
        existing.setId(commentId);

        when(expenseCommentRepository.findById(commentId)).thenReturn(Optional.of(existing));

        service.deleteComment(expenseId, commentId, creatorId);

        verify(expenseCommentRepository).delete(existing);
    }

    @Test
    @DisplayName("14. Non-author non-creator cannot delete comment (PermissionDeniedException)")
    void testDeleteComment_UnrelatedUser_Forbidden() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(groupExpense));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, payerId, "Caller");

        UUID commentId = UUID.randomUUID();
        ExpenseComment existing = new ExpenseComment(groupExpense, memberId, "Member comment");
        existing.setId(commentId);

        when(expenseCommentRepository.findById(commentId)).thenReturn(Optional.of(existing));

        assertThrows(PermissionDeniedException.class, () -> service.deleteComment(expenseId, commentId, payerId));
    }

    @Test
    @DisplayName("15. Missing expense throws ExpenseNotFoundException")
    void testMissingExpense_ThrowsNotFound() {
        UUID fakeId = UUID.randomUUID();
        when(expenseRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThrows(ExpenseNotFoundException.class, () -> service.listComments(fakeId, memberId));
        assertThrows(ExpenseNotFoundException.class, () -> service.createComment(fakeId, new CreateExpenseCommentRequest("Hi"), memberId));
    }
}
