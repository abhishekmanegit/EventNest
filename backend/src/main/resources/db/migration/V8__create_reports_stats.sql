-- =============================================================================
-- V8: reports + event_daily_stats
-- =============================================================================

-- ----------------------------------------------------------------- reports --
-- Moderation is AFTER-THE-FACT, not a gate before publishing (decision D19).
-- A college is verified once; what it then publishes is handled by reports and
-- admin takedown. Cheaper to operate, and it never blocks a time-sensitive event.
--
-- Three nullable targets rather than a polymorphic pair, for the same reason as
-- notifications (V7): we want real FK integrity and cascade on all three.
-- Exactly one target is expected per report; the service layer enforces it.

CREATE TABLE reports (
    id                BIGSERIAL PRIMARY KEY,
    reporter_user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_id          BIGINT REFERENCES events (id) ON DELETE CASCADE,
    college_id        BIGINT REFERENCES colleges (id) ON DELETE CASCADE,
    target_user_id    BIGINT REFERENCES users (id) ON DELETE CASCADE,
    reason            VARCHAR(40) NOT NULL,
    details           TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_note   VARCHAR(500),
    resolved_by       BIGINT REFERENCES users (id) ON DELETE SET NULL,
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_reason CHECK (reason IN
        ('SPAM', 'FAKE_EVENT', 'SCAM', 'INAPPROPRIATE', 'DUPLICATE', 'OTHER')),
    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED')),
    -- At least one thing must actually be reported.
    CONSTRAINT ck_reports_has_target CHECK (
        event_id IS NOT NULL OR college_id IS NOT NULL OR target_user_id IS NOT NULL),
    CONSTRAINT ck_reports_resolved CHECK (
        status NOT IN ('RESOLVED', 'DISMISSED') OR resolved_at IS NOT NULL)
);

CREATE TRIGGER trg_reports_updated_at BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- the admin queue: open reports, oldest first
CREATE INDEX ix_reports_status ON reports (status, created_at DESC);
CREATE INDEX ix_reports_event  ON reports (event_id);


-- ------------------------------------------------------- event_daily_stats --
-- Reserved at MVP: an empty table is a schema decision, not dead code.
--
-- events.view_count / registration_count are lifetime totals, which is all the
-- MVP needs. Long-term charts (registrations per day for one event) cannot be
-- computed from a lifetime counter without losing the shape of the curve, so
-- the per-day aggregate lands in its own table from day one. Adding it in
-- Phase 10 is then an INSERT into a table that already exists rather than a
-- migration on a table with production rows.

CREATE TABLE event_daily_stats (
    event_id                BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    stat_date               DATE NOT NULL,
    views                   INTEGER NOT NULL DEFAULT 0,
    registrations           INTEGER NOT NULL DEFAULT 0,
    registrations_cancelled INTEGER NOT NULL DEFAULT 0,
    -- one row per event per day
    CONSTRAINT pk_event_daily_stats PRIMARY KEY (event_id, stat_date),
    CONSTRAINT ck_event_daily_stats_nonneg CHECK (
        views >= 0 AND registrations >= 0 AND registrations_cancelled >= 0)
);

-- trend queries: "top events in Maharashtra this week"
CREATE INDEX ix_event_daily_stats_date ON event_daily_stats (stat_date DESC);
