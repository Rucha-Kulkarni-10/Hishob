package com.hishob.expense.service;

import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.CreateExpenseRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.CreatePersonalExpenseRequest;
import com.hishob.expense.dto.ExpenseResponse;
import com.hishob.expense.dto.ExpenseSplitRequest;
import com.hishob.expense.dto.UpdatePersonalExpenseRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.exception.ExpenseNotFoundException;
import com.hishob.expense.exception.InvalidGroupException;
import com.hishob.expense.exception.InvalidSplitException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.repository.ExpenseSplitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private static final String TYPE_GROUP_ADDED = "EXPENSE_ADDED";
    private static final String TYPE_GROUP_UPDATED = "EXPENSE_UPDATED";
    private static final String TYPE_GROUP_DELETED = "EXPENSE_DELETED";
    private static final String TYPE_PERSONAL_ADDED = "PERSONAL_EXPENSE_ADDED";
    private static final String TYPE_PERSONAL_UPDATED = "PERSONAL_EXPENSE_UPDATED";
    private static final String TYPE_PERSONAL_DELETED = "PERSONAL_EXPENSE_DELETED";

    private final ExpenseRepository expenseRepository;
    @SuppressWarnings("unused")
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupService groupService;
    private final AuthServiceClient authServiceClient;
    private final SplitCalculationService splitCalculationService;
    private final NotificationServiceClient notificationServiceClient;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            GroupService groupService,
            AuthServiceClient authServiceClient,
            SplitCalculationService splitCalculationService,
            NotificationServiceClient notificationServiceClient) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupService = groupService;
        this.authServiceClient = authServiceClient;
        this.splitCalculationService = splitCalculationService;
        this.notificationServiceClient = notificationServiceClient;
    }

    public ExpenseResponse createExpense(CreateExpenseRequest req, UUID currentUserId) {
        return createGroupExpense(req, currentUserId);
    }

    public ExpenseResponse createGroupExpense(CreateExpenseRequest req, UUID currentUserId) {
        validateGroupCreateOrUpdate(req.getGroupId(), req.getPaidBy(), req.getSplits(), currentUserId);
        List<ExpenseSplit> splits = buildSplits(req.getSplitType(), req.getTotalAmount(), req.getSplits());

        ExpenseCategory category = req.getCategory() != null ? req.getCategory() : ExpenseCategory.OTHER;

        Expense expense = new Expense(
                req.getGroupId(),
                req.getDescription().trim(),
                req.getTotalAmount(),
                req.getPaidBy(),
                currentUserId,
                req.getSplitType(),
                category
        );

        for (ExpenseSplit s : splits) {
            expense.addSplit(s);
        }

        Expense saved = expenseRepository.save(expense);
        sendExpenseNotifications(saved, TYPE_GROUP_ADDED, "A new expense was added to the group.", currentUserId);
        return ExpenseResponse.fromEntity(saved);
    }

    public ExpenseResponse createPersonalExpense(CreatePersonalExpenseRequest req, UUID currentUserId) {
        validatePersonalUsers(req.getPaidBy(), req.getSplits(), currentUserId);
        List<ExpenseSplit> splits = buildSplits(req.getSplitType(), req.getTotalAmount(), req.getSplits());

        ExpenseCategory category = req.getCategory() != null ? req.getCategory() : ExpenseCategory.OTHER;

        Expense expense = new Expense(
                null,
                req.getDescription().trim(),
                req.getTotalAmount(),
                req.getPaidBy(),
                currentUserId,
                req.getSplitType(),
                category
        );

        for (ExpenseSplit s : splits) {
            expense.addSplit(s);
        }

        Expense saved = expenseRepository.save(expense);
        sendExpenseNotifications(saved, TYPE_PERSONAL_ADDED, "A new personal expense was added.", currentUserId);
        return ExpenseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listGroupExpenses(UUID groupId, UUID currentUserId) {
        groupService.validateGroupExists(groupId);
        groupService.validateMember(groupId, currentUserId, "Caller");
        List<Expense> list = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        return list.stream().map(ExpenseResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listPersonalExpenses(UUID currentUserId) {
        return expenseRepository.findPersonalExpensesForUser(currentUserId).stream()
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listFriendPersonalExpenses(UUID friendUserId, UUID currentUserId) {
        authServiceClient.validateAcceptedFriendships(currentUserId, List.of(friendUserId));
        return expenseRepository.findPersonalExpensesBetweenUsers(currentUserId, friendUserId).stream()
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseDetail(UUID expenseId, UUID currentUserId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));
        if (expense.getGroupId() == null) {
            throw new ExpenseNotFoundException("Expense not found: " + expenseId);
        }
        groupService.validateMember(expense.getGroupId(), currentUserId, "Caller");
        return ExpenseResponse.fromEntity(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getPersonalExpenseDetail(UUID expenseId, UUID currentUserId) {
        Expense expense = expenseRepository.findPersonalExpenseByIdForUser(expenseId, currentUserId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));
        return ExpenseResponse.fromEntity(expense);
    }

    public ExpenseResponse updateExpense(UUID expenseId, CreateExpenseRequest req, UUID currentUserId) {
        return updateGroupExpense(expenseId, req, currentUserId);
    }

    public ExpenseResponse updateGroupExpense(UUID expenseId, CreateExpenseRequest req, UUID currentUserId) {
        Expense existing = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

        if (existing.getGroupId() == null) {
            throw new ExpenseNotFoundException("Expense not found: " + expenseId);
        }
        if (!existing.getGroupId().equals(req.getGroupId())) {
            throw new InvalidGroupException("Expense group cannot be changed");
        }
        if (!existing.getCreatedBy().equals(currentUserId)) {
            throw new PermissionDeniedException("Only the expense creator can update this expense");
        }

        validateGroupCreateOrUpdate(existing.getGroupId(), req.getPaidBy(), req.getSplits(), currentUserId);
        List<ExpenseSplit> splits = buildSplits(req.getSplitType(), req.getTotalAmount(), req.getSplits());

        ExpenseCategory category = req.getCategory() != null ? req.getCategory() : ExpenseCategory.OTHER;
        applyExpenseUpdate(existing, req.getDescription(), req.getTotalAmount(), req.getPaidBy(), req.getSplitType(), category, splits);
        Expense saved = expenseRepository.save(existing);
        sendExpenseNotifications(saved, TYPE_GROUP_UPDATED, "An expense was updated in the group.", currentUserId);
        return ExpenseResponse.fromEntity(saved);
    }

    public ExpenseResponse updatePersonalExpense(UUID expenseId, UpdatePersonalExpenseRequest req, UUID currentUserId) {
        Expense existing = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

        if (existing.getGroupId() != null) {
            throw new ExpenseNotFoundException("Expense not found: " + expenseId);
        }
        if (!existing.getCreatedBy().equals(currentUserId)) {
            throw new PermissionDeniedException("Only the expense creator can update this expense");
        }

        validatePersonalUsers(req.getPaidBy(), req.getSplits(), currentUserId);
        List<ExpenseSplit> splits = buildSplits(req.getSplitType(), req.getTotalAmount(), req.getSplits());

        ExpenseCategory category = req.getCategory() != null ? req.getCategory() : ExpenseCategory.OTHER;
        applyExpenseUpdate(existing, req.getDescription(), req.getTotalAmount(), req.getPaidBy(), req.getSplitType(), category, splits);
        Expense saved = expenseRepository.save(existing);
        sendExpenseNotifications(saved, TYPE_PERSONAL_UPDATED, "A personal expense was updated.", currentUserId);
        return ExpenseResponse.fromEntity(saved);
    }

    public void deleteExpense(UUID expenseId, UUID currentUserId) {
        deleteGroupExpense(expenseId, currentUserId);
    }

    public void deleteGroupExpense(UUID expenseId, UUID currentUserId) {
        Expense existing = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

        if (existing.getGroupId() == null) {
            throw new ExpenseNotFoundException("Expense not found: " + expenseId);
        }
        if (!existing.getCreatedBy().equals(currentUserId)) {
            throw new PermissionDeniedException("Only the expense creator can delete this expense");
        }

        sendExpenseNotifications(existing, TYPE_GROUP_DELETED, "An expense was deleted from the group.", currentUserId);
        expenseRepository.delete(existing);
    }

    public void deletePersonalExpense(UUID expenseId, UUID currentUserId) {
        Expense existing = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

        if (existing.getGroupId() != null) {
            throw new ExpenseNotFoundException("Expense not found: " + expenseId);
        }
        if (!existing.getCreatedBy().equals(currentUserId)) {
            throw new PermissionDeniedException("Only the expense creator can delete this expense");
        }

        sendExpenseNotifications(existing, TYPE_PERSONAL_DELETED, "A personal expense was deleted.", currentUserId);
        expenseRepository.delete(existing);
    }

    private void validateGroupCreateOrUpdate(
            UUID groupId,
            UUID paidBy,
            List<ExpenseSplitRequest> splitRequests,
            UUID currentUserId) {
        groupService.validateGroupExists(groupId);
        groupService.validateMember(groupId, currentUserId, "Creator");
        groupService.validateMember(groupId, paidBy, "Payer");

        List<UUID> userIds = splitRequests.stream()
                .map(ExpenseSplitRequest::getUserId)
                .collect(Collectors.toList());
        splitCalculationService.validateNoDuplicateUsers(userIds);

        for (UUID userId : userIds) {
            groupService.validateMember(groupId, userId, "Participant");
        }
    }

    private void validatePersonalUsers(
            UUID paidBy,
            List<ExpenseSplitRequest> splitRequests,
            UUID currentUserId) {
        List<UUID> splitUserIds = splitRequests.stream()
                .map(ExpenseSplitRequest::getUserId)
                .collect(Collectors.toList());
        splitCalculationService.validateNoDuplicateUsers(splitUserIds);

        LinkedHashSet<UUID> referencedUserIds = new LinkedHashSet<>(splitUserIds);
        referencedUserIds.add(paidBy);

        if (!currentUserId.equals(paidBy) && !referencedUserIds.contains(currentUserId)) {
            throw new PermissionDeniedException("Current user must be the payer or a participant");
        }

        authServiceClient.validateAcceptedFriendships(currentUserId, List.copyOf(referencedUserIds));
    }

    private List<ExpenseSplit> buildSplits(
            SplitType splitType,
            BigDecimal totalAmount,
            List<ExpenseSplitRequest> splitRequests) {
        List<UUID> userIds = splitRequests.stream()
                .map(ExpenseSplitRequest::getUserId)
                .collect(Collectors.toList());

        return switch (splitType) {
            case EQUAL -> splitCalculationService.calculateEqualSplits(totalAmount, userIds);
            case EXACT -> splitCalculationService.validateAndBuildExactSplits(totalAmount, splitRequests);
            case PERCENTAGE -> splitCalculationService.validateAndBuildPercentageSplits(totalAmount, splitRequests);
        };
    }

    private void applyExpenseUpdate(
            Expense existing,
            String description,
            BigDecimal totalAmount,
            UUID paidBy,
            SplitType splitType,
            ExpenseCategory category,
            List<ExpenseSplit> splits) {
        existing.setDescription(description.trim());
        existing.setTotalAmount(totalAmount);
        existing.setPaidBy(paidBy);
        existing.setSplitType(splitType);
        existing.setCategory(category != null ? category : ExpenseCategory.OTHER);
        existing.clearSplits();
        expenseRepository.saveAndFlush(existing);
        for (ExpenseSplit split : splits) {
            existing.addSplit(split);
        }
    }

    private void sendExpenseNotifications(Expense expense, String notificationType, String message, UUID actorUserId) {
        Set<UUID> recipientIds = new LinkedHashSet<>();
        try {
            recipientIds = resolveNotificationRecipients(expense, actorUserId);

            if (recipientIds.isEmpty()) {
                log.debug("No eligible recipient for {} on expense {} (group {}); dispatch skipped",
                        notificationType, expense.getId(), expense.getGroupId());
                return;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("expenseId", expense.getId().toString());
            metadata.put("amount", expense.getTotalAmount() != null ? expense.getTotalAmount().toString() : "0.00");
            metadata.put("description", expense.getDescription() != null ? expense.getDescription() : "");
            metadata.put("category", expense.getCategory() != null ? expense.getCategory().name() : "OTHER");
            metadata.put("personal", expense.getGroupId() == null);
            if (actorUserId != null) {
                metadata.put("actorUserId", actorUserId.toString());
            }

            for (UUID recipientId : recipientIds) {
                CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                        recipientId,
                        expense.getGroupId(),
                        notificationType,
                        message,
                        metadata
                );

                notificationServiceClient.createNotification(notificationRequest);
            }
        } catch (Exception e) {
            // Isolation: notification dispatch failure must never roll back expense operations
            log.warn("Failed to dispatch {} notification for expense {} in group {} to {} recipient(s): {}",
                    notificationType, expense.getId(), expense.getGroupId(), recipientIds.size(), e.toString());
        }
    }

    /**
     * Group activity reaches every active member because all members can see group expenses;
     * personal activity reaches only the people involved in it. Split participation is deliberately
     * not used for group recipients, so a member who owes nothing still learns about the expense.
     */
    private Set<UUID> resolveNotificationRecipients(Expense expense, UUID actorUserId) {
        Set<UUID> recipientIds = new LinkedHashSet<>();

        if (expense.getGroupId() != null) {
            // listMemberIds stays silent for an unknown group, unlike getGroupMemberIds which throws.
            recipientIds.addAll(groupService.listMemberIds(expense.getGroupId()));
        } else {
            for (ExpenseSplit split : expense.getSplits()) {
                if (split.getUserId() != null) {
                    recipientIds.add(split.getUserId());
                }
            }
            if (expense.getPaidBy() != null) {
                recipientIds.add(expense.getPaidBy());
            }
        }

        // Exclude the acting user from receiving notifications about their own action
        if (actorUserId != null) {
            recipientIds.remove(actorUserId);
        }

        return recipientIds;
    }
}

