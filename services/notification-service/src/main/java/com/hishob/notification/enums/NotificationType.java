package com.hishob.notification.enums;

/**
 * NotificationType — categorizes in-app notifications.
 *
 * MVP notification types:
 * - GROUP_MEMBER_ADDED: User was added to a group
 * - EXPENSE_ADDED: New expense created in a group
 * - EXPENSE_UPDATED: Expense was modified
 * - EXPENSE_DELETED: Expense was removed
 * - SETTLEMENT_RECORDED: Payment recorded between members
 */
public enum NotificationType {
    GROUP_MEMBER_ADDED,
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    PERSONAL_EXPENSE_ADDED,
    PERSONAL_EXPENSE_UPDATED,
    PERSONAL_EXPENSE_DELETED,
    SETTLEMENT_RECORDED,
    PERSONAL_SETTLEMENT_RECORDED
}
