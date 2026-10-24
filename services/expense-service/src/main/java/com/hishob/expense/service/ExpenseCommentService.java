package com.hishob.expense.service;

import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.CreateExpenseCommentRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.ExpenseCommentResponse;
import com.hishob.expense.dto.UpdateExpenseCommentRequest;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseComment;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.exception.ExpenseCommentNotFoundException;
import com.hishob.expense.exception.ExpenseNotFoundException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.ExpenseCommentRepository;
import com.hishob.expense.repository.ExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseCommentService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseCommentService.class);

    private static final String TYPE_COMMENT_ADDED = "EXPENSE_COMMENT_ADDED";
    private static final int PREVIEW_MAX_LENGTH = 80;

    private final ExpenseRepository expenseRepository;
    private final ExpenseCommentRepository expenseCommentRepository;
    private final GroupService groupService;
    private final NotificationServiceClient notificationServiceClient;

    public ExpenseCommentService(
            ExpenseRepository expenseRepository,
            ExpenseCommentRepository expenseCommentRepository,
            GroupService groupService,
            NotificationServiceClient notificationServiceClient) {
        this.expenseRepository = expenseRepository;
        this.expenseCommentRepository = expenseCommentRepository;
        this.groupService = groupService;
        this.notificationServiceClient = notificationServiceClient;
    }

    @Transactional(readOnly = true)
    public List<ExpenseCommentResponse> listComments(UUID expenseId, UUID currentUserId) {
        Expense expense = findAndValidateExpenseAccess(expenseId, currentUserId);
        return expenseCommentRepository.findByExpenseIdOrderByCreatedAtAsc(expense.getId())
                .stream()
                .map(ExpenseCommentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ExpenseCommentResponse createComment(UUID expenseId, CreateExpenseCommentRequest request, UUID currentUserId) {
        Expense expense = findAndValidateExpenseAccess(expenseId, currentUserId);

        String trimmedContent = request.getContent() != null ? request.getContent().trim() : "";
        ExpenseComment comment = new ExpenseComment(expense, currentUserId, trimmedContent);
        ExpenseComment saved = expenseCommentRepository.save(comment);

        sendCommentNotifications(expense, saved, currentUserId);

        return ExpenseCommentResponse.fromEntity(saved);
    }

    public ExpenseCommentResponse updateComment(UUID expenseId, UUID commentId, UpdateExpenseCommentRequest request, UUID currentUserId) {
        Expense expense = findAndValidateExpenseAccess(expenseId, currentUserId);

        ExpenseComment comment = expenseCommentRepository.findById(commentId)
                .orElseThrow(() -> new ExpenseCommentNotFoundException("Comment not found: " + commentId));

        if (!comment.getExpense().getId().equals(expense.getId())) {
            throw new ExpenseCommentNotFoundException("Comment not found: " + commentId);
        }

        if (!comment.getAuthorUserId().equals(currentUserId)) {
            throw new PermissionDeniedException("Only the comment author can edit this comment");
        }

        String trimmedContent = request.getContent() != null ? request.getContent().trim() : "";
        comment.setContent(trimmedContent);
        comment.setUpdatedAt(Instant.now());
        ExpenseComment saved = expenseCommentRepository.saveAndFlush(comment);

        return ExpenseCommentResponse.fromEntity(saved);
    }

    public void deleteComment(UUID expenseId, UUID commentId, UUID currentUserId) {
        Expense expense = findAndValidateExpenseAccess(expenseId, currentUserId);

        ExpenseComment comment = expenseCommentRepository.findById(commentId)
                .orElseThrow(() -> new ExpenseCommentNotFoundException("Comment not found: " + commentId));

        if (!comment.getExpense().getId().equals(expense.getId())) {
            throw new ExpenseCommentNotFoundException("Comment not found: " + commentId);
        }

        boolean isAuthor = comment.getAuthorUserId().equals(currentUserId);
        boolean isExpenseCreator = expense.getCreatedBy().equals(currentUserId);

        if (!isAuthor && !isExpenseCreator) {
            throw new PermissionDeniedException("Only the comment author or the expense creator can delete this comment");
        }

        expenseCommentRepository.delete(comment);
    }

    private Expense findAndValidateExpenseAccess(UUID expenseId, UUID currentUserId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

        if (expense.getGroupId() != null) {
            groupService.validateGroupExists(expense.getGroupId());
            groupService.validateMember(expense.getGroupId(), currentUserId, "Caller");
        } else {
            if (!isPersonalExpenseParticipant(expense, currentUserId)) {
                throw new ExpenseNotFoundException("Expense not found: " + expenseId);
            }
        }

        return expense;
    }

    private boolean isPersonalExpenseParticipant(Expense expense, UUID userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(expense.getCreatedBy()) || userId.equals(expense.getPaidBy())) {
            return true;
        }
        if (expense.getSplits() == null || expense.getSplits().isEmpty()) {
            return false;
        }
        return expense.getSplits().stream()
                .map(ExpenseSplit::getUserId)
                .anyMatch(userId::equals);
    }

    private void sendCommentNotifications(Expense expense, ExpenseComment comment, UUID actorUserId) {
        Set<UUID> recipientIds = new LinkedHashSet<>();
        try {
            if (expense.getCreatedBy() != null) {
                recipientIds.add(expense.getCreatedBy());
            }
            if (expense.getPaidBy() != null) {
                recipientIds.add(expense.getPaidBy());
            }
            if (expense.getSplits() != null) {
                for (ExpenseSplit split : expense.getSplits()) {
                    if (split.getUserId() != null) {
                        recipientIds.add(split.getUserId());
                    }
                }
            }

            // Exclude the comment author
            recipientIds.remove(actorUserId);

            if (recipientIds.isEmpty()) {
                log.debug("No eligible recipient for {} on expense {} (group {}); dispatch skipped",
                        TYPE_COMMENT_ADDED, expense.getId(), expense.getGroupId());
                return;
            }

            String preview = comment.getContent();
            if (preview != null && preview.length() > PREVIEW_MAX_LENGTH) {
                preview = preview.substring(0, PREVIEW_MAX_LENGTH) + "...";
            }

            String message = expense.getGroupId() != null
                    ? "A new comment was added to an expense in the group."
                    : "A new comment was added to the personal expense.";

            Map<String, Object> metadata = Map.of(
                    "expenseId", expense.getId().toString(),
                    "commentId", comment.getId().toString(),
                    "commentPreview", preview != null ? preview : "",
                    "description", expense.getDescription() != null ? expense.getDescription() : "",
                    "personal", expense.getGroupId() == null,
                    "actorUserId", actorUserId.toString()
            );

            for (UUID recipientId : recipientIds) {
                CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                        recipientId,
                        expense.getGroupId(),
                        TYPE_COMMENT_ADDED,
                        message,
                        metadata
                );
                notificationServiceClient.createNotification(notificationRequest);
            }
        } catch (Exception e) {
            // Isolation: ensure notification failure never rolls back comment creation
            log.warn("Failed to dispatch {} notification for expense {} in group {} to {} recipient(s): {}",
                    TYPE_COMMENT_ADDED, expense.getId(), expense.getGroupId(), recipientIds.size(), e.toString());
        }
    }
}
