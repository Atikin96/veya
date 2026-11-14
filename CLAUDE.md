# CLAUDE.md — VEYA project conventions

VEYA is a diploma project (EHU, Informatics, 2026): a responsive web system for adaptive meal and
physical-activity planning with **Dynamic Plan Recovery**. Team: Hanna Ivanova (client/UX),
Mikita Malafei (backend/architecture).

## Source of truth: the Business Analysis document

`docs/BA_Malafei_Mikita.docx` is the **primary source of requirements**. Before designing or
implementing a feature:

1. Find the user story (US-xx) in BA Appendix A and implement its Given/When/Then criteria exactly.
2. Check the related NFRs (Appendix C, Table C.2), legal requirements (LEG-1..9, §2.8) and the
   data model (Figure B.11).
3. Reference the story ID in code comments for non-obvious rules, in test names/`@DisplayName`,
   and in commit/PR descriptions.

Rules:
- **Do not add features the BA does not support** (e.g. weight history, BMR/TDEE calculation,
  meal-photo model training, wearables, payments, social features, autonomous plan changes,
  LLM-made decisions). `Profile.energy_target` is a stored user goal value, not a calculated one.
- **MVP = the 27 Must stories** (BA §2.5, Appendix A): US-01–US-10, US-12–US-20, US-23, US-24,
  US-26, US-29–US-33.
  **Post-MVP — do not start without explicit approval:**
  Should: US-11, US-21, US-25, US-27, US-28, US-34, US-35; Could: US-22, US-36, US-37, US-38.
  An epic marked Must can still contain Should/Could stories.
- If the BA is ambiguous or contradicts itself, stop and ask; do not invent behavior. Known open
  questions and their resolutions are tracked in `docs/ba-open-questions.md`; record every agreed
  interpretation there. Do not implement anything that depends on an **open** item.
- The .docx is binary; extract its text (e.g. unzip `word/document.xml`) into a scratch directory to
  read it. Do not edit the BA document; a new BA version from the authors replaces the file as a whole.

## Dynamic Plan Recovery rules (EPIC 4, US-15..US-19, UC-01, Fig. B.4)

Recovery is a **separate module (`recovery`)**, distinct from `planning`. Non-negotiable rules:

- **Significant deviation** (sets `recoveryRequired`) — any of:
  - a confirmed meal differs from its planned value by **≥ 200 kcal** (default threshold, configurable);
  - a planned meal or workout is marked **missed/skipped**;
  - changed time / available ingredients / equipment make a **future** item infeasible.
  Smaller differences without a hard-constraint breach do **not** trigger automatic recovery
  (manual replacement stays available).
- Recovery is **deterministic and constraint-based** (no LLM decisions): completed items are frozen
  and immutable; remaining targets are derived from confirmed facts only; candidates are filtered
  by hard constraints (exclusions, time, equipment), then feasible combinations are scored/ranked.
  Bounded candidate set (≤100 meal + ≤100 activity), p95 ≤ 2 s (NFR-02).
- A proposal stores `baseVersion` and a human-readable **reason per changed item** (US-17).
- Decisions: **ACCEPT / EDIT / REJECT** (US-18).
  - ACCEPT/EDIT: check ownership and that `baseVersion` equals the current plan version, then save
    the decision **and** the new plan version in **one transaction** (NFR-09).
  - REJECT: record the decision; the active plan stays unchanged.
  - Stale `baseVersion` → **409**, no data changed (NFR-10). Invalid edit → **400**.
- No feasible combination → **422** with `blockingConstraints`; no new plan version (US-19).
- The active plan never changes without an explicit, authorized user decision. History is never
  rewritten: confirmed actual records stay distinct from planned items.

## Architecture

Modular monolith (BA §2.4, Fig. B.12). Root package `com.veya`. Planned modules
(post-MVP stories are shown in *italics* and are not part of the MVP scope):

| Module | Responsibility | MVP stories | Post-MVP |
|---|---|---|---|
| `identity` | Registration, sign-in (JWT), account deletion, authorization | US-01, US-04, US-30 | |
| `profile` | Profile/goal, preferences, daily planning constraints | US-02, US-03 | |
| `nutrition` | Food search, diary, totals, saved meals, meal plan, meal replacement | US-05–US-10 | *US-27, US-28 (photo), US-34 (favorites)* |
| `activity` | Basic exercise selection, workout scheduling, status, exercise replacement | US-12–US-14 | *US-11 (filters), US-35 (history filter)* |
| `planning` | Daily plan, plan items, versioning, active plan | US-09, US-10, US-12, US-14 (with nutrition/activity) | |
| `recovery` | Deviation detection, proposals, reasons, decisions, no-solution | US-15–US-19 | |
| `history` | Day dashboard; stored plan versions and decisions | US-20 | *US-21 (browsing), US-22, US-36, US-37* |
| `integration` | Food / exercise provider adapters, timeouts, fallbacks | US-05, US-31 | *US-27 (recognition provider)* |
| `shared` | Error model, common infrastructure (no business logic) | US-29, NFR-14 | |

`planning` and `shared` are not separate boxes in Fig. B.12; the split is an implementation choice
(see `docs/ba-open-questions.md`, Q9). Create a module's packages only when the first story that
needs them is implemented — no empty placeholder modules or layers.

Conventions:
- Package by module, then by layer: `web`, `application`, `domain`, `infrastructure`; other modules
  may only use a module's public `api` package or its published events.
- Each module owns its tables; no cross-module JPA relationships (reference by ID).
- REST under `/api/v1`, documented in OpenAPI (US-29, NFR-15). Errors use RFC 9457 Problem Details
  with a **stable error `code`** (NFR-14).
- Schema changes only through Flyway migrations in `backend/src/main/resources/db/migration`
  (`V<n>__<description>.sql`); never edit an applied migration. Hibernate runs with `ddl-auto: validate`.
- Never log passwords, tokens or personal payloads (LEG-5, NFR-08).

## Repository layout

```
backend/    Spring Boot 4.1 (Java 21, Maven Wrapper)
frontend/   React + TypeScript client (not scaffolded yet)
docs/       BA document, BA open questions/decisions, project documentation
infra/      Docker Compose (PostgreSQL 17) and .env.example
.github/    GitHub Actions workflows
```

## Commands

```bash
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL 17 on localhost:5433
cd backend
./mvnw verify                                      # build + all tests (requires Docker for Testcontainers)
./mvnw spring-boot:run                             # run API on :8080, health at /actuator/health
```

## Testing

- Integration tests use Testcontainers PostgreSQL 17 via `TestcontainersConfiguration`
  (`@Import(TestcontainersConfiguration.class)`); never H2.
- Recovery logic needs parameterized scenario tests with fixed data (KPI-1, KPI-3), plus 409/422/
  transaction-rollback tests (NFR-09, NFR-10). Target ≥70% coverage on selected logic (NFR-16).

## Workflow

- Do not commit, push, or open PRs without the user's explicit approval.
- Run `./mvnw verify` after changes and report the actual result.
- Keep changes scoped to the approved step; propose, then implement after approval.
