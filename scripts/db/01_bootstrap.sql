-- Bootstrap script: creates the application role and database.
-- Run once, as the postgres superuser.
--   psql -U postgres -h localhost -f 01_bootstrap.sql

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'eventnest_app') THEN
        CREATE ROLE eventnest_app LOGIN PASSWORD 'eventnest_local_dev';
    END IF;
END
$$;

ALTER ROLE eventnest_app SET search_path TO public;

SELECT 'role eventnest_app ready' AS status;
