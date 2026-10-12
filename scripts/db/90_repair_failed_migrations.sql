-- Run ONLY after a migration failed mid-flight.
-- PostgreSQL has transactional DDL, so Flyway rolls the failed script back -
-- but it leaves a success=false row in flyway_schema_history that blocks every
-- subsequent run until removed. Deleting that row is all "flyway repair" does.
\connect eventnest

SELECT installed_rank, version, description, type, success
  FROM flyway_schema_history ORDER BY installed_rank;

DELETE FROM flyway_schema_history WHERE success = false;

SELECT 'after repair' AS phase, version, success
  FROM flyway_schema_history ORDER BY installed_rank;
