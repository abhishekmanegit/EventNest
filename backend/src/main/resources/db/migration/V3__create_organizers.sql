-- =============================================================================
-- V3: organizers - the trust boundary
-- =============================================================================
-- A college is verified ONCE, here. Until verification_status = 'APPROVED', the
-- login works but every "create event" call is rejected with 403.
--
-- Why approval lives on organizers and not on users (this is worth being able to
-- explain): approval is a fact about a person's authority to publish for a
-- college, not about whether a login works. Keeping it here means
--   (a) a pending applicant can still log in, finish their profile, and see
--       "Awaiting approval" instead of hitting a support wall, and
--   (b) one college can legitimately have many authorized organizer logins
--       (a coordinator, a faculty advisor, a student head).
--
-- PK is user_id (1:1 with users). A college, meanwhile, has MANY organizers -
-- that is 1:N from colleges to organizers and the key that expresses it is
-- college_id below.

CREATE TABLE organizers (
    user_id            BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    college_id         BIGINT NOT NULL REFERENCES colleges (id) ON DELETE RESTRICT,
    designation        VARCHAR(80),
    department         VARCHAR(120),
    phone              VARCHAR(20),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at        TIMESTAMPTZ,
    verified_by        BIGINT REFERENCES users (id) ON DELETE SET NULL,
    rejection_reason   VARCHAR(300),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_organizers_verification_status
        CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    -- An approved organizer must have an approver and a timestamp: the trust
    -- decision is always auditable ("who approved this college, and when?").
    CONSTRAINT ck_organizers_approval_audit
        CHECK (verification_status <> 'APPROVED' OR (verified_at IS NOT NULL AND verified_by IS NOT NULL))
);

CREATE TRIGGER trg_organizers_updated_at BEFORE UPDATE ON organizers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX ix_organizers_college ON organizers (college_id);
CREATE INDEX ix_organizers_verification ON organizers (verification_status, created_at);
