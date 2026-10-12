-- ============================================================================
-- DEV ONLY. Drops and recreates the eventnest schema so edited migrations can
-- be re-applied cleanly.
--
-- Why this is sometimes necessary: Flyway stores a checksum of each applied
-- migration. Editing a migration that has already run makes its checksum fail
-- validation, and the correct fix for already-applied DDL is to rebuild the dev
-- schema from scratch rather than quietly patch production history.
--
-- NEVER run this against anything with data you care about.
-- ============================================================================
\connect eventnest
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO eventnest_app;
GRANT USAGE, CREATE ON SCHEMA public TO eventnest_app;
SELECT 'schema reset' AS status;
