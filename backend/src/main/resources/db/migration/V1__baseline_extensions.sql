-- =============================================================================
-- V1: Baseline extensions and shared trigger function
-- =============================================================================
-- pg_trgm gives us an indexable LIKE/ILIKE. Without it, searching event titles
-- with ILIKE '%hackathon%' is a sequential scan of the whole events table.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Keeps updated_at honest at the DATABASE level rather than relying on every
-- application code path remembering to set it. A column that can silently go
-- stale is worse than not having it.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
