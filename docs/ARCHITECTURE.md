# EventNest — Architecture & Design Document

**Working product name:** `EventNest` ✅ confirmed
**Document status:** v1 — **approved**, decisions below locked in
**Stack:** React + Vite · Spring Boot 3 (Java 17) · PostgreSQL 16

### Decisions confirmed with the product owner

| Question | Decision |
|---|---|
| Product name | **EventNest** (matches the project folder) |
| Roles | **STUDENT** (registers) · **ORGANIZER** (college, publishes) · **ADMIN** (governs). Two sides of a marketplace. |
| Per-event approval queue? | **No.** Approved college publishes directly. Trust is verified once per college; abuse is handled by reports + admin takedown. `moderation_required` flag retained per-college as opt-in. |
| Seed data | **Yes** — realistic, `dev` Flyway profile only, never in production |
| Local database | **The PostgreSQL 16 instance already installed on Windows**, reached from WSL at `localhost:5432` |

---

## Table of contents

1. [Product framing](#1-product-framing)
2. [System architecture](#2-system-architecture)
3. [Key architectural decisions (with rationale)](#3-key-architectural-decisions-with-rationale)
4. [Database schema](#4-database-schema)
5. [Backend architecture](#5-backend-architecture)
6. [Frontend architecture](#6-frontend-architecture)
7. [API specification](#7-api-specification)
8. [Recommended folder structure](#8-recommended-folder-structure)
9. [Development phases](#9-development-phases)
10. [Toolchain & environment findings](#10-toolchain--environment-findings)
11. [Testing strategy](#11-testing-strategy)
12. [Designed-for-now, built-later](#12-designed-for-now-built-later)

---

## 1. Product framing

### The core loop we are engineering around

```
College organizer (verified) creates event
        ↓  publish → PUBLISHED (visible immediately; no approval gate)
Event becomes discoverable
        ↓  GET /events (filter + sort, server-side)
Student discovers it in one of the discovery rails
        ↓  GET /events/{slug}
Student reads details and checks the deadline
        ↓  POST /events/{id}/register  (DB unique constraint = single source of truth)
Organizer sees the registration in their dashboard
        ↓  GET /organizer/events/{id}/registrations
Scheduler sends deadline + start reminders
        ↓  notifications table (email/push plugs in later)
Student attends · Organizer marks attendance
```

**Every schema table, endpoint, and screen below exists to serve one arrow in that loop.** Anything that
does not serve an arrow is deferred (§12).

### Naming

| | |
|---|---|
| Working name | **EventNest** — matches your folder; "nest" = where events gather. Short, ownable, not generic. |
| Alternative | `CampusPulse` — good, but "pulse" implies live/analytics; our product is discovery + registration. |
| How to rename | Brand strings live in exactly 2 places: `frontend/src/config/brand.js` and `backend/src/main/resources/application.yml` (`app.brand.*`). No hardcoded product name in components. |

### Users and the trust model

**Two sides of the marketplace, one platform in between:**

| Role | Side | Trust state | What they can do |
|---|---|---|---|
| `STUDENT` | **Demand** — consumes events | auto-`ACTIVE` on signup | discover, filter, register, cancel, bookmark, manage profile |
| `ORGANIZER` | **Supply** — a college publishing events | `PENDING` → `APPROVED` (admin grants) | create/publish/edit/cancel own events, manage its registration roster, edit its college profile |
| `ADMIN` | **Governance** | seeded, `ACTIVE` | approve colleges, manage users, review reports, remove abuse, manage categories, platform stats |

**The trust rule that makes this production-shaped, not a toy:** an organizer account is *useless* until an
admin approves it (`organizers.verification_status`). Only `APPROVED` organizers can publish. This is the
single most important product mechanic in the whole system — it is why a student can trust that "XYZ Institute
of Technology" actually *is* that college, and it is enforced exactly **once per college**, not once per event.

**Why there is no per-event approval queue (confirmed product decision):** a college is verified *once*;
after that it publishes directly. Adding a second human gate before every event would mean no organizer ever
gets an event online in time — which is precisely the problem this product exists to solve. Trust is
established at the **account** level (who is this college?) and enforced after the fact via **reports +
admin takedown**. The `moderation_required` flag and the moderation queue still exist in the schema and the
admin UI (§12) so you can switch a single college to reviewed mode without a migration.

---

## 2. System architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│  BROWSER — React SPA (Vite, Tailwind)                                   │
│  AuthContext · React Router · feature hooks · design tokens             │
└───────────────┬──────────────────────────────────────────────────────────┘
                │  HTTPS  /api/v1/**   (JSON over REST)
                │  Authorization: Bearer <accessToken>
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  REVERSE PROXY — Nginx (prod)  |  Vite dev-proxy (dev)                   │
│  serves built SPA  +  proxies /api → :8080   (dev proxy removes CORS)   │
└───────────────┬──────────────────────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  SPRING BOOT MONOLITH (modular by feature)                              │
│                                                                        │
│  Servlet filter chain:  CorsFilter → JwtAuthenticationFilter →         │
│                         Controller → @RestControllerAdvice (errors)     │
│                                                                        │
│  Feature modules: auth · user · student · college · organizer ·        │
│                  category · event · registration · bookmark ·           │
│                  notification · report · admin · storage               │
│  Cross-cutting:  security · config · common(exception, dto, util)       │
│                                                                        │
│  Spring Data JPA (Hibernate)  ──►  HikariCP  ──►  JDBC  ──►  PostgreSQL│
│  Flyway (schema migrations)  ·  MapStruct (entity↔DTO)  ·  springdoc    │
└───────────────────────────────────┬──────────────────────────────────────┘
                                    │  local disk (MVP) behind FileStorageService
                                    ▼
                    ┌───────────────────────────────┐
                    │  ./uploads  (posters, logos,  │
                    │   avatars) — swappable to S3  │
                    └───────────────────────────────┘
```

### Why a modular monolith (and not microservices)

You are one CS student shipping an MVP for real college organizers. Microservices would cost you:
network calls between "event service" and "registration service", distributed transactions (register →
increment `registration_count` → create notification), local dev setup of N containers, and no
transactional guarantees — in exchange for scaling you do not need (a few thousand events, not
millions).

So: **one deployable, one database, one transaction boundary.** But we organise the code as *modules*
with explicit boundaries (§5), so that later — if one module gets hot or needs independent deployment —
extracting it is a mechanical refactor, not a rewrite. That is the honest version of "microservices-ready":
it is a *design property*, not a marketing word.

### Request lifecycle (what happens when a student opens the home page)

1. React calls `GET /api/v1/events/home?city=Kolhapur&state=Maharashtra`.
2. No `Authorization` header → `SecurityConfig` allows this path → no filter work, no session.
3. `EventController` → `EventQueryService.findHome()` → builds a JPA `Specification` +
   one `COUNT` + one `SELECT` per rail (all inside one read-only transaction, so the rails are a
   **consistent snapshot** — you never see "4 hackathons" above "0 hackathons").
4. Hibernate translates to SQL, Hikari borrows a pooled connection, Postgres executes using the
   composite indexes from §4.
5. Entities are mapped to **DTO records** by MapStruct (inside the transaction, so no lazy-loading
   errors).
6. Jackson serialises to JSON → 200. Zero rows is `200` + empty rails, never a 404.

---

## 3. Key architectural decisions (with rationale)

| # | Decision | Why | Cost / how to undo |
|---|---|---|---|
| D1 | **Modules as packages** (`event/`, `registration/`), each with its own controller/service/repository/dto/mapper — instead of global `controller/`, `service/`, `repository/` folders | Business logic for one feature lives in one place. When you split `notification` out into its own service later, you move one folder. Global layer folders scatter a single feature across 7 folders and make that split a nightmare. You still get the `controller/service/repository/entity/dto/mapper` separation you asked for — inside each module. | None. Just convention. |
| D2 | **DTOs + MapStruct**, never expose JPA entities | (a) Prevents N+1 queries and accidental lazy-loading stack traces in JSON. (b) Stops infinite recursion on bidirectional relations (`event.organizer.college.events[].organizer…`). (c) Lets the API contract change without a DB migration. (d) Never leak `passwordHash`, `verificationStatus`, `rejectionReason` to a client by accident. | Slightly more code. Worth it. |
| D3 | **Flyway + `ddl-auto: validate`** (never `update`) | Schema changes become versioned SQL files you can review, diff, and roll back. `ddl-auto: update` silently mutates a dev DB and produces schema drift that breaks on prod. In an interview this is a *correctness* talking point, not a detail. | Manual SQL for new columns — which you want. |
| D4 | **Stateless JWT access token (30 min) + DB-backed rotating refresh token (7 days)** | Pure stateless JWT logout is *fake logout* — the token stays valid until expiry. Storing a hash of the refresh token lets you revoke it (real logout, "log out all devices"), and gives you the audit trail a college platform needs. | +1 table (`refresh_tokens`), ~80 lines. Teachable in 5 minutes. |
| D5 | **Versioned API: `/api/v1/**`** | Renaming an endpoint later breaks every deployed frontend. The `/v1` prefix costs nothing now and is the difference between a version bump and an outage. | None. |
| D6 | **Discovery = server-side filtered, paged query** (`Specification` + indexed SQL). Never "fetch all events, filter in JS" | Correctness (deadlines change, events are added), performance (indexes do the work), and scale (a page of 20 rows from a 10k-row table, not 10k rows over the wire). | Dynamic predicates are more code than `WHERE x = ?`. Worth it. |
| D7 | **Home page = ONE endpoint returning pre-built rails** (`/events/home`), not 8 parallel requests | 1 round trip instead of 8, and the rails come from a single transaction so they cannot contradict each other. `Promise.all` of 8 filtered list calls is how you ship a home page that flickers and loads in 4 different bursts. | Endpoint is less "pure REST". Worth the latency win. |
| D8 | **DB-level uniqueness for `registrations(event_id, user_id)` and `bookmarks(user_id, event_id)`** | An application-level `if (!exists) insert()` is **not** correct: two simultaneous requests both pass the check, both insert, user gets two registrations. The unique constraint is the only race-free answer. The app catches `DataIntegrityViolationException` → `409 Conflict`. This is one of the most commonly-asked interview questions in backend rounds. | None — strictly better. |
| D9 | **Optimistic capacity check** for limited-capacity events: a single conditional `UPDATE events SET registration_count = registration_count + 1 WHERE id = ? AND (max_participants IS NULL OR registration_count < max_participants)` and require `rowsAffected == 1` | Row-lock-free, race-free capacity enforcement in one statement. The naive `count()` then `insert()` oversells seats under concurrency. | Denormalised counter must be maintained in the same transaction. |
| D10 | **Direct resource JSON, `PageResponse` for lists, `ApiError` for errors** — no `{ "data": ... }` envelope on everything | Uniform wrapping adds a layer the frontend must unwrap everywhere, and it is what makes many Indian-market APIs unpleasant to consume. One consistent *error* envelope matters much more than one consistent success envelope. | Trivial to change if you disagree. |
| D11 | **CSRF disabled, CORS restricted to an allowlist** | With JWT in an `Authorization` header (not an ambient cookie), the browser will not attach credentials cross-site by itself, so the classic CSRF vector is gone. **Caveat to explain in the interview:** the moment you move refresh tokens to an `httpOnly` cookie, CSRF protection must come *back* — this is a real, commonly-missed detail. | — |
| D12 | **Hand-rolled `useAsync` hook instead of TanStack Query for the MVP** | You asked for no unnecessary libraries, and the pattern (loading / error / empty / retry, ~90 lines) is something you can explain line-by-line in an interview. TanStack Query is the correct answer at scale — documented as the swap. | Swap later behind the same hook signature. |
| D13 | **`schedule` as `JSONB`, not a child table** | Schedule is always read and written as a whole document and never queried by time. A `event_schedule` table would add a join + N+1 to render every event card's detail page for zero query benefit. JSONB = 1 row read, and Postgres can still index it with GIN if that ever changes. | Loses "find all sessions at 10am" queries. Acceptable. |
| D14 | **Denormalised `college_id`, `city`, `state`, `registration_count`, `view_count` on `events`** | These exist to keep the *hot* discovery query a single indexed index scan instead of a 3-table join on every page load. Deliberate, documented, and maintained inside the same transaction as the source of truth. | Redundancy = a bug surface if you ever forget to update a counter. Mitigated by service-layer unit tests. |
| D15 | **React Context for auth + a `fetch` wrapper. No Redux** | The MVP's shared state is: *who is logged in*, and *server data*. Server data is owned by the API (refetched, never a global cache); session state is one context. Redux would be ceremony with nothing to coordinate. | Add Zustand/Redux only when you hit real cross-tree state. |
| D16 | **Categories & colleges are database-driven** | Admin can add "Robotics" or a new college without a deploy. Also means discovery rails, filter chips, and `/colleges` all read the same source of truth. | Requires seed/migration rows — which you want for referential integrity anyway. |
| D17 | **Separate `EventQueryService` (reads) and `EventCommandService` (writes)** | The list endpoint has 10 filter/sort combinations; the write path has a strict lifecycle. Mixing them produces a 400-line service with entity locks and lazy graphs bleeding into query code. CQRS-lite with zero infrastructure — just two classes. | Two classes instead of one. |
| D18 | **JavaScript (not TypeScript) on the frontend, as requested** | Noted honestly: TS is the better production choice and catches prop-contract bugs before runtime. The frontend talks to the backend only through `services/apiClient.js` + DTO shapes, so adding `tsc` later is a contained migration. Flagged so you can defend the choice either way in an interview. | — |
| D19 | **Trust at the *account* level, not the *event* level.** An approved college publishes immediately; there is no per-event approval queue in the default flow | Two reasons. (a) **Product:** organizers publish time-sensitive events — deadlines measured in days. A human gate in front of every event defeats the entire reason the product exists. (b) **Trust:** verifying *who is publishing* once, then auditing *what they published* via reports + takedown, is both cheaper to operate and easier to reason about than reviewing every submission. `events.moderation_required` stays in the schema (default `false`) so any single college can be flipped into reviewed mode without a migration. | None — the opt-in flag and the admin queue ship with Phase 10 either way. |

---

## 4. Database schema

PostgreSQL 15+. All tables get `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` and, where mutable,
`updated_at` (maintained by Hibernate `@PreUpdate`).

### 4.1 ER overview

```
                    ┌──────────────┐
                    │    users     │  identity + password_hash + role + status
                    └──────┬───────┘
         ┌─────────────────┼────────────────────┬───────────────────┐
         │ 1:1             │ 1:1                │ 1:N               │ 1:N
  ┌──────▼───────┐  ┌──────▼──────────┐  ┌──────▼──────┐   ┌────────▼────────┐
  │student_      │  │   organizers    │  │registrations│   │   bookmarks     │
  │profiles      │  │  (approval!)    │  │ UNIQUE(ev,user)│ │ UNIQUE(user,ev) │
  └──┬─────┬─────┘  └──────┬──────────┘  └──────┬──────┘   └────────┬────────┘
     │     │                │                    │                     │
     │     └────────┐       │ 1:N                │ N:1                 │ N:1
     │              │       ▼                    ▼                     │
     │        ┌─────▼───────▼─────┐   ┌────────────────┐   ┌────────────────────┐
     │        │     colleges      │◄──┤     events     │◄──┤    notifications   │
     │        │ (directory,      │ N │ (slug, status, │ 1 │ type, event_id,    │
     │        │  verified flag)  │   │  deadline,    │ N │ is_read, action_url│
     │        └──────────────────┘   │  counts, JSONB)│   └────────────────────┘
     │                               └───────┬────────┘
     └───────────────────────────────────────┤
                    ┌────────────┐            │
                    │ categories │────────────┤
                    └────────────┘  N:1      │
                    ┌────────────┐            │
                    │  reports   │────────────┘
                    └────────────┘  (report → event / college / user)
```

### 4.2 Table-by-table

#### `users` — one identity for every role

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `email` | `VARCHAR(255)` | **UNIQUE**, stored lowercased. Login identifier. |
| `password_hash` | `VARCHAR(255)` | BCrypt output (`$2a$10$...`). Plaintext never stored, never logged, never returned. |
| `full_name` | `VARCHAR(120)` | |
| `avatar_url` | `VARCHAR(400)` | nullable |
| `role` | `VARCHAR(20)` | `CHECK IN ('STUDENT','ORGANIZER','ADMIN')` — VARCHAR+CHECK over a PG `ENUM` so new roles need no `ALTER TYPE` lock. |
| `status` | `VARCHAR(20)` | `CHECK IN ('ACTIVE','SUSPENDED','REJECTED')` |
| `email_verified` | `BOOLEAN` | default `false` (verified-email flow is §12) |
| `last_login_at` | `TIMESTAMPTZ` | nullable |

Indexes: `uq_users_email UNIQUE(email)`, `ix_users_role_status(role, status)`

#### `student_profiles` — 1:1 with a `STUDENT` user

| Column | Type | Notes |
|---|---|---|
| `user_id` | `BIGINT` | **PK + FK** → `users(id)` `ON DELETE CASCADE` |
| `college_id` | `BIGINT` | FK → `colleges(id)`, nullable until student picks a college |
| `course` | `VARCHAR(80)` | e.g. `B.Tech CSE` |
| `graduation_year` | `SMALLINT` | CHECK between 2020 and 2040 |
| `phone` | `VARCHAR(20)` | nullable |
| `city` | `VARCHAR(80)` | drives "Near You" |
| `district` | `VARCHAR(80)` | nullable |
| `state` | `VARCHAR(80)` | |
| `skills` | `TEXT[]` | GIN-indexed. Array not a child table: never queried relationally, always read as a set, and avoids a `skills` table + join for a 6-value list. |

#### `colleges` — the shared directory (students *and* organizers reference it)

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `name` | `VARCHAR(180)` | **UNIQUE** |
| `slug` | `VARCHAR(200)` | **UNIQUE** — `/colleges/xyz-institute-of-technology` |
| `logo_url` | `VARCHAR(400)` | nullable |
| `website_url` | `VARCHAR(300)` | nullable |
| `about` | `TEXT` | nullable |
| `verification_status` | `VARCHAR(20)` | `PENDING` / `VERIFIED` / `REJECTED` — drives the verified badge |
| `city`,`district`,`state`,`country` | `VARCHAR(80)` | |
| `latitude`,`longitude` | `NUMERIC(9,6)` | nullable → PostGIS-ready (see §4.5) |

Index: `uq_colleges_name`, `uq_colleges_slug`, `ix_colleges_state_city(state, city)`, `ix_colleges_verification(verification_status)`

#### `organizers` — the approval boundary

| Column | Type | Notes |
|---|---|---|
| `user_id` | `BIGINT` | **PK + FK** → `users(id)` CASCADE |
| `college_id` | `BIGINT` | **FK NOT NULL** → `colleges(id)` — *which* college this login represents |
| `designation` | `VARCHAR(80)` | e.g. `Event Coordinator, CSE Dept` |
| `department` | `VARCHAR(120)` | nullable |
| `phone` | `VARCHAR(20)` | nullable |
| `verification_status` | `VARCHAR(20)` | `PENDING` / `APPROVED` / `REJECTED` / `SUSPENDED` |
| `verified_at`, `verified_by` | `TIMESTAMPTZ`, `BIGINT` FK→`users` | admin audit trail |
| `rejection_reason` | `VARCHAR(300)` | shown to the organizer so they can fix and reapply |

> **Why approval lives here and not on `users`:** approval is a fact about *this person's authority to
> publish for this college*, not about whether a login works. Keeping it here means (a) the login keeps
> working while pending, so the applicant can see "Awaiting approval" and complete their profile; and
> (b) one college legitimately has many authorized organizer logins. Ask about this in the interview —
> it is a small design detail with a good justification.

#### `categories` — database-driven taxonomy

`id`, `name` **UNIQUE**, `slug` **UNIQUE**, `description`, `icon` (name key, e.g. `Code2` — resolved
to an icon component in the frontend, so the DB never stores markup), `color_hex` (chip tint),
`is_active BOOLEAN`, `display_order SMALLINT`.

Seeded: Hackathon, Coding Competition, Project Competition, Paper Presentation, Workshop, Seminar,
Cultural, Sports, Robotics, Entrepreneurship, Quiz, Debate, Photography, Design, Other.

#### `events` — the core entity

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `slug` | `VARCHAR(220)` | **UNIQUE** — shareable `/events/national-hackathon-2027` |
| `organizer_id` | `BIGINT` | **FK NOT NULL** → `organizers(user_id)` |
| `college_id` | `BIGINT` | **FK NOT NULL** → `colleges(id)` — denormalised from organizer (D14) |
| `category_id` | `BIGINT` | **FK NOT NULL** → `categories(id)` |
| `title` | `VARCHAR(200)` | NOT NULL |
| `summary` | `VARCHAR(300)` | the one-line subtitle on the card |
| `description` | `TEXT` | NOT NULL |
| `banner_url`,`poster_url` | `VARCHAR(400)` | nullable; poster is the card image |
| `mode` | `VARCHAR(15)` | `ONLINE` / `OFFLINE` / `HYBRID` |
| `venue`,`address_line` | `VARCHAR(255)`,`VARCHAR(300)` | e.g. `Seminar Hall A, Block 3` |
| `city`,`district`,`state`,`country` | `VARCHAR(80)` | copied from college, overridable for a different venue |
| `latitude`,`longitude` | `NUMERIC(9,6)` | nullable |
| `starts_at`,`ends_at` | `TIMESTAMPTZ` | NOT NULL, `CHECK (ends_at > starts_at)` |
| `registration_deadline` | `TIMESTAMPTZ` | NOT NULL, `CHECK (registration_deadline <= starts_at)` — enforced in the DB, not just the form |
| `registration_mode` | `VARCHAR(15)` | `INTERNAL` (register on EventNest) / `EXTERNAL` (organizer supplies their own link, e.g. Google Form) |
| `external_registration_url` | `VARCHAR(500)` | nullable; required when `EXTERNAL` |
| `max_participants` | `INTEGER` | nullable = unlimited |
| `team_size_min`,`team_size_max` | `SMALLINT` | nullable; stored now, used when team registration lands (§12) |
| `prize_title`,`prize_amount`,`prize_currency` | `VARCHAR(150)`,`NUMERIC(12,2)`,`CHAR(3)` | `prize_currency` default `INR`; amount NULL means "no cash prize" |
| `prize_description` | `TEXT` | nullable (often "Laptop + internship + certificate" beats a number) |
| `eligibility` | `TEXT` | |
| `rules` | `TEXT` | |
| `schedule` | `JSONB` | `[{ "start":"09:30","title":"Opening","description":"..." }, ...]` (D13) |
| `tags` | `TEXT[]` | |
| `coordinator_name`,`coordinator_email`,`coordinator_phone` | `VARCHAR(120)`,`VARCHAR(255)`,`VARCHAR(20)` | **Snapshot on the event, not a FK.** The real coordinator is often not a platform user; if they change jobs, last year's events must still show the right contact. |
| `status` | `VARCHAR(20)` | `DRAFT` / `PUBLISHED` / `CANCELLED` / `COMPLETED` in the default flow. `PENDING_REVIEW` / `REJECTED` exist for colleges with `moderation_required = true` (D19). |
| `moderation_required` | `BOOLEAN` | `DEFAULT false` — inherited from the college at creation; `true` forces this event through the admin queue. Stored per-event so flipping the platform default later never rewrites history. |
| `registration_count`,`view_count` | `INTEGER` | `DEFAULT 0`, `CHECK >= 0` (D14) |
| `published_at`,`cancelled_at`,`cancellation_reason` | `TIMESTAMPTZ`,`VARCHAR(300)` | |
| `created_at`,`updated_at` | `TIMESTAMPTZ` | |

**Indexes** (this is the part that makes Phase 4/5 fast — the filter combinations map 1:1 to these):

| Index | Serves |
|---|---|
| `uq_events_slug(slug)` | shareable URLs |
| `ix_events_discovery(status, starts_at)` | the default "upcoming" listing |
| `ix_events_deadline(status, registration_deadline)` | "closing soon", "upcoming deadlines" rail |
| `ix_events_category(status, category_id, starts_at)` | category rail + category filter |
| `ix_events_city(status, city, starts_at)` | city filter, "Near You" fallback |
| `ix_events_state(status, state, starts_at)` | state filter |
| `ix_events_college(status, college_id, starts_at)` | "events by this college" |
| `ix_events_organizer(organizer_id, status)` | organizer dashboard list |
| `ix_events_popular(status, registration_count DESC)` | "trending" rail |
| `ix_events_trgm` — `GIN (title gin_trgm_ops)` | `ILIKE '%hackathon%'` via `pg_trgm` |

#### `registrations`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `event_id` | `BIGINT` | **FK NOT NULL** → `events(id)` `ON DELETE CASCADE` |
| `user_id` | `BIGINT` | **FK NOT NULL** → `users(id)` `ON DELETE CASCADE` |
| `status` | `VARCHAR(20)` | `CONFIRMED` / `CANCELLED` / `WAITLISTED` / `ATTENDED` / `DISQUALIFIED` |
| `registration_number` | `VARCHAR(32)` | **UNIQUE**, e.g. `CP-7QK3M2X9` — the organizer's real-world roll number |
| `notes` | `TEXT` | student's message to the organizer ("I'd like to be in a team") |
| `cancelled_at`,`cancel_reason` | `TIMESTAMPTZ`,`VARCHAR(200)` | |
| `created_at`,`updated_at` | `TIMESTAMPTZ` | |

Constraints & indexes:
`uq_registrations_event_user UNIQUE(event_id, user_id)` **(D8)** ·
`ix_registrations_user(user_id, status, created_at DESC)` — powers `/me/registrations` ·
`ix_registrations_event(event_id, status)` — powers the organizer's roster

#### `bookmarks`

`id` PK, `user_id` FK CASCADE, `event_id` FK CASCADE, `created_at`.
`uq_bookmarks_user_event UNIQUE(user_id, event_id)` **(D8)** · `ix_bookmarks_user(user_id, created_at DESC)`.

#### `notifications`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `user_id` | `BIGINT` | FK → `users(id)` CASCADE |
| `type` | `VARCHAR(40)` | `DEADLINE_REMINDER` / `EVENT_STARTING` / `NEW_EVENT_MATCH` / `REGISTRATION_CONFIRMED` / `REGISTRATION_CANCELLED` / `EVENT_CANCELLED` / `APPROVAL_DECISION` / `REPORT_RESOLVED` / `SYSTEM` |
| `event_id` | `BIGINT` | FK → `events(id)` CASCADE, nullable |
| `title` | `VARCHAR(150)` | |
| `body` | `VARCHAR(500)` | |
| `action_url` | `VARCHAR(255)` | frontend deep link, e.g. `/events/national-hackathon-2027` |
| `is_read` | `BOOLEAN` | `DEFAULT false` |
| `read_at` | `TIMESTAMPTZ` | |
| `dedupe_key` | `VARCHAR(160)` | nullable, **UNIQUE** — see §5.6 |

> **Why `event_id` FK instead of a generic `(entity_type, entity_id)` pair:** a polymorphic reference has
> no referential integrity (you cannot delete an event and have its notifications disappear or be
> invalidated), and no cascade. One nullable FK is simpler, safer, and still covers every real
> notification type. Ask me about the trade-off if you want the generic version.

Indexes: `ix_notifications_user(user_id, is_read, created_at DESC)` (bell + list) · `uq_notifications_dedupe(dedupe_key)` · `ix_notifications_due(type, created_at)` (scheduler).

#### `reports`

`id` PK · `reporter_user_id` FK · `event_id` FK NULL · `college_id` FK NULL · `target_user_id` FK NULL ·
`reason` VARCHAR(40) (`SPAM`/`FAKE_EVENT`/`SCAM`/`INAPPROPRIATE`/`DUPLICATE`/`OTHER`) · `details` TEXT ·
`status` (`OPEN`/`REVIEWING`/`RESOLVED`/`DISMISSED`) · `resolution_note` · `resolved_by` FK · `resolved_at` · timestamps.
Index: `ix_reports_status(status, created_at DESC)`.

#### `refresh_tokens` (D4)

`id` PK · `user_id` FK CASCADE · `token_hash` VARCHAR(128) **UNIQUE** (SHA-256 of the token — never store the
token itself) · `expires_at` · `revoked_at` nullable · `replaced_by_id` nullable FK → `refresh_tokens(id)` ·
`created_at` · `user_agent` VARCHAR(300) nullable · `ip` VARCHAR(45) nullable.

#### `event_daily_stats` — *reserved, created in Phase 10*

`event_id` + `stat_date` composite PK, `views`, `registrations`, `registrations_cancelled`. Empty at MVP;
this is where per-day aggregates land so long-term charts never scan raw events. Mentioning it now costs
two lines and proves you thought about analytics.

### 4.3 Referential integrity summary

- Every FK has an explicit `ON DELETE` behaviour — **never** left implicit. Cascade only for
  ownership rows a user owns (`registrations`, `bookmarks`, `notifications`, `student_profiles`).
  `RESTRICT` (default) for editorial relations, so you cannot accidentally delete a college that still
  has events.
- `CHECK` constraints own all business invariants that must hold regardless of which code path writes:
  `ends_at > starts_at`, `registration_deadline <= starts_at`, `graduation_year` range, non-negative counters.
  A `NOT NULL` that lives only in Java is a `NOT NULL` waiting to be violated by a script.

### 4.4 Migration order

```
V1__baseline_extensions.sql        CREATE EXTENSION IF NOT EXISTS pg_trgm;
V2__create_users_colleges.sql      users, colleges, student_profiles, categories
V3__create_organizers.sql          organizers
V4__create_events.sql              events + indexes
V5__create_registrations.sql       registrations + UNIQUE(event_id,user_id)
V6__create_bookmarks.sql           bookmarks + UNIQUE(user_id,event_id)
V7__create_notifications.sql       notifications + refresh_tokens
V8__create_reports_stats.sql       reports, event_daily_stats
V9__seed_categories.sql            15 categories
V10__seed_demo_data.sql            DEV ONLY (Flyway locations per profile)
```

### 4.5 Location strategy (MVP → geospatial later)

**MVP:** `city` / `district` / `state` equality filters, backed by `(status, city, starts_at)` composite
indexes. "Near You" = same city first, then same district, then same state.

**Already stored for the future:** `latitude` / `longitude` on both `colleges` and `events`. When you want
real "within 25 km", the upgrade path is *additive, not a rewrite*:

1. `CREATE EXTENSION postgis;` → `ALTER TABLE events ADD COLUMN location geography(Point,4326);`
2. `CREATE INDEX ix_events_gist ON events USING GIST(location);`
3. Swap the repository predicate from `city = :city` to
   `ST_DWithin(location, ST_MakePoint(:lng,:lat)::geography, 25000)`.

Nothing else in the app changes, because the location contract (`city`/`district`/`state`/lat/lng on the
DTO) is already the PostGIS shape. **That is what "design the DB so geospatial can be added later" means in
practice** — this paragraph is the answer.

---

## 5. Backend architecture

### 5.1 Dependencies (deliberately short)

| Artifact | Why | Version |
|---|---|---|
| `spring-boot-starter-web` | REST + Jackson | 3.3.x (parent-managed) |
| `spring-boot-starter-data-jpa` | Hibernate ORM, repositories, Specifications | parent |
| `spring-boot-starter-security` | `BCryptPasswordEncoder`, filter chain, method security | parent |
| `spring-boot-starter-validation` | `@Valid` DTO validation → field errors for the forms | parent |
| `spring-boot-starter-actuator` | `/actuator/health` for deployment probes | parent |
| `postgresql` (runtime) | JDBC driver | parent |
| `flyway-core` + `flyway-database-postgresql` | versioned migrations (D3) | parent |
| `jjwt-api` + `jjwt-impl` + `jjwt-jackson` | JWT **sign/verify** | 0.12.x |
| `mapstruct` (+ processor) | entity↔DTO mapping (D2) | 1.6.x |
| `lombok` | entity getters/setters only | parent |
| `springdoc-openapi-starter-webmvc-ui` | `/swagger-ui.html` — live API docs | 2.6.x |
| `spring-boot-starter-test`, `testcontainers-postgresql` | tests against a real Postgres (§11) | parent |

> **On JWT libraries:** `java-jwt` (Auth0) is the common alternative and is perfectly fine. We use **jjwt**
> because its API separates *claims building* from *signing* (`Jwts.builder()` vs `Jwts.parser()`), which
> makes the two flows readable side by side — and because `jjwt-api` is an interface jar with `impl` and
> `jackson` as runtime deps, so nothing breaks when Jackson versions move. Note the well-known jjwt
> maintenance history honestly if asked.
>
> **On Lombok:** used *only* for entity accessors (`@Getter/@Setter`), never on DTOs (which are `record`s).
> Keeping it away from DTOs means the data you send over HTTP is explicit, immutable, and greppable.

### 5.2 Module map (D1)

```
com.eventnest
├── EventNestApplication.java
├── config/       SecurityConfig · JacksonConfig · OpenApiConfig · WebConfig(CORS)
│                 FileStorageProperties · ModerationProperties · SchedulingConfig
├── security/     JwtService · JwtAuthenticationFilter · CustomUserDetailsService
│                 RefreshTokenService · SecurityUtils(current user) · AuthEntryPoint(JSON 401)
├── common/
│   ├── exception/  AppException (base) · NotFound · Conflict · Forbidden · BadRequest
│   │               Unauthorized · GlobalExceptionHandler · ApiError
│   ├── dto/        PageResponse<T> · PageQuery
│   └── util/       SlugUtil · RegistrationNumberUtil
├── user/          User · UserRole · UserStatus · UserRepository · UserService · UserMapper
├── auth/          AuthController · AuthService · AuthDtos(register/login/refresh/me)
├── student/       StudentProfile · StudentController · StudentService · StudentMapper
├── college/       College · CollegeRepository · CollegeService · CollegeController · CollegeMapper
├── organizer/     Organizer · OrganizerRepository · OrganizerService · OrganizerController · ApprovalService
├── category/      Category · CategoryRepository · CategoryService · CategoryController
├── event/         Event · EventStatus · EventMode · EventRepository
│                  EventQueryService · EventCommandService · EventController
│                  EventCriteria · EventSpecifications · EventMapper · EventHomeService
├── registration/  Registration · RegistrationStatus · RegistrationRepository
│                  RegistrationService · RegistrationController · RegistrationMapper
├── bookmark/      Bookmark · BookmarkRepository · BookmarkService · BookmarkController
├── notification/  Notification · NotificationRepository · NotificationService
│                  NotificationController · ReminderScheduler · NotificationType
├── report/        Report · ReportRepository · ReportService · ReportController
├── admin/         AdminController · AdminUserController · AdminEventController
│                  AdminCollegeController · AdminCategoryController · AdminReportController · PlatformStatsService
├── storage/       FileStorageService (interface) · LocalFileStorageService · StoredFile
└── common/error/  (one ApiError shape everywhere)
```

Each feature module still contains its **controller / service / repository / entity / dto / mapper** —
you get the separation you asked for, organised by feature instead of by technical type.

### 5.3 Authentication, step by step

**Signup — student**
1. `POST /api/v1/auth/register/student` body `{fullName,email,password,collegeId,course,graduationYear,city,state,phone,skills}`
2. `@Valid` → Bean Validation: `@Email`, `@Size(min=8,max=72)`, `@Pattern` for a password character mix.
   → failure = `400` + `ApiError.fieldErrors[]` = the frontend's inline form errors, no extra work.
3. `passwordEncoder.encode(raw)` → BCrypt: a **random 16-byte salt** is generated, mixed in, and the
   result is hashed **2^10 = 1024 times**. Output `$2a$10$<22-char salt><31-char hash>`. Salting means two
   users with password `password123` have different hashes, and precomputed rainbow tables are useless.
4. Save `user` (`ACTIVE`) + `student_profiles` row in one `@Transactional` method.
5. Return `201` + user DTO + tokens. Frontend swaps `/register` → `/dashboard`.

**Signup — organizer**
Identical, plus an `organizers` row with `verification_status = 'PENDING'`. Login works; every
create-event call returns `403 {code: "ORGANIZER_NOT_APPROVED"}` and the frontend renders
**"Awaiting admin approval"**. This is the trust mechanism in action.

**Login**
1. `email.toLowerCase()` lookup → `SELECT ... WHERE email = ?`
2. `passwordEncoder.matches(raw, hash)` — BCrypt compares against the stored salt, so verification
   cannot be forged without the hash.
3. **Uniform failure:** unknown email *and* wrong password both return `401 {code:"INVALID_CREDENTIALS"}`
   with an identical message and comparable timing — otherwise the endpoint is a user-enumeration oracle.
4. Issue **access token** (JWT, HS256, 15 min, claims `sub`,`role`,`name`,`jti`) and **refresh token**
   (opaque 256-bit random string; only its SHA-256 hash is stored in `refresh_tokens`, 7 days).
5. Return `{accessToken, refreshToken, expiresIn, user}`. Client keeps the access token **in memory only**
   (lost on refresh — that is the XSS mitigation) and the refresh token in `localStorage`.

**Every authenticated request**
`JwtAuthenticationFilter` (a `OncePerRequestFilter`) → skip public paths → parse & verify signature +
expiry + `tokenType == access` → build an `Authentication` with a `ROLE_` authority → `SecurityContextHolder`.
The controller reads the user via `SecurityUtils.currentUserId()`. No session, no DB hit per request.

> **Gotcha worth memorising:** filters run **outside** `@RestControllerAdvice`. If your JWT is invalid,
> you cannot throw a `@RestControllerException` and expect a nice JSON body — you must write the 401
> response yourself in `AuthenticationEntryPoint`. This is a classic interview question and a classic
> real-world bug (blank 500s on expired tokens).

**Refresh & logout**
- `POST /api/v1/auth/refresh` → hash lookup → if valid & unrevoked & unexpired: mark revoked, issue a
  **new** refresh token (`replaced_by_id` = old one) = **rotation**. Reuse of an already-rotated token
  revokes the whole family (token-theft detection).
- `POST /api/v1/auth/logout` → revoke that refresh token. The access token dies within ≤15 min.

**Frontend handling of an expired access token** — the common "my app logs me out randomly" bug:
a single-flight refresh queue in `apiClient`: the first `401` triggers `POST /auth/refresh`; every other
in-flight request **awaits that same promise** instead of firing its own refresh (no stampede, no race);
on success the queue replays them with the new header; on failure it clears auth state and routes to
`/login?next=…`. This is ~40 lines and a genuinely strong thing to explain.

### 5.4 Role-based authorisation — three layers, defence in depth

| Layer | Mechanism | Example |
|---|---|---|
| 1. URL rules | `SecurityFilterChain` matchers | `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`, `.requestMatchers("/api/v1/organizer/**").hasRole("ORGANIZER")` |
| 2. Method security | `@EnableMethodSecurity` + `@PreAuthorize` | `deleteEvent`: `@PreAuthorize("hasRole('ADMIN')")` |
| 3. Ownership check | **service-layer assertion** | `EventCommandService.update(eventId, …)` loads the event and calls `assertCanManage(currentUserId, event)` → `403` if `event.organizerId != currentUserId` |

Layer 3 is the one people forget. URL rules answer *"is this user an organizer?"* — they cannot answer
*"is this user **the** organizer of event 42?"*. Ownership belongs in the service layer, next to the
transaction, because it is a business rule, not a transport rule. Defence in depth: if someone later
mistypes a matcher, layer 3 still protects the data.

**Event lifecycle (default flow, D19):**

```
DRAFT ──publish──▶ PUBLISHED ──start time passes──▶ COMPLETED
                      │
                  cancel│──▶ CANCELLED  (+ reason, + notification to all registrants)
```

`PUBLISHED` is reachable immediately after publish when `moderation_required = false`. With
`moderation_required = true` (per-college opt-in): `DRAFT ──submit──▶ PENDING_REVIEW ──admin──▶ PUBLISHED`
or `──▶ REJECTED` (+ reason shown to the organizer, who can edit and resubmit).

**Visibility rules (the discovery trust layer), evaluated in SQL, not in Java:**

| Situation | Public listing | Direct link `/events/{slug}` | Register button |
|---|---|---|---|
| `DRAFT` | hidden | 404 to non-owners | hidden |
| `PENDING_REVIEW` (only if `moderation_required`) | hidden | 404 to non-owners | hidden |
| `PUBLISHED`, deadline in future | **shown** | 200 | **Register Now** |
| `PUBLISHED`, deadline passed | **hidden from default listing** | 200 | **Registration Closed** (grey) |
| `PUBLISHED`, event ended | hidden from default listing | 200 | **Event concluded** |
| `REJECTED` (only if `moderation_required`) | hidden | 404 | hidden — resubmit after editing |
| `CANCELLED` | **shown, badged "Cancelled"**, pinned to top for registrants | 200 | **Cancelled** + reason; registrants get a notification |

> **Design decision to defend:** after the deadline an event *disappears from discovery* but the page
> still works when shared. Students who got the link on WhatsApp must not hit a 404 on a real event —
> showing "Registration closed, here's what it was" is both honest and good for trust. Cancelled events
> staying visible (badged) prevents the confusing "link is dead" experience.

### 5.5 Registration flow & duplicate prevention

`POST /api/v1/events/{id}/register`

```
1. Guard   : event exists? status == PUBLISHED? (else 404 / 409 EVENT_NOT_PUBLISHED)
2. Guard   : registration_deadline > now()?              (else 409 REGISTRATION_CLOSED)
3. Guard   : starts_at > now()?                          (else 409 EVENT_ALREADY_STARTED)
4. Guard   : eligibility — MVP logs the accepted terms; no auto-matching (documented limitation)
5. Guard   : max_participants — conditional UPDATE (D9); rowsAffected==0 → 409 EVENT_FULL
6. Guard   : existing registration?  → 409 ALREADY_REGISTERED  (fast path, friendly message)
7. INSERT  : registration (CONFIRMED) + registration_number (retry loop on unique violation)
8. UPDATE  : events.registration_count += 1
9. INSERT  : notification REGISTRATION_CONFIRMED for the student,
             REGISTRATION_NEW for the organizer, dedupe_key = 'reg:' + eventId + ':' + studentId
10. COMMIT (single @Transactional) → 201 {registrationNumber, status, eventSummary}
```

**Answers to your specific questions:**

- *How are users prevented from registering twice?* Two mechanisms, because one is not enough.
  The **DB `UNIQUE(event_id, user_id)`** is the only race-free guarantee — an `if (!exists)` check in Java
  has a TOCTOU window where two concurrent requests both pass it. The app-level check exists only to
  return a friendly `409 ALREADY_REGISTERED` instead of a raw constraint-violation message.
  `catch (DataIntegrityViolationException)` → translate to `409`. Belt *and* braces, and the belt is the one
  that counts.
- *Cancel:* `DELETE /events/{id}/register` → status → `CANCELLED` (row kept for the organizer's audit and
  so re-registration updates the same row), `registration_count -= 1`, notification sent. Allowed only
  before `starts_at`; after that the endpoint returns `409` and the student is told to contact the
  coordinator — which the event page surfaces prominently.
- *UI state:* `GET /events/{id}` returns `myRegistration` (`null` | `{status, registrationNumber}`) so the
  card and detail page render **"Registered · CP-7QK3M2X9"** instead of "Register Now" from one payload.

### 5.6 Search, filter & sort (Phase 5)

`EventQueryService.find(criteria)` builds a JPA `Specification<Event>` composed with `Specification.where(...)`
/ `Specification.allOf(...)`:

```
status = PUBLISHED
AND starts_at > :now
AND (city = :city)                          -- if provided
AND (state IN :states)                      -- if provided
AND (category_id IN :categoryIds)
AND (mode IN :modes)
AND (registration_deadline > :now)         -- unless includeClosed=true
AND (starts_at BETWEEN :from AND :to)
AND (title ILIKE %:q% OR summary ILIKE %:q%)   -- served by the pg_trgm GIN index
```

Sorts map to **indexed orders**, not arbitrary `ORDER BY`:
`UPCOMING → starts_at ASC` · `NEWEST → published_at DESC NULLS LAST` · `DEADLINE → registration_deadline ASC` ·
`POPULAR → registration_count DESC, starts_at ASC`.

Then `PageRequest.of(page, size, sort)` → Hibernate adds `LIMIT ? OFFSET ?` and a `COUNT(*) OVER()`.
Hard cap `size ≤ 50`. This is why Phase 5 is one focused service method instead of a mess.

**"Near You" (MVP, no PostGIS):** the same city → then district → then state, as separate rails on
`/events/home`, using the student's `student_profiles` city/state (or an explicit `city` query param when
logged out). Switch to `ST_DWithin` later per §4.5.

### 5.7 Notifications (Phase 9)

- **Write path:** `NotificationService.notify(userId, type, eventId, title, body, actionUrl, dedupeKey)`.
  `dedupe_key` is `UNIQUE` → `ON CONFLICT DO NOTHING`, so a retried job can never spam a student. This is
  the same "database is the source of truth" pattern as registration, applied to a different problem.
- **Read path:** paged list, unread-only filter, unread-count endpoint (badge), mark-one-read, mark-all-read.
- **Scheduler** (`@Scheduled`, `app.notifications.enabled=true` in dev):
  - every 30 min → events with `registration_deadline` inside 24h that have ≥1 registration → per
    registrant `DEADLINE_REMINDER` (`dedupe_key = 'deadline:' + eventId + ':' + userId`)
  - every 30 min → events starting inside 24h → per registrant `EVENT_STARTING`
  - daily 09:00 → newly published events matching a student's city/category interests →
    `NEW_EVENT_MATCH` (`dedupe_key = 'match:' + eventId + ':' + userId`)
- **Where email/push goes later:** `NotificationService.notify()` writes the DB row (always) and calls a
  `NotificationChannel` interface (`InAppChannel` today). `EmailChannel`/`PushChannel` implement the same
  one-method interface; a real deployment puts a queue (`@Async`, then RabbitMQ/Kafka) behind it. Nothing
  else in the codebase changes. **Design the seam now, implement the channel later.**

### 5.8 Error handling & HTTP semantics

One `@RestControllerAdvice GlobalExceptionHandler` → one `ApiError` shape:

```json
{
  "timestamp": "2026-10-05T09:12:44.181Z",
  "status": 409,
  "code": "ALREADY_REGISTERED",
  "message": "You have already registered for this event.",
  "path": "/api/v1/events/42/register",
  "fieldErrors": [{ "field": "email", "message": "must be a well-formed email address" }],
  "traceId": "6f1c9a2e"
}
```

| Exception | Status | Code |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` + `fieldErrors[]` |
| `BadRequestException` | 400 | (per-site) |
| `UnauthorizedException` / bad JWT | 401 | `UNAUTHORIZED` |
| `ForbiddenException` / not-owner / not-approved | 403 | `FORBIDDEN`, `ORGANIZER_NOT_APPROVED` |
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ConflictException` (already registered, closed, full, duplicate slug) | **409** | `ALREADY_REGISTERED`, `REGISTRATION_CLOSED`, `EVENT_FULL`, … |
| `DataIntegrityViolationException` | 409 | `DUPLICATE_RESOURCE` — the DB-constraint backstop (D8) |
| anything else | 500 | `INTERNAL_ERROR` — **log the detail, return a generic message.** Never leak a stack trace or SQL to a client. |

A machine-readable `code` plus a human `message` is what lets the frontend do
`if (code === 'REGISTRATION_CLOSED') showDeadlineBanner()` instead of string-matching messages.
`traceId` is echoed in the response **and** the server log so you can find the exact stack trace from a
user-reported error.

### 5.9 File uploads

`POST /api/v1/files` (auth required) multipart → validate `content-type ∈ {image/png,jpeg,webp}` and
size ≤ 5 MB → generate a **random UUID filename** (never trust the client filename — that is how path
traversal and double-extension attacks work) → store under `./uploads/{yyyy}/{MM}/{uuid}.ext` →
return `{ url: "/api/v1/files/2026/10/8f2c….png" }`. `FileStorageService` is an interface with
`LocalFileStorageService` today and `S3StorageService` tomorrow.

---

## 6. Frontend architecture

### 6.1 Stack & why

| Choice | Version | Reasoning |
|---|---|---|
| React + Vite | 18.3 / 6 | Vite = near-instant HMR; React = the ecosystem everyone (and every interviewer) expects. |
| JavaScript + JSX | — | As requested (D18). |
| React Router | 7 (declarative) | File-based routing with `ProtectedRoute` / `RoleRoute` guards. |
| Tailwind CSS | 4.x | Utility classes keep spacing/typography consistent without a 3000-line CSS file. |
| `fetch` wrapper | — | Native, zero-dependency, full control over the auth/refresh logic (§5.3). Axios would only add an interceptor we write ourselves anyway. |
| Context | — | Auth only (D15). |

### 6.2 Design system — "Ink & Signal"

Tokens live in **one CSS file** (`src/styles/theme.css`) using Tailwind v4's CSS-first `@theme` syntax, so
there is no `tailwind.config.js` to drift out of sync and no design token living in JS.

| Token group | Values | Rationale |
|---|---|---|
| Canvas | `#FAFAF9` (warm off-white), `#FFFFFF` surfaces | Warm neutral reads calmer and less "SaaS default" than pure grey/blue |
| Ink | `#18181B` primary text, `#71717A` secondary, `#E4E4E7` borders | High contrast for long descriptions |
| **Primary** | `#4338CA` deep indigo | One confident CTA colour. Buttons, active nav, links. |
| **Signal amber** | `#D97706` | **Deadlines only** — "closes in 2 days". Amber is reserved; nothing else may use it, which is exactly why the deadline rail *feels* urgent. |
| Success / Danger | `#059669` / `#DC2626` | Registered state / destructive actions |
| Radii | `10px` cards, `8px` controls, `999px` pills | |
| Shadows | one soft card shadow + one sticky-bar shadow | Restraint over depth |
| Type | Inter/system stack; `13/14/16/20/28/36px` scale; `tabular-nums` for all dates/amounts/counts | Tabular numerals are a small detail that stops countdown timers from jittering |

**Rules of the visual language:** no gradients; one border radius scale; 4px spacing grid; every card has a
1px border + 6% shadow; **date, deadline and money always use the mono/tabular treatment**; the only
"loud" colour in the entire product is amber, and it means *time is running out*.

### 6.3 Layout & routing

```
PublicLayout  ── Navbar (logo · search · category strip · NotificationBell · avatar menu) + Footer
AppLayout     ── Sidebar (role-aware) + Topbar + <Outlet/>  (student / organizer / admin)
AuthLayout    ── centred card on canvas, split brand panel on desktop
```

Route map (all from your brief, plus the guards each needs):

| Route | Guard | Layout |
|---|---|---|
| `/` | public | Public |
| `/login`, `/register`, `/register/organizer` | public (redirect if authed) | Auth |
| `/events`, `/events/:slug` | public (personalisation if authed) | Public |
| `/colleges`, `/colleges/:slug` | public | Public |
| `/dashboard`, `/my-events`, `/saved`, `/notifications`, `/profile` | `STUDENT` | App |
| `/organizer/dashboard`, `/organizer/events`, `/organizer/events/create`, `/organizer/events/:id/edit`, `/organizer/registrations`, `/organizer/profile` | `ORGANIZER` | App |
| `/admin/dashboard`, `/admin/users`, `/admin/colleges`, `/admin/events`, `/admin/reports` | `ADMIN` | App |

`ProtectedRoute` = not authed → `<Navigate to="/login" state={{ next }} />`; wrong role → `/403`
(not a silent redirect to `/dashboard`, which confuses users).
`NotificationBell` polls unread-count every 60s while authed (cheap endpoint) and refetches on focus.

### 6.4 Data fetching — `useAsync` and the four UI states

```js
const { data, loading, error, reload } = useAsync(() => api.get("/events", params), [deps]);
```

Every screen must render **four** states, and this is graded on it:

| State | Component | Example |
|---|---|---|
| Loading | `Skeleton` (shaped like the real card, not a spinner) | grey poster block + 3 grey lines |
| Error | `ErrorState` with the human message + **Retry** | "Couldn't load events. Check your connection." |
| Empty | `EmptyState` with icon, one line, one CTA | "No hackathons near you yet — widen your filters" |
| Success | real content | |

Plus: **optimistic** bookmark toggle (instant flip, rollback on failure, toast on error) and a
**disabled-with-reason** `RegistrationButton` (`Registration closed · 2 days ago`) instead of hiding the
button — the user learns *why*, which is the whole point of a deadline-driven product.

### 6.5 The API client (how frontend talks to backend)

```
components/page/hook
   → services/api.js          ← endpoint definitions + response shapes
   → services/apiClient.js    ← fetch wrapper: base URL, JSON, auth header,
                                401 single-flight refresh, ApiError normalisation
   → VITE_API_URL             ← http://localhost:8080/api/v1  (never a secret;
                                only VITE_-prefixed vars reach the bundle)
```

- **Dev:** `vite.config.js` proxies `/api` → `http://localhost:8080`, so the browser makes a **same-origin**
  request and CORS never enters the picture. (Also: relative `VITE_API_URL=/api/v1` means prod needs only
  `nginx.conf`, no rebuild.)
- **Errors** are normalised to `ApiError { status, code, message, fieldErrors }`, so a component can write
  `error.code === 'REGISTRATION_CLOSED'` instead of parsing strings.
- **Auth state:** `AuthContext` holds `{ user, accessToken }` in memory; `authService` persists only the
  refresh token and rehydrates on app start (silent refresh) — which is why a hard refresh does not log
  you out.

### 6.6 Component inventory

**Primitives** — `Button` (variants: primary/secondary/ghost/danger; sizes; `isLoading`) · `Input`,
`Select`, `Textarea`, `Chip` · `Badge` (status → colour map) · `Card` · `Modal` · `Drawer` · `Toast` ·
`Avatar` · `Skeleton` · `EmptyState` · `ErrorState` · `Pagination` · `Tabs` · `ConfirmDialog` ·
`FormField` (label + error + hint — reused by every form so validation UX is uniform).

**Domain** — `EventCard` · `EventGrid` · `EventCardSkeleton` · `EventRail` (horizontal scroller: "Trending
Events", "Hackathons", "Upcoming Deadlines") · `EventCountdown` · `DeadlineBadge` ·
`RegistrationButton` · `SaveButton` (bookmark toggle) · `ShareMenu` (Web Share API → clipboard fallback) ·
`SearchBar` (debounced 300ms) · `FilterPanel` (drawer on mobile, sidebar on desktop) · `CategoryChip` ·
`CategoryIcon` · `LocationPicker` · `NotificationBell` · `Navbar` · `Sidebar` · `CollegeCard` ·
`CollegeLogo` · `VerifiedBadge` · `PrizeTag` · `ScheduleTimeline` · `CoordinatorCard` ·
`EventForm` (the big one) · `RegistrationTable` · `StatCard` · `AdminDataTable`.

`EventCard` spec (never more than ~6 lines of text + 2 actions):
poster (3:2, `object-cover`, category chip overlaid) → title (2-line clamp) → college + verified badge →
row of `📅 Jan 15 · 09:00` / `📍 Kolhapur` → **deadline line in amber** (`⏳ 5 days left` / `Closed`) →
prize tag if present → `Register`/`Registered` + bookmark icon. Grid: 1 col mobile · 2 col tablet ·
3 col desktop.

---

## 7. API specification

**Conventions (D5, D10):** base `/api/v1` · JSON in/out · `Authorization: Bearer <token>` ·
single resource = bare JSON · lists = `{ content, page, size, totalElements, totalPages }` ·
errors = `ApiError` · page params `page` (0-based), `size` (default 12, max 50) ·
`sort` ∈ `upcoming | newest | deadline | popular`.

### 7.1 Auth & me

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register/student` | public | Create student + profile, returns tokens |
| POST | `/auth/register/organizer` | public | Create organizer (**PENDING**), returns tokens |
| POST | `/auth/login` | public | `{email,password}` → tokens + user |
| POST | `/auth/refresh` | public | Rotate refresh token → new pair |
| POST | `/auth/logout` | authed | Revoke refresh token |
| GET | `/auth/me` | authed | Current user + role-specific profile |
| GET/PUT | `/me/profile` | authed | Student profile read/update |
| PUT | `/me/avatar` | authed | Multipart avatar upload |
| GET | `/me/dashboard` | STUDENT | `{upcomingCount, closingSoon[], unreadCount, recommended[]}` |
| GET | `/me/registrations?status=&page=` | STUDENT | Registered events (paged) |
| GET | `/me/bookmarks?page=` | STUDENT | Saved events (paged) |
| GET | `/me/notifications?unreadOnly=&page=` | authed | Inbox |
| PATCH | `/me/notifications/{id}/read` | authed | Mark one read |
| POST | `/me/notifications/read-all` | authed | Mark all read |
| GET | `/me/notifications/unread-count` | authed | `{ count }` (bell badge) |

### 7.2 Discovery (public unless noted)

| Method | Path | Notes |
|---|---|---|
| GET | `/events` | Filters: `q`, `category`(csv), `collegeId`, `city`, `district`, `state`, `mode`, `from`, `to`, `includeClosed`, `registered`, `saved`, `sort`, `page`, `size` |
| GET | `/events/home` | **One call** → `{ recommended[], nearYou[], trending[], byCategory{}, closingSoon[], newlyAdded[] }` |
| GET | `/events/{idOrSlug}` | Full detail + `myRegistration` + `isSaved` |
| GET | `/events/{id}/related` | Same category / nearby, limit 6 |
| GET | `/categories` | Active, ordered, with live event counts |
| GET | `/colleges?q=&state=&verified=&page=` | Directory |
| GET | `/colleges/{idOrSlug}` | Profile + upcoming events |
| GET | `/colleges/{id}/events?page=` | That college's events |
| GET | `/meta/locations` | Distinct `state`/`city` pairs → powers the location filter |
| POST | `/events/{id}/report` | authed — `{reason, details}` |

### 7.3 Student actions

| Method | Path | Notes |
|---|---|---|
| POST | `/events/{id}/register` | 201 + `{registrationNumber, status}`; 409 on duplicate/closed/full |
| DELETE | `/events/{id}/register` | Cancel (status → `CANCELLED`, count −1) |
| GET | `/events/{id}/registration` | My status only |
| POST | `/events/{id}/bookmark` | 201; `ON CONFLICT DO NOTHING` → 200 if already saved |
| DELETE | `/events/{id}/bookmark` | 204 |

### 7.4 Organizer

| Method | Path | Notes |
|---|---|---|
| GET | `/organizer/dashboard` | `{stats, upcoming[], recentRegistrations[]}` |
| POST | `/organizer/events` | Create → `DRAFT` or `PUBLISHED` directly (D19; `PENDING_REVIEW` only if `moderation_required`) |
| GET | `/organizer/events?status=&page=` | Own events only |
| GET | `/organizer/events/{id}` | Own event + counts |
| PUT | `/organizer/events/{id}` | Update (ownership-checked) |
| PATCH | `/organizer/events/{id}/status` | `{action: SUBMIT\|PUBLISH\|CANCEL, reason?}` |
| DELETE | `/organizer/events/{id}` | Only while `DRAFT` |
| GET | `/organizer/events/{id}/registrations?status=&q=&page=` | Roster |
| PATCH | `/organizer/registrations/{id}` | `{status: ATTENDED\|DISQUALIFIED}` |
| GET | `/organizer/events/{id}/registrations/export` | CSV (nice real-world organizer win) |
| GET/PUT | `/organizer/profile` | Organizer + college details |
| POST | `/organizer/college/logo` | Multipart logo |

### 7.5 Admin

| Method | Path | Notes |
|---|---|---|
| GET | `/admin/stats` | Users/colleges/events/registrations, 30-day trend, pending counts |
| GET | `/admin/users?role=&status=&q=&page=` | |
| PATCH | `/admin/users/{id}/status` | `SUSPEND` / `ACTIVATE` (cascade-locks their access) |
| GET | `/admin/organizers?status=&page=` | Approval queue |
| PATCH | `/admin/organizers/{id}/approval` | `APPROVE` / `REJECT` + `reason` → notifies applicant |
| GET | `/admin/colleges?page=` | |
| POST/PATCH | `/admin/colleges` · `/admin/colleges/{id}` | Create / edit / `verification_status` |
| GET | `/admin/events?status=&categoryId=&q=&page=` | Moderation queue |
| PATCH | `/admin/events/{id}/moderation` | `APPROVE` / `REJECT` (only if `moderation_required`) · `REMOVE` (takedown, always available) · `FEATURE` (pin to top of discovery) |
| GET | `/admin/reports?status=&page=` | |
| PATCH | `/admin/reports/{id}` | `RESOLVE` / `DISMISS` + note |
| GET/POST/PATCH/DELETE | `/admin/categories[/{id}]` | Taxonomy management (D16) |

### 7.6 Files

`POST /files` (authed, multipart) → `{url}` · `GET /files/{yyyy}/{MM}/{name}` (public read, posters must
load for logged-out visitors).

---

## 8. Recommended folder structure

```
EventNest/
├── docs/
│   └── ARCHITECTURE.md                     ← you are reading this
├── README.md
├── .gitignore
├── docker-compose.yml                      ← Postgres 16 for dev/CI (optional locally)
│
├── backend/                                ← Spring Boot, Maven
│   ├── pom.xml
│   ├── mvnw / .mvnw                        ← wrapper: "clone and run", no global Maven needed
│   └── src/
│       ├── main/
│       │   ├── java/com/eventnest/
│       │   │   ├── config/  security/  common/{exception,dto,util}/
│       │   │   ├── user/  auth/  student/  college/  organizer/  category/
│       │   │   ├── event/  registration/  bookmark/  notification/  report/
│       │   │   ├── admin/  storage/
│       │   │   └── EventNestApplication.java
│       │   └── resources/
│       │       ├── application.yml                  ← base config + ${ENV} placeholders
│       │       ├── application-dev.yml              ← dev profile
│       │       ├── application-test.yml             ← Testcontainers
│       │       └── db/migration/V1__…  V10__…       ← Flyway (D3)
│       └── test/java/com/eventnest/…               ← unit + integration tests
│
└── frontend/                               ← React + Vite
    ├── package.json  vite.config.js  tailwind (v4: CSS-first)
    ├── index.html  .env.development  .env.production
    └── src/
        ├── main.jsx  App.jsx
        ├── styles/theme.css            ← design tokens (the design system)
        ├── config/brand.js             ← product name (rename in ONE place)
        ├── services/  apiClient.js  auth.js  events.js  colleges.js  notifications.js …
        ├── context/   AuthContext.jsx  ToastContext.jsx
        ├── hooks/     useAsync.js  useDebounce.js  useCountdown.js  useAuthGuard.js
        ├── layouts/   PublicLayout.jsx  AppLayout.jsx  AuthLayout.jsx
        ├── routes/    ProtectedRoute.jsx  RoleRoute.jsx  router.jsx
        ├── components/
        │   ├── ui/        Button Input Select Badge Card Modal Drawer Toast
        │   │              Skeleton EmptyState ErrorState Pagination Tabs Avatar …
        │   ├── layout/    Navbar Sidebar Topbar NotificationBell Footer
        │   ├── events/    EventCard EventGrid EventRail EventCountdown DeadlineBadge
        │   │              RegistrationButton SaveButton ShareMenu FilterPanel
        │   │              SearchBar CategoryChip EventForm ScheduleTimeline
        │   ├── colleges/  CollegeCard CollegeLogo VerifiedBadge
        │   └── organizer/ RegistrationTable StatCard
        ├── features/
        │   ├── auth/        hooks + auth-specific components
        │   ├── events/      useEvents, useEventFilters, EventFiltersBar
        │   ├── discovery/   useHomeFeeds (rails)
        │   ├── organizer/   useOrganizerEvents, useRegistrations
        │   └── admin/       useAdminQueues
        ├── pages/
        │   ├── public/    HomePage EventsPage EventDetailsPage CollegesPage CollegeDetailsPage
        │   ├── auth/      LoginPage RegisterPage RegisterOrganizerPage
        │   ├── student/   DashboardPage MyEventsPage SavedPage NotificationsPage ProfilePage
        │   ├── organizer/ OrganizerDashboard OrganizerEvents OrganizerEventCreate
        │   │              OrganizerEventEdit OrganizerRegistrations OrganizerProfile
        │   ├── admin/     AdminDashboard AdminUsers AdminColleges AdminEvents AdminReports
        │   └── shared/    NotFoundPage ForbiddenPage ServerErrorPage
        └── utils/  formatDate.js  formatCurrency.js  deadline.js  relativeTime.js  cn.js
```

---

## 9. Development phases

Each phase ends **runnable** and ends with something you can demo. This is the build order.

| Phase | Deliverable | Concrete exit criteria |
|---|---|---|
| **0. Foundation** | Repo, Maven+Vite skeletons, Flyway, `application.yml`, `.gitignore`, README run steps | `mvn spring-boot:run` → `/actuator/health` UP; `npm run dev` → blank page served |
| **1. Auth + roles + DB** | Migrations V1–V8, `users/refresh_tokens`, BCrypt, register(student+organizer)/login/refresh/logout, JWT filter, 3-layer authz, `/auth/me`, seed admin | Register, log out, log in, wrong password → 401, forged JWT → 401, expired → 401 JSON (not a stack trace); Postman/curl collection works |
| **2. Colleges & profiles** | `colleges` CRUD (admin-seeded), student profile, organizer profile + logo upload, verification status plumbing | Organizer sees "Awaiting approval" and cannot create events |
| **3. Event creation** | Full event form (all §4.2 fields incl. JSONB schedule, poster upload), organizer event list, edit, cancel, delete-draft | Organizer creates a full event end-to-end; invalid dates rejected by DB CHECK |
| **4. Discovery** | `GET /events` + `/events/{slug}` + `/events/home` rails, EventCard/Grid/Rail, skeleton/empty/error states, search bar | Home shows populated rails; card → detail → back |
| **5. Search & location filters** | `EventCriteria` + Specifications, all filters + 4 sorts, server-side paging, pg_trgm index, location filter | Filter by category+city+date; explain the index used (`EXPLAIN ANALYZE`) |
| **6. Registration** | Register/cancel endpoints, `UNIQUE` constraint + 409 mapping, registration number, capacity guard (D9), `myRegistration` in detail DTO, `/my-events` | Double-click register → exactly one registration; closed event → "Registration closed" |
| **7. Bookmarks** | Bookmark endpoints, optimistic toggle, `/saved`, `isSaved` on cards | Save → instant flip → survives refresh; duplicate save = no error |
| **8. Organizer registration mgmt** | Roster table with status filter/search, mark attended/disqualified, CSV export, dashboard stats | Organizer sees and filters their roster; CSV downloads |
| **9. Notifications** | Notification entity/service/bell/inbox, reminder scheduler with dedupe, "closing soon" rail | Advance a deadline, run the job, see exactly one reminder per student |
| **10. Admin moderation** | Stats dashboard, user suspend, **organizer approval queue** (the trust gate), reports queue → takedown, event queue (only for `moderation_required` colleges), category CRUD, dev seed data | Suspend a student → their token stops working; resolve a report → event removed from discovery |
| **11. Hardening & deploy** | Testcontainers suite green, `Dockerfile` + `nginx.conf` + `docker-compose.yml`, README, Swagger pass, accessibility + responsive pass, empty/error-state audit | `docker compose up` → full stack live; tests green in CI |

**Effort reality check:** Phases 0–6 are the MVP. Phases 7–10 make it trustworthy — and note that in this
product the trust gate is **Phase 10's organizer approval queue**, not a per-event queue (D19). Phase 11 is
what turns it from a project into something you can hand to a real college.

---

## 10. Toolchain & environment findings

I inspected this machine. Here is what exists and what is missing.

| Tool | Status on this machine | Plan |
|---|---|---|
| **JDK 17** | ✅ Temurin 17.0.17 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot` (runs from WSL via `java.exe`) | Install a **native Linux JDK 17 tarball** into `~/tools` — no `sudo` needed, ~10× faster Maven I/O over `/mnt/c`, no Windows path/length quirks |
| **Maven** | ⚠️ 3.9.x cached in `~/.m2/wrapper/dists` (so `mvnw` works), no `mvn` on PATH | Use the **Maven Wrapper** (`./mvnw`) — the project ships it, so any laptop/host/CI runs `./mvnw spring-boot:run` with no global install. This is also a genuine best practice, not a workaround |
| **PostgreSQL** | ✅ **PostgreSQL 16 installed on Windows**, reachable from WSL at `localhost:5432` (connection reached the server; auth rejected because no password was supplied) | Two options: (a) use your Windows instance — I need the `postgres` password; (b) a self-contained `docker-compose.yml` Postgres for dev/CI. Recommend **(a) for your machine now + (b) committed for reproducibility** |
| **Node.js** | ⚠️ v22.14.0 exists on the **Windows** side (`C:\Program Files\nodejs`), not in the WSL shell | Either run `npm` via Windows, or install Node 22 into WSL with `nvm` (no sudo). Recommend the latter so `npm run dev` hot-reload works properly |
| **Docker** | ❌ Docker Desktop installed on Windows but **WSL integration disabled** | Not required — see the two options above. Enabling WSL integration is the cleanest if you'd like `docker compose up` to work |
| Network | ✅ Maven Central + npm reachable | — |

**Blockers I need from you (Phase 0):**
1. 🔴 **The PostgreSQL password** for the Windows install (superuser `postgres`), so I can create the
   `eventnest` database + a least-privilege `eventnest_app` role. *(This is the only hard blocker.)*
2. ✅ **Confirm I may download a Linux JDK 17 + Node 22 into `~/tools`** (no `sudo`, your WSL home stays
   clean) — otherwise I fall back to the Windows JDK/Node from WSL, which works but is slower over `/mnt/c`.

Everything else is unblocked and I can start Phase 0 now.

---

## 11. Testing strategy

| Layer | Tool | What it proves |
|---|---|---|
| Unit | JUnit 5 + AssertJ | `deadline.js`-style pure logic, `EventSpecifications`, eligibility/capacity rules, `SlugUtil` |
| Service integration | `@SpringBootTest` + **Testcontainers PostgreSQL** + `@MockBean` for the clock | Registration flow, duplicate → 409, closed deadline → 409, approval gate → 403 |
| Web layer | `MockMvc` | Status codes, `ApiError` shape, role-guard 401/403/404, validation `fieldErrors` |
| Frontend | Vitest (hooks/utilities) — optional React Testing Library | `useCountdown`, `deadlineLabel`, `formatCurrency` |

**Why Testcontainers instead of H2:** H2 is a *different database* — it ignores `NUMERIC` precision,
`TEXT[]`, `JSONB`, `pg_trgm`, and (critically) it does **not** enforce the same `UNIQUE`/transaction
semantics that your duplicate-registration guarantee depends on. Testing against real PostgreSQL in a
throwaway container means "the test passes" actually means "it works in production". Slower, honest.

---

## 12. Designed-for-now, built-later

Each of these has a seam already in the design above — none requires a rewrite.

| Feature | Seam that already exists |
|---|---|
| Email / push notifications | `NotificationService.notify()` + `NotificationChannel` interface (`InAppChannel` today) |
| Google login | `users` gains `oauth_identities(user_id, provider, provider_user_id)`; a `JwtService` is provider-agnostic — issue the same token |
| Email verification / password reset | `email_verified` column exists; add a `verification_tokens` table mirroring `refresh_tokens` |
| Team registration | `team_size_min/max` columns already on `events`; add `teams` + `team_members`, change `registrations` to reference a team |
| QR check-in & certificates | `registrations.registration_number` already exists; add `checked_in_at`, `certificate_issued_at` |
| Comments, organizer ratings | `comments` table keyed to `event_id`; add `rating` on `organizers`, feed into the discovery sort |
| Personalised recommendations | `/events/home` already accepts a `studentId`; swap its rule set for a scoring function over `skills`, `city`, `category` history |
| Calendar / WhatsApp share | `ShareMenu` already wraps the Web Share API; a `.ics` endpoint is one controller method |
| Analytics & leaderboards | `event_daily_stats` table reserved (V8); `events.registration_count`/`view_count` already maintained |
| Geo search | `latitude`/`longitude` already stored → PostGIS swap per §4.5 |
| Service extraction | Feature-module boundaries (D1); each module owns its tables exclusively, so extraction is a schema move + a new deployable |
| Mobile app | REST + JWT already a clean public contract; only `/events/home` may need a graph-style endpoint later |

---

## Appendix A — Quick "explain it in the interview" crib sheet

1. **Read path** — SPA → `fetch` → JSON → REST → controller → service → JPA Specification → indexed SQL → PostgreSQL.
2. **Write path** — controller validates DTO → service checks business rules in a `@Transactional` → repository → constraint guarantees correctness → notification written in the same transaction.
3. **Auth** — BCrypt hash at rest · short-lived stateless JWT access token · DB-backed rotating refresh token for real logout · 401 JSON written by an entry point because filters bypass `@ControllerAdvice`.
4. **Authorisation** — URL matchers *and* `@PreAuthorize` *and* a service-layer ownership assertion.
5. **No duplicate registrations** — DB `UNIQUE`, with an app-level check only to return a nicer 409.
6. **Scale mindset** — every filter/sort maps to a composite index; `EXPLAIN ANALYZE` is part of the definition of done; counts are denormalised so no `COUNT(*)` on hot paths.
7. **Trust** — a college is verified **once**, at the account level; only then can it publish. No per-event approval gate, because deadlines are days away and a human gate would defeat the product. Abuse is handled after the fact: reports → admin takedown. Every approval and status change is audited (`verified_by`, `created_at`, `updated_at`).
