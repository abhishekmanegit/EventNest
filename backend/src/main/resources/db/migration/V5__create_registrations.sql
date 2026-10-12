-- =============================================================================
-- V5: registrations
-- =============================================================================
-- The single most important constraint in this product:
--
--   UNIQUE (event_id, user_id)
--
-- An application-level "if (!exists) insert()" check is NOT correct. Two requests
-- arriving at the same time can both pass the check and both insert - the user
-- ends up registered twice. The database constraint is the only race-free
-- guarantee, so it is the real source of truth. The service layer still checks
-- first, purely so the API can return a friendly 409 ALREADY_REGISTERED instead
-- of a raw constraint-violation message.
--
-- ON DELETE CASCADE: a registration is owned by the event/user pair, so deleting
-- either side removes it. Unlike editorial relations (colleges -> events, which is
-- RESTRICT) there is no scenario where orphaning one is correct.

CREATE TABLE registrations (
    id                   BIGSERIAL PRIMARY KEY,
    event_id             BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id              BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status               VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    -- The organizer's real-world roll number. A random Crockford-style alphabet
    -- avoids look-alike characters (0/O, 1/I) when read aloud or retyped.
    registration_number  VARCHAR(32) NOT NULL,
    notes                TEXT,
    cancelled_at         TIMESTAMPTZ,
    cancel_reason        VARCHAR(200),
    checked_in_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Row kept on cancel rather than deleted: it is the organizer's audit trail,
    -- and re-registering updates this same row instead of inserting a duplicate.
    CONSTRAINT uq_registrations_event_user   UNIQUE (event_id, user_id),
    CONSTRAINT uq_registrations_number       UNIQUE (registration_number),
    CONSTRAINT ck_registrations_status       CHECK (status IN
        ('CONFIRMED', 'CANCELLED', 'WAITLISTED', 'ATTENDED', 'DISQUALIFIED')),
    -- A cancellation must always record when and ideally why.
    CONSTRAINT ck_registrations_cancelled    CHECK (
        status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE TRIGGER trg_registrations_updated_at BEFORE UPDATE ON registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- powers GET /api/v1/me/registrations
CREATE INDEX ix_registrations_user  ON registrations (user_id, status, created_at DESC);
-- powers the organizer's roster for one event
CREATE INDEX ix_registrations_event ON registrations (event_id, status, created_at);
