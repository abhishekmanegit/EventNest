-- =============================================================================
-- V10: colleges.moderation_required
-- =============================================================================
-- events.moderation_required has existed since V4 but had no source: it is meant
-- to be copied from the college at event creation (decision D19 - "per-college
-- opt-in"), and the colleges table never had the column.
--
-- Additive, so it lands as a new versioned migration rather than an edit to V2 -
-- editing an applied migration changes its checksum and forces a rebuild of the
-- dev schema for no benefit.
--
-- Default FALSE is the whole point: moderation is the exception. A college that
-- wants a human in the loop flips this one flag; everyone else keeps publishing
-- directly, which is what makes the platform usable for a time-sensitive event.

ALTER TABLE colleges
    ADD COLUMN moderation_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN colleges.moderation_required IS
    'When true, events created for this college start in PENDING_REVIEW instead of going straight to PUBLISHED (ARCHITECTURE.md decision D19).';