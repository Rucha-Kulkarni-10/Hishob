-- ============================================================================
-- Migration: V1__allow_null_group_id_in_settlements.sql
-- Service:   settlement-service
-- Database:  hishob_settlement
-- Table:     settlements
--
-- Purpose:
--   Alters the "group_id" column on the "settlements" table to allow NULL values.
--   This supports direct personal settlements between friends (pairwise debts)
--   where groupId is intentionally NULL, as defined in Settlement.java.
--
-- Safety Guarantees:
--   - Non-destructive: Does NOT drop table, truncate rows, or delete settlements.
--   - Data preserving: All existing group settlements retain their non-null group_id.
--   - Atomic: Wrapped in a transaction block (BEGIN ... COMMIT).
--   - Idempotent: In PostgreSQL, ALTER COLUMN ... DROP NOT NULL is safe to re-run.
-- ============================================================================

BEGIN;

ALTER TABLE settlements 
    ALTER COLUMN group_id DROP NOT NULL;

COMMIT;
