-- ============================================================================
-- Migration: V1__update_notifications_type_check.sql
-- Service:   notification-service
-- Database:  hishob_notification
-- Table:     notifications
--
-- Purpose:
--   Updates the legacy "notifications_type_check" check constraint to support
--   all current NotificationType enum constants introduced across HISHOB:
--     1.  GROUP_MEMBER_ADDED
--     2.  EXPENSE_ADDED
--     3.  EXPENSE_UPDATED
--     4.  EXPENSE_DELETED
--     5.  PERSONAL_EXPENSE_ADDED
--     6.  PERSONAL_EXPENSE_UPDATED
--     7.  PERSONAL_EXPENSE_DELETED
--     8.  EXPENSE_COMMENT_ADDED
--     9.  SETTLEMENT_RECORDED
--     10. PERSONAL_SETTLEMENT_RECORDED
--
-- Safety Guarantees:
--   - Non-destructive: Does NOT drop table, truncate rows, or delete notifications.
--   - Idempotent: Uses IF EXISTS on DROP CONSTRAINT.
--   - Backward compatible: Retains all 5 original group notification types.
-- ============================================================================

BEGIN;

ALTER TABLE notifications 
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications 
    ADD CONSTRAINT notifications_type_check CHECK (
        type::text = ANY (ARRAY[
            'GROUP_MEMBER_ADDED'::character varying,
            'EXPENSE_ADDED'::character varying,
            'EXPENSE_UPDATED'::character varying,
            'EXPENSE_DELETED'::character varying,
            'PERSONAL_EXPENSE_ADDED'::character varying,
            'PERSONAL_EXPENSE_UPDATED'::character varying,
            'PERSONAL_EXPENSE_DELETED'::character varying,
            'EXPENSE_COMMENT_ADDED'::character varying,
            'SETTLEMENT_RECORDED'::character varying,
            'PERSONAL_SETTLEMENT_RECORDED'::character varying
        ]::text[])
    );

COMMIT;
