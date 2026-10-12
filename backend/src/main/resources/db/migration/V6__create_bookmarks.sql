-- =============================================================================
-- V6: bookmarks
-- =============================================================================
-- Identical duplicate-prevention story to registrations (see V5): the database
-- constraint is the guarantee, the pre-check exists only to return a nicer status
-- code. POST is idempotent by design - saving an already-saved event is a no-op
-- that returns success, not an error, because a double-tapping a bookmark button
-- is not a user mistake worth an error message.

CREATE TABLE bookmarks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_id    BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bookmarks_user_event UNIQUE (user_id, event_id)
);

-- powers GET /api/v1/me/bookmarks, newest first
CREATE INDEX ix_bookmarks_user ON bookmarks (user_id, created_at DESC);
