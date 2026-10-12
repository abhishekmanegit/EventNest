-- =============================================================================
-- V7: notifications + refresh_tokens
-- =============================================================================

-- ------------------------------------------------------------ notifications --
-- design choice worth explaining: a nullable event_id FOREIGN KEY instead of a
-- generic (entity_type, entity_id) pair.
--
-- A polymorphic reference has no referential integrity - nothing stops a
-- notification row pointing at event 9999, which no longer exists - and no
-- cascade, so deleting an event leaves dangling notifications. One nullable FK
-- is simpler, safer, and still covers every notification type we actually send
-- (the non-event ones simply leave it NULL).
--
-- dedupe_key is what makes the reminder scheduler safe to re-run: INSERT with
-- ON CONFLICT DO NOTHING against a UNIQUE key means a retried or duplicated job
-- can never spam a student. Same principle as the registration constraint -
-- let the database enforce "exactly once", not application timing.

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_id    BIGINT REFERENCES events (id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(150) NOT NULL,
    body        VARCHAR(500),
    action_url  VARCHAR(255),
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMPTZ,
    dedupe_key  VARCHAR(160),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_notifications_read   CHECK (is_read = FALSE OR read_at IS NOT NULL),
    CONSTRAINT ck_notifications_type   CHECK (type IN (
        'DEADLINE_REMINDER', 'EVENT_STARTING', 'NEW_EVENT_MATCH',
        'REGISTRATION_CONFIRMED', 'REGISTRATION_CANCELLED', 'NEW_REGISTRATION',
        'EVENT_CANCELLED', 'APPROVAL_DECISION', 'REPORT_RESOLVED', 'SYSTEM'))
);

-- The bell + the inbox list, both filtered by read state and sorted by recency.
CREATE INDEX ix_notifications_user ON notifications (user_id, is_read, created_at DESC);
-- the reminder scheduler scans for due work by type and age
CREATE INDEX ix_notifications_due  ON notifications (type, created_at);


-- ----------------------------------------------------------- refresh_tokens --
-- Why this table exists at all: a purely stateless JWT cannot be revoked. Logout
-- would be theatre - the token stays valid until it expires. Storing a HASH of
-- the refresh token makes logout real, enables "log out all devices", and gives
-- us the audit trail a college platform needs.
--
-- Only the SHA-256 hash is stored. If the database leaks, the hashes are useless
-- as credentials because they cannot be presented to /auth/refresh (which hashes
-- what it receives and compares) - the attacker would need the original random
-- strings, which were never persisted.

CREATE TABLE refresh_tokens (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    -- set when this token is rotated, pointing at its replacement. Lets us
    -- detect replay of an already-rotated token (i.e. a stolen token).
    replaced_by_id BIGINT REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent     VARCHAR(300),
    ip_address     VARCHAR(45),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- refresh lookup: hash -> row, and "is this user still active?"
CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
-- lets the scheduled cleanup job find expired rows without a table scan
CREATE INDEX ix_refresh_tokens_active  ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;
