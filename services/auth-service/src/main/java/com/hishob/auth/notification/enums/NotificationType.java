package com.hishob.auth.notification.enums;

/**
 * NotificationType — categorizes in-app notifications.
 *
 * Supported notification types:
 * - GROUP_MEMBER_ADDED: User was added to a group
 * - EXPENSE_ADDED: New expense created in a group
 * - EXPENSE_UPDATED: Expense was modified
 * - EXPENSE_DELETED: Expense was removed
 * - PERSONAL_EXPENSE_ADDED: Personal expense added
 * - PERSONAL_EXPENSE_UPDATED: Personal expense updated
 * - PERSONAL_EXPENSE_DELETED: Personal expense deleted
 * - EXPENSE_COMMENT_ADDED: Comment added on an expense
 * - SETTLEMENT_RECORDED: Payment recorded between members
 * - PERSONAL_SETTLEMENT_RECORDED: Personal settlement recorded
 */
public enum NotificationType {
    GROUP_MEMBER_ADDED,
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    PERSONAL_EXPENSE_ADDED,
    PERSONAL_EXPENSE_UPDATED,
    PERSONAL_EXPENSE_DELETED,
    EXPENSE_COMMENT_ADDED,
    SETTLEMENT_RECORDED,
    PERSONAL_SETTLEMENT_RECORDED
}
