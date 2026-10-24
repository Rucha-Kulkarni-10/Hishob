package com.hishob.expense.service;

import com.hishob.expense.service.GroupService;
import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.CreateExpenseRequest;
import com.hishob.expense.dto.CreatePersonalExpenseRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.ExpenseResponse;
import com.hishob.expense.dto.ExpenseSplitRequest;
import com.hishob.expense.dto.UpdatePersonalExpenseRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.InvalidGroupException;
import com.hishob.expense.exception.ExpenseNotFoundException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.repository.ExpenseSplitRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private SplitCalculationService splitCalculationService;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    private ExpenseService expenseService;

    private UUID creatorId;
    private UUID otherUserId;
    private UUID groupId;
    private UUID payerId;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, expenseSplitRepository, groupService, authServiceClient, splitCalculationService, notificationServiceClient);
        creatorId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        payerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Create happy path: validates all, saves expense + splits, returns response")
    void testCreateExpense_HappyPath() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("600"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("400"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Dinner", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        ExpenseSplit split1 = new ExpenseSplit(payerId, new BigDecimal("600.00"), SplitType.EXACT, null);
        ExpenseSplit split2 = new ExpenseSplit(participantId, new BigDecimal("400.00"), SplitType.EXACT, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupService).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, payerId, participantId));

        Expense saved = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);

        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getCreatedBy()).isEqualTo(creatorId);
        assertThat(resp.getPaidBy()).isEqualTo(payerId);
        assertThat(resp.getGroupId()).isEqualTo(groupId);
        verify(expenseRepository).save(any(Expense.class));
        
        // Verify notifications are sent to participants
        verify(notificationServiceClient).createNotification(argThat(notification ->
                notification.getUserId().equals(payerId)
                        && notification.getMessage().equals("A new expense was added to the group.")
                        && notification.getMetadata().containsKey("expenseId")));
        verify(notificationServiceClient).createNotification(argThat(notification ->
                notification.getUserId().equals(participantId)
                        && notification.getMessage().equals("A new expense was added to the group.")
                        && notification.getMetadata().containsKey("expenseId")));
    }

    @Test
    @DisplayName("Create: non-member caller throws PermissionDenied via GroupService.validateMember")
    void testCreateExpense_NonMemberCaller_Forbidden() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Lunch", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        doNothing().when(groupService).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("Creator is not a member of this group"))
                .when(groupService).validateMember(groupId, creatorId, "Creator");

        assertThatThrownBy(() -> expenseService.createExpense(req, creatorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Creator");
    }

    @Test
    @DisplayName("Create: payer not in group throws PermissionDenied")
    void testCreateExpense_PayerNotInGroup_Forbidden() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Lunch", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doThrow(new PermissionDeniedException("Payer is not a member of this group"))
                .when(groupService).validateMember(groupId, payerId, "Payer");

        assertThatThrownBy(() -> expenseService.createExpense(req, creatorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Payer");
    }

    @Test
    @DisplayName("Create: participant not in group throws PermissionDenied")
    void testCreateExpense_ParticipantNotInGroup_Forbidden() {
        UUID badParticipantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(badParticipantId, new BigDecimal("500"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Lunch", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doThrow(new PermissionDeniedException("Participant is not a member of this group"))
                .when(groupService).validateMember(groupId, badParticipantId, "Participant");

        assertThatThrownBy(() -> expenseService.createExpense(req, creatorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Participant");
    }

    @Test
    @DisplayName("Update: creator can update — splits replaced, all revalidated")
    void testUpdateExpense_Creator_Success() {
        UUID expenseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Old desc", new BigDecimal("500"), payerId, creatorId, SplitType.EQUAL);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        existing.addSplit(new ExpenseSplit(payerId, new BigDecimal("250.00"), SplitType.EQUAL, null));
        existing.addSplit(new ExpenseSplit(participantId, new BigDecimal("250.00"), SplitType.EQUAL, null));

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("600"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("400"), null);
        CreateExpenseRequest updateReq = new CreateExpenseRequest(groupId, "New desc", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        ExpenseSplit newSplit1 = new ExpenseSplit(payerId, new BigDecimal("600.00"), SplitType.EXACT, null);
        ExpenseSplit newSplit2 = new ExpenseSplit(participantId, new BigDecimal("400.00"), SplitType.EXACT, null);

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupService).validateMember(groupId, participantId, "Participant");
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(newSplit1, newSplit2));

        Expense savedCopy = new Expense(groupId, "New desc", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        savedCopy.setId(expenseId);
        savedCopy.setCreatedAt(existing.getCreatedAt());
        savedCopy.setUpdatedAt(Instant.now());
        savedCopy.addSplit(newSplit1);
        savedCopy.addSplit(newSplit2);
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedCopy);

        ExpenseResponse resp = expenseService.updateExpense(expenseId, updateReq, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getDescription()).isEqualTo("New desc");
        assertThat(resp.getTotalAmount()).isEqualByComparingTo("1000");
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    @DisplayName("Update: non-creator throws PermissionDenied")
    void testUpdateExpense_NonCreator_Forbidden() {
        UUID expenseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreateExpenseRequest updateReq = new CreateExpenseRequest(groupId, "Updated", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> expenseService.updateExpense(expenseId, updateReq, otherUserId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Only the expense creator can update");
    }

    @Test
    @DisplayName("Delete: creator succeeds, repository.delete called")
    void testDeleteExpense_Creator_Success() {
        UUID expenseId = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));

        expenseService.deleteExpense(expenseId, creatorId);

        verify(expenseRepository).delete(existing);
    }

    @Test
    @DisplayName("Delete: non-creator throws PermissionDenied")
    void testDeleteExpense_NonCreator_Forbidden() {
        UUID expenseId = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> expenseService.deleteExpense(expenseId, otherUserId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Only the expense creator can delete");
    }

    @Test
    @DisplayName("listGroupExpenses: member sees ordered list from repository")
    void testListGroupExpenses_Member_Success() {
        Expense e1 = new Expense(groupId, "Dinner", new BigDecimal("100"), payerId, creatorId, SplitType.EQUAL);
        e1.setId(UUID.randomUUID());
        e1.setCreatedAt(Instant.now());
        e1.setUpdatedAt(Instant.now());
        Expense e2 = new Expense(groupId, "Lunch", new BigDecimal("50"), payerId, creatorId, SplitType.EQUAL);
        e2.setId(UUID.randomUUID());
        e2.setCreatedAt(Instant.now());
        e2.setUpdatedAt(Instant.now());

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Caller");
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(e1, e2));

        List<ExpenseResponse> list = expenseService.listGroupExpenses(groupId, creatorId);

        assertThat(list).hasSize(2);
        verify(groupService).validateMember(groupId, creatorId, "Caller");
    }

    @Test
    @DisplayName("getExpenseDetail: member can view returns ExpenseResponse")
    void testGetExpenseDetail_Member_Success() {
        UUID expenseId = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        doNothing().when(groupService).validateMember(groupId, creatorId, "Caller");

        ExpenseResponse resp = expenseService.getExpenseDetail(expenseId, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(expenseId);
        assertThat(resp.getDescription()).isEqualTo("Dinner");
    }

    @Test
    @DisplayName("getExpenseDetail: non-member throws PermissionDenied")
    void testGetExpenseDetail_NonMember_Forbidden() {
        UUID expenseId = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        doThrow(new PermissionDeniedException("Caller is not a member of this group"))
                .when(groupService).validateMember(groupId, otherUserId, "Caller");

        assertThatThrownBy(() -> expenseService.getExpenseDetail(expenseId, otherUserId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Caller");
    }

    @Test
    @DisplayName("Create: invalid group throws InvalidGroupException")
    void testCreateExpense_InvalidGroup_Throws() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Lunch", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        doThrow(new InvalidGroupException("Group not found: " + groupId))
                .when(groupService).validateGroupExists(groupId);

        assertThatThrownBy(() -> expenseService.createExpense(req, creatorId))
                .isInstanceOf(InvalidGroupException.class)
                .hasMessageContaining("Group not found");
    }

    @Test
    @DisplayName("Create expense: notification attempted successfully")
    void testCreateExpense_NotificationAttempted() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("600"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("400"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Dinner", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        ExpenseSplit split1 = new ExpenseSplit(payerId, new BigDecimal("600.00"), SplitType.EXACT, null);
        ExpenseSplit split2 = new ExpenseSplit(participantId, new BigDecimal("400.00"), SplitType.EXACT, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupService).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));

        Expense saved = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);

        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, payerId, participantId));

        // Expense creation should succeed and notification should be attempted
        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getCreatedBy()).isEqualTo(creatorId);
        assertThat(resp.getPaidBy()).isEqualTo(payerId);
        assertThat(resp.getGroupId()).isEqualTo(groupId);
        verify(expenseRepository).save(any(Expense.class));
        // Verify notifications are sent to both participants (2 calls expected)
        verify(notificationServiceClient, org.mockito.Mockito.times(2)).createNotification(any(CreateNotificationRequest.class));
    }

    @Test
    @DisplayName("Create personal: 3-person equal validates friendships, stores null group, sends personal notifications")
    void testCreatePersonalExpense_ThreePersonEqual_Success() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, null, null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(otherUserId, null, null);
        ExpenseSplitRequest s3 = new ExpenseSplitRequest(participantId, null, null);
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest("Trip snacks", new BigDecimal("1200"), creatorId, SplitType.EQUAL, List.of(s1, s2, s3));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("400.00"), SplitType.EQUAL, null);
        ExpenseSplit split2 = new ExpenseSplit(otherUserId, new BigDecimal("400.00"), SplitType.EQUAL, null);
        ExpenseSplit split3 = new ExpenseSplit(participantId, new BigDecimal("400.00"), SplitType.EQUAL, null);

        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1, split2, split3));

        Expense saved = new Expense(null, "Trip snacks", new BigDecimal("1200"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);
        saved.addSplit(split3);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        ExpenseResponse resp = expenseService.createPersonalExpense(req, creatorId);

        assertThat(resp.getGroupId()).isNull();
        assertThat(resp.getSplits()).hasSize(3);
        verify(authServiceClient).validateAcceptedFriendships(eq(creatorId), argThat(users ->
                users.containsAll(List.of(creatorId, otherUserId, participantId))));
        verify(groupService, never()).validateGroupExists(any());
        verify(notificationServiceClient).createNotification(argThat(notification ->
                notification.getUserId().equals(otherUserId)
                        && notification.getGroupId() == null
                        && notification.getType().equals("PERSONAL_EXPENSE_ADDED")
                        && Boolean.TRUE.equals(notification.getMetadata().get("personal"))));
    }

    @Test
    @DisplayName("Create personal: payer gets notification even when not in splits")
    void testCreatePersonalExpense_PayerExcluded_StillNotified() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest("Gift", new BigDecimal("1000"), otherUserId, SplitType.EXACT, List.of(s1, s2));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("500.00"), SplitType.EXACT, null);
        ExpenseSplit split2 = new ExpenseSplit(participantId, new BigDecimal("500.00"), SplitType.EXACT, null);

        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));

        Expense saved = new Expense(null, "Gift", new BigDecimal("1000"), otherUserId, creatorId, SplitType.EXACT);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        expenseService.createPersonalExpense(req, creatorId);

        verify(notificationServiceClient).createNotification(argThat(notification ->
                notification.getUserId().equals(otherUserId)
                        && notification.getType().equals("PERSONAL_EXPENSE_ADDED")
                        && notification.getGroupId() == null));
    }

    @Test
    @DisplayName("Create personal: current user must be payer or participant")
    void testCreatePersonalExpense_CurrentUserNotInvolved_Forbidden() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(otherUserId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest("Lunch", new BigDecimal("1000"), otherUserId, SplitType.EXACT, List.of(s1, s2));

        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());

        assertThatThrownBy(() -> expenseService.createPersonalExpense(req, creatorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("must be the payer or a participant");
        verify(authServiceClient, never()).validateAcceptedFriendships(any(), any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create personal: rejected friendship validation blocks arbitrary users")
    void testCreatePersonalExpense_UnacceptedFriendship_Forbidden() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest("Lunch", new BigDecimal("1000"), creatorId, SplitType.EXACT, List.of(s1, s2));

        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doThrow(new PermissionDeniedException("All personal expense users must have accepted friendships"))
                .when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());

        assertThatThrownBy(() -> expenseService.createPersonalExpense(req, creatorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("accepted friendships");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Group update: changing group context is rejected")
    void testUpdateExpense_GroupChangeRejected() {
        UUID expenseId = UUID.randomUUID();
        UUID newGroupId = UUID.randomUUID();
        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        CreateExpenseRequest updateReq = new CreateExpenseRequest(newGroupId, "Updated", new BigDecimal("1000"), payerId, SplitType.EXACT,
                List.of(new ExpenseSplitRequest(payerId, new BigDecimal("1000"), null)));

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> expenseService.updateExpense(expenseId, updateReq, creatorId))
                .isInstanceOf(InvalidGroupException.class)
                .hasMessageContaining("cannot be changed");
    }

    @Test
    @DisplayName("Personal detail: group expense is hidden from personal endpoint")
    void testGetPersonalExpenseDetail_GroupExpense_NotFound() {
        UUID expenseId = UUID.randomUUID();
        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(expenseRepository.findPersonalExpenseByIdForUser(expenseId, creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.getPersonalExpenseDetail(expenseId, creatorId))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    @DisplayName("Friend personal list validates friendship before querying shared expenses")
    void testListFriendPersonalExpenses_ValidatesFriendship() {
        Expense e1 = new Expense(null, "Dinner", new BigDecimal("100"), otherUserId, creatorId, SplitType.EQUAL);
        e1.setId(UUID.randomUUID());
        e1.setCreatedAt(Instant.now());
        e1.setUpdatedAt(Instant.now());

        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());
        when(expenseRepository.findPersonalExpensesBetweenUsers(creatorId, otherUserId)).thenReturn(List.of(e1));

        List<ExpenseResponse> list = expenseService.listFriendPersonalExpenses(otherUserId, creatorId);

        assertThat(list).hasSize(1);
        verify(authServiceClient).validateAcceptedFriendships(eq(creatorId), argThat(users -> users.contains(otherUserId)));
        verify(expenseRepository).findPersonalExpensesBetweenUsers(creatorId, otherUserId);
    }

    @Test
    @DisplayName("Personal update: only creator can update")
    void testUpdatePersonalExpense_NonCreator_Forbidden() {
        UUID expenseId = UUID.randomUUID();
        Expense existing = new Expense(null, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        UpdatePersonalExpenseRequest updateReq = new UpdatePersonalExpenseRequest("Updated", new BigDecimal("1000"), payerId, SplitType.EXACT,
                List.of(new ExpenseSplitRequest(payerId, new BigDecimal("1000"), null)));

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> expenseService.updatePersonalExpense(expenseId, updateReq, otherUserId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Only the expense creator can update");
    }

    @Test
    @DisplayName("Create group expense saves specified category")
    void testCreateGroupExpense_WithCategory() {
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Team Lunch", new BigDecimal("100.00"), payerId, SplitType.EQUAL,
                List.of(new ExpenseSplitRequest(payerId, null, null), new ExpenseSplitRequest(otherUserId, null, null)),
                ExpenseCategory.FOOD
        );

        ExpenseSplit split1 = new ExpenseSplit(payerId, new BigDecimal("50.00"), SplitType.EQUAL, null);
        ExpenseSplit split2 = new ExpenseSplit(otherUserId, new BigDecimal("50.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupService).validateMember(groupId, otherUserId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1, split2));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp.getCategory()).isEqualTo(ExpenseCategory.FOOD);
        verify(expenseRepository).save(argThat(e -> e.getCategory() == ExpenseCategory.FOOD));
    }

    @Test
    @DisplayName("Create group expense with missing category defaults to OTHER")
    void testCreateGroupExpense_DefaultCategory() {
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Misc item", new BigDecimal("100.00"), payerId, SplitType.EQUAL,
                List.of(new ExpenseSplitRequest(payerId, null, null)),
                null
        );

        ExpenseSplit split1 = new ExpenseSplit(payerId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp.getCategory()).isEqualTo(ExpenseCategory.OTHER);
    }

    @Test
    @DisplayName("Create personal expense saves specified category")
    void testCreatePersonalExpense_WithCategory() {
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest(
                "Flight tickets", new BigDecimal("5000.00"), creatorId, SplitType.EQUAL,
                List.of(new ExpenseSplitRequest(creatorId, null, null), new ExpenseSplitRequest(otherUserId, null, null)),
                ExpenseCategory.TRAVEL
        );

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("2500.00"), SplitType.EQUAL, null);
        ExpenseSplit split2 = new ExpenseSplit(otherUserId, new BigDecimal("2500.00"), SplitType.EQUAL, null);

        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1, split2));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        ExpenseResponse resp = expenseService.createPersonalExpense(req, creatorId);

        assertThat(resp.getCategory()).isEqualTo(ExpenseCategory.TRAVEL);
        verify(expenseRepository).save(argThat(e -> e.getCategory() == ExpenseCategory.TRAVEL));
    }

    @Test
    @DisplayName("Update expense category is saved correctly")
    void testUpdateExpense_Category() {
        UUID expenseId = UUID.randomUUID();
        Expense existing = new Expense(groupId, "Original", new BigDecimal("100.00"), payerId, creatorId, SplitType.EQUAL);
        existing.setId(expenseId);
        existing.setCategory(ExpenseCategory.OTHER);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        CreateExpenseRequest updateReq = new CreateExpenseRequest(
                groupId, "Updated groceries", new BigDecimal("100.00"), payerId, SplitType.EQUAL,
                List.of(new ExpenseSplitRequest(payerId, null, null)),
                ExpenseCategory.GROCERIES
        );

        ExpenseSplit split1 = new ExpenseSplit(payerId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse resp = expenseService.updateExpense(expenseId, updateReq, creatorId);

        assertThat(resp.getCategory()).isEqualTo(ExpenseCategory.GROCERIES);
    }

    @Test
    @DisplayName("Create group expense: acting user is excluded from notification recipients")
    void testCreateExpense_ActorExcludedFromRecipients() {
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("500.00"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(otherUserId, new BigDecimal("500.00"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Groceries", new BigDecimal("1000.00"), creatorId, SplitType.EQUAL,
                List.of(s1, s2)
        );

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("500.00"), SplitType.EQUAL, null);
        ExpenseSplit split2 = new ExpenseSplit(otherUserId, new BigDecimal("500.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(groupService).validateMember(groupId, otherUserId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1, split2));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, otherUserId));

        Expense saved = new Expense(groupId, "Groceries", new BigDecimal("1000.00"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);

        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp).isNotNull();
        // Creator was payer and participant, but should be excluded from receiving notifications
        verify(notificationServiceClient, org.mockito.Mockito.times(1)).createNotification(any(CreateNotificationRequest.class));
        verify(notificationServiceClient).createNotification(argThat(n -> n.getUserId().equals(otherUserId)));
        verify(notificationServiceClient, never()).createNotification(argThat(n -> n.getUserId().equals(creatorId)));
    }

    @Test
    @DisplayName("Create group expense: creator is the only active member -> zero notifications, no exception")
    void testCreateGroupExpense_SoleMemberIsCreator_NoNotificationsSent() {
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("100.00"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Solo item", new BigDecimal("100.00"), creatorId, SplitType.EQUAL,
                List.of(s1)
        );

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId));

        Expense saved = new Expense(groupId, "Solo item", new BigDecimal("100.00"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);

        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp).isNotNull();
        // Only member is the acting creator, so the recipient set is empty after actor exclusion
        verify(groupService).listMemberIds(groupId);
        verify(notificationServiceClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("Create group expense: notification failure does not break expense creation")
    void testCreateExpense_NotificationFailure_ExpenseCreatedSuccessfully() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("600"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("400"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Dinner", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        ExpenseSplit split1 = new ExpenseSplit(payerId, new BigDecimal("600.00"), SplitType.EXACT, null);
        ExpenseSplit split2 = new ExpenseSplit(participantId, new BigDecimal("400.00"), SplitType.EXACT, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupService).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupService).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));

        Expense saved = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);

        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, payerId, participantId));
        doThrow(new RuntimeException("Notification service is down"))
                .when(notificationServiceClient).createNotification(any());

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(saved.getId());
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    @DisplayName("Create personal expense: notification failure does not break personal expense creation")
    void testCreatePersonalExpense_NotificationFailure_ExpenseCreatedSuccessfully() {
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, null, null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(otherUserId, null, null);
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest(
                "Coffee", new BigDecimal("200"), creatorId, SplitType.EQUAL, List.of(s1, s2)
        );

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("100.00"), SplitType.EQUAL, null);
        ExpenseSplit split2 = new ExpenseSplit(otherUserId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1, split2));

        Expense saved = new Expense(null, "Coffee", new BigDecimal("200"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        doThrow(new RuntimeException("Notification service down"))
                .when(notificationServiceClient).createNotification(any());

        ExpenseResponse resp = expenseService.createPersonalExpense(req, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(saved.getId());
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    @DisplayName("Create group expense: all active members except the actor are notified, including non-participants")
    void testCreateGroupExpense_NotifiesAllActiveMembersIncludingNonParticipants() {
        UUID participantId = UUID.randomUUID();
        UUID memberWithoutSplit = UUID.randomUUID();
        UUID memberWithoutSplit2 = UUID.randomUUID();

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("700"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("300"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Team dinner", new BigDecimal("1000"), creatorId, SplitType.EXACT, List.of(s1, s2));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("700.00"), SplitType.EXACT, null);
        ExpenseSplit split2 = new ExpenseSplit(participantId, new BigDecimal("300.00"), SplitType.EXACT, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(groupService).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, participantId, memberWithoutSplit, memberWithoutSplit2));

        Expense saved = new Expense(groupId, "Team dinner", new BigDecimal("1000"), creatorId, creatorId, SplitType.EXACT);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        ExpenseResponse resp = expenseService.createExpense(req, creatorId);

        assertThat(resp).isNotNull();
        verify(groupService).listMemberIds(groupId);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, org.mockito.Mockito.times(3)).createNotification(captor.capture());
        List<UUID> notifiedUserIds = captor.getAllValues().stream()
                .map(CreateNotificationRequest::getUserId)
                .toList();
        assertThat(notifiedUserIds).containsExactlyInAnyOrder(participantId, memberWithoutSplit, memberWithoutSplit2);

        for (CreateNotificationRequest notification : captor.getAllValues()) {
            assertThat(notification.getType()).isEqualTo("EXPENSE_ADDED");
            assertThat(notification.getGroupId()).isEqualTo(groupId);
            assertThat(notification.getMetadata()).containsEntry("personal", false);
            assertThat(notification.getMetadata()).containsEntry("expenseId", saved.getId().toString());
        }

        // One notification per eligible recipient, and never to the actor
        verify(notificationServiceClient).createNotification(argThat(n -> n.getUserId().equals(memberWithoutSplit)));
        verify(notificationServiceClient, never()).createNotification(argThat(n -> n.getUserId().equals(creatorId)));

        // Split participation is untouched by the notification change: non-participants owe nothing
        assertThat(resp.getSplits()).hasSize(2);
        assertThat(resp.getSplits().stream().map(split -> split.getUserId()).toList())
                .containsExactlyInAnyOrder(creatorId, participantId);
    }

    @Test
    @DisplayName("Create group expense: duplicate membership rows do not produce duplicate notifications")
    void testCreateGroupExpense_DuplicateMembershipRow_NotifiesOnce() {
        UUID member = UUID.randomUUID();

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("100"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Parking", new BigDecimal("100"), creatorId, SplitType.EQUAL, List.of(s1));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(member, member));

        Expense saved = new Expense(groupId, "Parking", new BigDecimal("100"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        expenseService.createExpense(req, creatorId);

        verify(notificationServiceClient, org.mockito.Mockito.times(1)).createNotification(any(CreateNotificationRequest.class));
    }

    @Test
    @DisplayName("Update group expense: all active members except the actor are notified")
    void testUpdateGroupExpense_NotifiesAllActiveMembersExceptActor() {
        UUID expenseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID memberWithoutSplit = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Old desc", new BigDecimal("500"), creatorId, creatorId, SplitType.EQUAL);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreateExpenseRequest updateReq = new CreateExpenseRequest(
                groupId, "New desc", new BigDecimal("1000"), creatorId, SplitType.EQUAL, List.of(s1, s2));

        ExpenseSplit newSplit1 = new ExpenseSplit(creatorId, new BigDecimal("500.00"), SplitType.EQUAL, null);
        ExpenseSplit newSplit2 = new ExpenseSplit(participantId, new BigDecimal("500.00"), SplitType.EQUAL, null);

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(groupService).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(newSplit1, newSplit2));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, participantId, memberWithoutSplit));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        expenseService.updateExpense(expenseId, updateReq, creatorId);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient, org.mockito.Mockito.times(2)).createNotification(captor.capture());
        assertThat(captor.getAllValues().stream().map(CreateNotificationRequest::getUserId).toList())
                .containsExactlyInAnyOrder(participantId, memberWithoutSplit);
        assertThat(captor.getAllValues()).allMatch(n -> "EXPENSE_UPDATED".equals(n.getType()));
        assertThat(captor.getAllValues()).allMatch(n -> groupId.equals(n.getGroupId()));
    }

    @Test
    @DisplayName("Delete group expense: members are notified before deletion and group context is retained")
    void testDeleteGroupExpense_NotifiesAllActiveMembersExceptActor() {
        UUID expenseId = UUID.randomUUID();
        UUID memberWithoutSplit = UUID.randomUUID();

        Expense existing = new Expense(groupId, "Dinner", new BigDecimal("1000"), creatorId, creatorId, SplitType.EXACT);
        existing.setId(expenseId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        existing.addSplit(new ExpenseSplit(creatorId, new BigDecimal("1000.00"), SplitType.EXACT, null));

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, memberWithoutSplit));

        expenseService.deleteExpense(expenseId, creatorId);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationServiceClient).createNotification(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(memberWithoutSplit);
        assertThat(captor.getValue().getType()).isEqualTo("EXPENSE_DELETED");
        assertThat(captor.getValue().getGroupId()).isEqualTo(groupId);

        // Recipients come from membership, so deleting the expense cannot strand the notification
        verify(expenseRepository).delete(existing);
    }

    @Test
    @DisplayName("Create personal expense: never consults group membership and stays group-less")
    void testCreatePersonalExpense_DoesNotUseGroupMembershipRecipients() {
        UUID participantId = UUID.randomUUID();

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, null, null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, null, null);
        CreatePersonalExpenseRequest req = new CreatePersonalExpenseRequest(
                "Coffee", new BigDecimal("200"), creatorId, SplitType.EQUAL, List.of(s1, s2));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("100.00"), SplitType.EQUAL, null);
        ExpenseSplit split2 = new ExpenseSplit(participantId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(authServiceClient).validateAcceptedFriendships(eq(creatorId), any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1, split2));

        Expense saved = new Expense(null, "Coffee", new BigDecimal("200"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        ExpenseResponse resp = expenseService.createPersonalExpense(req, creatorId);

        assertThat(resp.getGroupId()).isNull();
        verify(groupService, never()).listMemberIds(any());
        verify(notificationServiceClient, org.mockito.Mockito.times(1)).createNotification(any(CreateNotificationRequest.class));
        verify(notificationServiceClient).createNotification(argThat(n ->
                n.getUserId().equals(participantId)
                        && "PERSONAL_EXPENSE_ADDED".equals(n.getType())
                        && n.getGroupId() == null
                        && Boolean.TRUE.equals(n.getMetadata().get("personal"))));
    }

    @Test
    @DisplayName("Notification dispatch failure: expense is kept and a sanitized warning is logged")
    void testCreateGroupExpense_NotificationFailure_LogsSanitizedWarning() {
        UUID memberWithoutSplit = UUID.randomUUID();

        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("100"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Secret dinner description", new BigDecimal("100"), creatorId, SplitType.EQUAL, List.of(s1));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId, memberWithoutSplit));

        Expense saved = new Expense(groupId, "Secret dinner description", new BigDecimal("100"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.addSplit(split1);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);
        doThrow(new RuntimeException("HTTP 500 Internal Server Error"))
                .when(notificationServiceClient).createNotification(any());

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(ExpenseService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        ExpenseResponse resp;
        try {
            resp = expenseService.createExpense(req, creatorId);
        } finally {
            serviceLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(saved.getId());
        verify(expenseRepository).save(any(Expense.class));

        List<ILoggingEvent> warnings = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        assertThat(warnings).anyMatch(e -> {
            String message = e.getFormattedMessage();
            return message.contains("EXPENSE_ADDED")
                    && message.contains(groupId.toString())
                    && message.contains("1 recipient(s)");
        });
        assertThat(warnings).allMatch(e -> !e.getFormattedMessage().contains("Secret dinner description"));
    }

    @Test
    @DisplayName("No eligible recipients: dispatch skipped with a debug log and no exception")
    void testCreateGroupExpense_NoEligibleRecipients_LogsDebugAndSkipsDispatch() {
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(creatorId, new BigDecimal("100"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(
                groupId, "Solo item", new BigDecimal("100"), creatorId, SplitType.EQUAL, List.of(s1));

        ExpenseSplit split1 = new ExpenseSplit(creatorId, new BigDecimal("100.00"), SplitType.EQUAL, null);

        doNothing().when(groupService).validateGroupExists(groupId);
        doNothing().when(groupService).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Payer");
        doNothing().when(groupService).validateMember(groupId, creatorId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.calculateEqualSplits(any(), any())).thenReturn(List.of(split1));
        when(groupService.listMemberIds(groupId)).thenReturn(List.of(creatorId));

        Expense saved = new Expense(groupId, "Solo item", new BigDecimal("100"), creatorId, creatorId, SplitType.EQUAL);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.addSplit(split1);
        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(ExpenseService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        serviceLogger.setLevel(Level.DEBUG);
        appender.start();
        serviceLogger.addAppender(appender);
        ExpenseResponse resp;
        try {
            resp = expenseService.createExpense(req, creatorId);
        } finally {
            serviceLogger.detachAppender(appender);
            appender.stop();
            serviceLogger.setLevel(null);
        }

        assertThat(resp).isNotNull();
        verify(notificationServiceClient, never()).createNotification(any());
        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.DEBUG
                && e.getFormattedMessage().contains("No eligible recipient"));
    }

    @Test
    @DisplayName("listGroupExpenses: non-member is rejected before any expense data is read")
    void testListGroupExpenses_NonMember_Forbidden() {
        doNothing().when(groupService).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("Caller is not a member of this group"))
                .when(groupService).validateMember(groupId, otherUserId, "Caller");

        assertThatThrownBy(() -> expenseService.listGroupExpenses(groupId, otherUserId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("not a member");

        verify(expenseRepository, never()).findByGroupIdOrderByCreatedAtDesc(groupId);
    }
}
