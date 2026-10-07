package com.hishob.expense.service;

import com.hishob.expense.client.GroupServiceClient;
import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.CreateExpenseRequest;
import com.hishob.expense.dto.CreatePersonalExpenseRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.ExpenseResponse;
import com.hishob.expense.dto.ExpenseSplitRequest;
import com.hishob.expense.dto.UpdatePersonalExpenseRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.InvalidGroupException;
import com.hishob.expense.exception.ExpenseNotFoundException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.repository.ExpenseSplitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private GroupServiceClient groupServiceClient;

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
        expenseService = new ExpenseService(expenseRepository, expenseSplitRepository, groupServiceClient, authServiceClient, splitCalculationService, notificationServiceClient);
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

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupServiceClient).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));

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
    @DisplayName("Create: non-member caller throws PermissionDenied via GroupServiceClient.validateMember")
    void testCreateExpense_NonMemberCaller_Forbidden() {
        UUID participantId = UUID.randomUUID();
        ExpenseSplitRequest s1 = new ExpenseSplitRequest(payerId, new BigDecimal("500"), null);
        ExpenseSplitRequest s2 = new ExpenseSplitRequest(participantId, new BigDecimal("500"), null);
        CreateExpenseRequest req = new CreateExpenseRequest(groupId, "Lunch", new BigDecimal("1000"), payerId, SplitType.EXACT, List.of(s1, s2));

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doThrow(new PermissionDeniedException("Creator is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, creatorId, "Creator");

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

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Creator");
        doThrow(new PermissionDeniedException("Payer is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, payerId, "Payer");

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

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Payer");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Participant");
        doThrow(new PermissionDeniedException("Participant is not a member of this group"))
                .when(groupServiceClient).validateMember(groupId, badParticipantId, "Participant");

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
        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Payer");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupServiceClient).validateMember(groupId, participantId, "Participant");
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

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Caller");
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(e1, e2));

        List<ExpenseResponse> list = expenseService.listGroupExpenses(groupId, creatorId);

        assertThat(list).hasSize(2);
        verify(groupServiceClient).validateMember(groupId, creatorId, "Caller");
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
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Caller");

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
                .when(groupServiceClient).validateMember(groupId, otherUserId, "Caller");

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
                .when(groupServiceClient).validateGroupExists(groupId);

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

        doNothing().when(groupServiceClient).validateGroupExists(groupId);
        doNothing().when(groupServiceClient).validateMember(groupId, creatorId, "Creator");
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Payer");
        doNothing().when(groupServiceClient).validateMember(groupId, payerId, "Participant");
        doNothing().when(groupServiceClient).validateMember(groupId, participantId, "Participant");
        doNothing().when(splitCalculationService).validateNoDuplicateUsers(any());
        when(splitCalculationService.validateAndBuildExactSplits(any(), any())).thenReturn(List.of(split1, split2));

        Expense saved = new Expense(groupId, "Dinner", new BigDecimal("1000"), payerId, creatorId, SplitType.EXACT);
        saved.setId(UUID.randomUUID());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        saved.addSplit(split1);
        saved.addSplit(split2);

        when(expenseRepository.save(any(Expense.class))).thenReturn(saved);

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
        verify(groupServiceClient, never()).validateGroupExists(any());
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
}
