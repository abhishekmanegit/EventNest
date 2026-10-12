-- =============================================================================
-- V4: events - the core entity
-- =============================================================================
-- Two deliberate design choices in this table, both of which you should be able
-- to defend in an interview:
--
-- 1. college_id / city / state are DENORMALISED onto events rather than joined
--    through organizers -> colleges. The single hottest query in the product is
--    "list published upcoming events filtered by city/category", and these columns
--    keep it a single indexed index scan instead of a three-table join. The
--    service layer guarantees they stay consistent with the organizer.
--
-- 2. registration_count / view_count are counters, not COUNT(*) subqueries.
--    Counting rows on a list endpoint is a full extra scan per request.
--
-- Every filter the API exposes has a matching index below - that 1:1 mapping is
-- the difference between "it works" and "it works at 10k events".

CREATE TABLE events (
    id                      BIGSERIAL PRIMARY KEY,
    slug                    VARCHAR(220) NOT NULL,
    organizer_id            BIGINT NOT NULL REFERENCES organizers (user_id) ON DELETE CASCADE,
    college_id              BIGINT NOT NULL REFERENCES colleges (id) ON DELETE RESTRICT,
    category_id             BIGINT NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,

    title                   VARCHAR(200) NOT NULL,
    summary                 VARCHAR(300),
    description             TEXT NOT NULL,

    poster_url              VARCHAR(400),
    banner_url              VARCHAR(400),

    mode                    VARCHAR(15) NOT NULL DEFAULT 'OFFLINE',
    venue                   VARCHAR(255),
    address_line            VARCHAR(300),
    city                    VARCHAR(80),
    district                VARCHAR(80),
    state                   VARCHAR(80),
    country                 VARCHAR(80) NOT NULL DEFAULT 'India',
    latitude                NUMERIC(9,6),
    longitude               NUMERIC(9,6),

    starts_at               TIMESTAMPTZ NOT NULL,
    ends_at                 TIMESTAMPTZ NOT NULL,
    registration_deadline   TIMESTAMPTZ NOT NULL,

    registration_mode       VARCHAR(15) NOT NULL DEFAULT 'INTERNAL',
    external_registration_url VARCHAR(500),
    max_participants        INTEGER,

    -- Stored now, used when team registration lands (section 12). Two nullable
    -- integers are far cheaper than a migration later.
    team_size_min           INTEGER,
    team_size_max           INTEGER,

    prize_title             VARCHAR(150),
    prize_amount            NUMERIC(12,2),
    -- VARCHAR, not CHAR: Hibernate maps a Java String to Types#VARCHAR, and
    -- ddl-auto: validate compares type codes, so a CHAR column can never satisfy
    -- it no matter what columnDefinition says. Correct by construction beats
    -- correct by intent here.
    prize_currency          VARCHAR(3) NOT NULL DEFAULT 'INR',
    prize_description       TEXT,

    eligibility             TEXT,
    rules                   TEXT,

    -- Always read and written as a whole document, never queried by time.
    -- A child table would add a join + N+1 to render every detail page for
    -- zero query benefit.
    schedule                JSONB NOT NULL DEFAULT '[]'::jsonb,
    tags                    TEXT[] NOT NULL DEFAULT '{}',

    -- A snapshot, deliberately NOT a foreign key. The real coordinator is often
    -- not a platform user, and if they change jobs, last year's events must
    -- still show a working contact.
    coordinator_name        VARCHAR(120),
    coordinator_email       VARCHAR(255),
    coordinator_phone       VARCHAR(20),

    -- DRAFT -> PUBLISHED -> COMPLETED, with CANCELLED as an escape hatch.
    -- PENDING_REVIEW / REJECTED only occur for colleges flagged
    -- moderation_required (decision D19: no approval gate in the default flow).
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Inherited from the college at creation. Stored per-event so flipping the
    -- platform default later never rewrites history.
    moderation_required     BOOLEAN NOT NULL DEFAULT FALSE,

    registration_count      INTEGER NOT NULL DEFAULT 0,
    view_count              INTEGER NOT NULL DEFAULT 0,

    published_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancellation_reason     VARCHAR(300),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_events_slug           UNIQUE (slug),
    CONSTRAINT ck_events_status         CHECK (status IN
        ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_events_mode           CHECK (mode IN ('ONLINE', 'OFFLINE', 'HYBRID')),
    CONSTRAINT ck_events_registration_mode CHECK (registration_mode IN ('INTERNAL', 'EXTERNAL')),

    -- Business invariants enforced by the DATABASE, not just the form. A rule
    -- that lives only in Java is a rule waiting to be broken by a script.
    CONSTRAINT ck_events_dates          CHECK (ends_at > starts_at),
    CONSTRAINT ck_events_deadline       CHECK (registration_deadline <= starts_at),
    CONSTRAINT ck_events_counters       CHECK (registration_count >= 0 AND view_count >= 0),
    CONSTRAINT ck_events_capacity       CHECK (max_participants IS NULL OR max_participants > 0),
    CONSTRAINT ck_events_team_size      CHECK (
        team_size_min IS NULL OR team_size_max IS NULL OR team_size_max >= team_size_min),
    -- If the organizer points us at their own form (Google Forms etc.), a URL
    -- must actually exist - otherwise students get a dead button.
    CONSTRAINT ck_events_external_url   CHECK (
        registration_mode <> 'EXTERNAL' OR external_registration_url IS NOT NULL),
    -- Publishing without a deadline would break the product's core promise
    -- ("registration closes in 2 days"), so require one.
    CONSTRAINT ck_events_published_deadline CHECK (
        status <> 'PUBLISHED' OR registration_deadline IS NOT NULL)
);

CREATE TRIGGER trg_events_updated_at BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- === indexes: one per API filter/sort ===

-- default listing: published + upcoming
CREATE INDEX ix_events_discovery   ON events (status, starts_at);
-- "closing soon" rail and deadline sort
CREATE INDEX ix_events_deadline    ON events (status, registration_deadline);
-- category rail + category filter
CREATE INDEX ix_events_category    ON events (status, category_id, starts_at);
-- city filter and the MVP "Near You"
CREATE INDEX ix_events_city        ON events (status, city, starts_at);
-- state filter
CREATE INDEX ix_events_state       ON events (status, state, starts_at);
-- "events by this college" public page
CREATE INDEX ix_events_college     ON events (status, college_id, starts_at);
-- organizer dashboard list
CREATE INDEX ix_events_organizer   ON events (organizer_id, status);
-- "trending" rail
CREATE INDEX ix_events_popular     ON events (status, registration_count DESC, starts_at);
-- search: ILIKE '%term%' on title, served by the trigram index instead of a scan
CREATE INDEX ix_events_title_trgm  ON events USING GIN (title gin_trgm_ops);
-- reminder scheduler: upcoming events with a deadline
CREATE INDEX ix_events_starts_at   ON events (starts_at);

-- Search relevance combines title and summary; a trigram index on title alone
-- leaves summaries unsearchable, so index the pair in a generated column.
ALTER TABLE events
    ADD COLUMN search_text VARCHAR(700)
        GENERATED ALWAYS AS (COALESCE(title, '') || ' ' || COALESCE(summary, '')) STORED;
CREATE INDEX ix_events_search_trgm ON events USING GIN (search_text gin_trgm_ops);
