-- =============================================================================
-- HISHOB Render PostgreSQL Schema Initialization Script
-- =============================================================================
-- Purpose:
--   Creates isolated schemas within a single Render PostgreSQL database for the
--   four database-backed microservices: Auth, Group, Expense, and Settlement.
--
-- Safety & Idempotency:
--   - Uses "CREATE SCHEMA IF NOT EXISTS" so it can be safely re-run without error.
--   - Wrapped in a transaction block (BEGIN ... COMMIT).
--   - Grants all privileges on each schema to CURRENT_USER.
--
-- Usage:
--   Run this ONCE against your freshly provisioned Render PostgreSQL database
--   using psql or Render's PostgreSQL web shell:
--     psql "<RENDER_POSTGRES_EXTERNAL_CONNECTION_STRING>" -f scripts/init-schemas.sql
--
-- DO NOT EXECUTE AUTOMATICALLY OR AGAINST PRODUCTION/LOCAL INSTANCES.
-- =============================================================================

BEGIN;

-- 1. Auth Service Schema
CREATE SCHEMA IF NOT EXISTS hishob_auth;
GRANT ALL ON SCHEMA hishob_auth TO CURRENT_USER;

-- 2. Group Service Schema
CREATE SCHEMA IF NOT EXISTS hishob_group;
GRANT ALL ON SCHEMA hishob_group TO CURRENT_USER;

-- 3. Expense Service Schema
CREATE SCHEMA IF NOT EXISTS hishob_expense;
GRANT ALL ON SCHEMA hishob_expense TO CURRENT_USER;

-- 4. Settlement Service Schema
CREATE SCHEMA IF NOT EXISTS hishob_settlement;
GRANT ALL ON SCHEMA hishob_settlement TO CURRENT_USER;

COMMIT;

-- Verify schema creation:
-- SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'hishob_%';
