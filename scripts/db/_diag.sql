\connect eventnest
\dt
SELECT count(*) AS table_count FROM information_schema.tables WHERE table_schema='public';
SELECT current_user, current_database();
