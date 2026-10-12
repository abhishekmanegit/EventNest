-- Create the application database. CREATE DATABASE cannot run inside a
-- transaction block / DO block, so this is a separate script from 01_bootstrap.sql.
--   psql -U postgres -h localhost -f 02_create_database.sql
CREATE DATABASE eventnest
    OWNER eventnest_app
    ENCODING 'UTF8'
    LC_COLLATE 'English_United States.1252'
    LC_CTYPE 'English_United States.1252'
    TEMPLATE template0;

\connect eventnest

-- The application role needs to create Flyway's schema-history table and
-- extensions (pg_trgm) on boot. It owns the database, so this is already true;
-- the grants below make the intent explicit.
GRANT ALL ON SCHEMA public TO eventnest_app;

SELECT 'database eventnest ready' AS status;
