-- =============================================================================
-- V11: featured events (admin "pin to top of discovery")
-- =============================================================================
-- An admin moderation action that the architecture already specifies
-- (ARCHITECTURE.md 7.5, FEATURE) but that has no column to land on.
--
-- Why a column rather than an ordering trick or a separate "promoted events"
-- table: a feature is a property of one event, it must survive the event being
-- edited, and it must be visible in the moderation queue without a join. One
-- boolean on the row gives all three, and defaults to false so no existing row
-- changes behaviour on migration.
--
-- Default false rather than NULL: "not featured" and "feature state unknown"
-- are the same fact here, and a nullable column would make every consumer write
-- `COALESCE(featured, false)` to avoid sorting NULLs above true.

ALTER TABLE events ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;

-- Supports the pin-to-top ordering: ORDER BY featured DESC, starts_at ASC is
-- exactly this index's key sequence, so the promotion happens in the index
-- rather than in a sort node over the whole result set.
CREATE INDEX ix_events_featured ON events (featured DESC, starts_at ASC);

-- Why a second column in the same migration: REJECTED is an event state the
-- schema already allows, but nothing recorded *why* - so an organizer whose event
-- was rejected got a status and no explanation, and had to email support to learn
-- what to fix. The rejection reason is the whole value of a review queue; storing
-- the outcome without it turns a gate into a wall.
ALTER TABLE events ADD COLUMN moderation_reason VARCHAR(500);
