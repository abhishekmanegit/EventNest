-- =============================================================================
-- V2: colleges, users, student_profiles, categories
-- =============================================================================
-- colleges is created first: both student_profiles and organizers reference it.
-- It is a shared directory - a student picks their college from it, and an
-- organizer represents one of them. Database-driven like categories (decision D16)
-- so admins can add a college without a code deploy.

CREATE TABLE colleges (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(180) NOT NULL,
    slug                VARCHAR(200) NOT NULL,
    logo_url            VARCHAR(400),
    website_url         VARCHAR(300),
    about               TEXT,
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    city                VARCHAR(80)  NOT NULL,
    district            VARCHAR(80),
    state               VARCHAR(80)  NOT NULL,
    country             VARCHAR(80)  NOT NULL DEFAULT 'India',
    -- Stored now so PostGIS distance queries can be added later with an
    -- additive migration only (see ARCHITECTURE.md section 4.5).
    latitude            NUMERIC(9,6),
    longitude           NUMERIC(9,6),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_colleges_name  UNIQUE (name),
    CONSTRAINT uq_colleges_slug  UNIQUE (slug),
    CONSTRAINT ck_colleges_verification_status
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TRIGGER trg_colleges_updated_at BEFORE UPDATE ON colleges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX ix_colleges_state_city ON colleges (state, city);
CREATE INDEX ix_colleges_verification ON colleges (verification_status);
CREATE INDEX ix_colleges_name_trgm ON colleges USING GIN (name gin_trgm_ops);


-- ---------------------------------------------------------------- users ----
-- One identity row for every role. Password is stored ONLY as a BCrypt hash
-- (salt + 1024 rounds). Plaintext is never persisted, never logged, never returned.
--
-- Role/status are VARCHAR + CHECK rather than a PostgreSQL ENUM: adding a value
-- later becomes an ordinary UPDATE instead of ALTER TYPE ... which takes a lock
-- and cannot run inside most transactions.

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    avatar_url     VARCHAR(400),
    role           VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Case-insensitive uniqueness: we lowercase on write, this makes it structural.
    CONSTRAINT uq_users_email       UNIQUE (email),
    CONSTRAINT ck_users_role        CHECK (role IN ('STUDENT', 'ORGANIZER', 'ADMIN')),
    CONSTRAINT ck_users_status      CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REJECTED'))
);

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX ix_users_role_status ON users (role, status);


-- ------------------------------------------------------ student_profiles ----
-- 1:1 with a STUDENT user. PK is also the FK, which is how a 1:1 is expressed
-- in SQL (and guarantees you cannot accidentally create two profiles).

CREATE TABLE student_profiles (
    user_id          BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    college_id       BIGINT REFERENCES colleges (id) ON DELETE SET NULL,
    course           VARCHAR(80),
    -- INTEGER, not SMALLINT: Hibernate maps Java int to INTEGER, and
    -- validate() would fail on SMALLINT. 4 bytes here saves casts everywhere else.
    graduation_year  INTEGER,
    phone            VARCHAR(20),
    city             VARCHAR(80),
    district         VARCHAR(80),
    state            VARCHAR(80),
    -- A set that is always read whole and never queried relationally. A child
    -- table + join would buy us nothing for a six-value list.
    skills           TEXT[] NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_student_graduation_year
        CHECK (graduation_year IS NULL OR graduation_year BETWEEN 2000 AND 2040)
);

CREATE INDEX ix_student_profiles_college ON student_profiles (college_id);
CREATE INDEX ix_student_profiles_city_state ON student_profiles (state, city);
CREATE INDEX ix_student_profiles_skills ON student_profiles USING GIN (skills);


-- ----------------------------------------------------------- categories ----
-- Database-driven taxonomy: discovery rails, filter chips, and the /categories
-- API all read this table, so adding "Robotics" is an INSERT, not a deploy.

CREATE TABLE categories (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(80)  NOT NULL,
    slug           VARCHAR(90)  NOT NULL,
    description    VARCHAR(300),
    icon           VARCHAR(40)  NOT NULL DEFAULT 'Sparkles',
    color_hex      VARCHAR(7)   NOT NULL DEFAULT '#4338CA',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order  INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

CREATE INDEX ix_categories_active_order ON categories (is_active, display_order);
