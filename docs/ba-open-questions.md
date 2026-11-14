# BA open questions and agreed interpretations

Source: `docs/BA_Malafei_Mikita.docx` (corrected version, 27 Must / 7 Should / 4 Could stories).

This file lists places where the BA is ambiguous, contradicts itself, or does not cover something
the Must scope depends on. **Nothing here is decided until its status is `AGREED`.** Do not implement
behavior that depends on an `OPEN` item. When the team agrees, fill in *Decision* and *Date*; if
the decision changes the BA itself, the BA authors update the .docx.

Status values: `OPEN` · `AGREED` · `BA UPDATED` (fixed in the BA, entry kept for traceability).

## Summary

| ID | Topic | Blocks | Status |
|---|---|---|---|
| Q1 | Status code for creating a recovery proposal (201 vs 200) | US-16 API / OpenAPI | OPEN |
| Q2 | How plan versions and their history are stored | first `planning` migration, US-10, US-14, US-18 | OPEN |
| Q3 | Available ingredients and meal/macro data on plan items | US-07, US-10, US-15 | OPEN |
| Q4 | Entities missing from Fig. B.11 that Must stories need | US-08, US-09, US-15, UC-01 | OPEN |
| Q5 | Food exclusions: permanent (profile) or per day (constraint) | US-03 migration | OPEN |
| Q6 | LEG-4 "access/export" vs export story US-36 being Could | US-04 scope | OPEN |
| Q7 | LEG-2 explicit, recorded, versioned health-data consent | `identity` design | OPEN |
| Q8 | GDPR Art. 8 — minimum age of users | US-01 | OPEN |
| Q9 | Module boundaries vs Fig. B.4 / Fig. B.12 | package structure | OPEN |
| Q10 | Content of the day summary (US-20) vs history browsing (US-21, Should) | US-20 API | OPEN |
| Q11 | PostgreSQL 17 (project) vs reference [23] (PostgreSQL 18 docs) | none (documentation) | OPEN |

---

## Q1. Recovery proposal creation: 201 or 200?

- **Conflict:** Table 2.8 — `POST /api/v1/plans/{planId}/recovery-proposals` returns
  **201** (proposalId, baseVersion, changed items, totals, reasons). Fig. B.4 step 13 —
  "Return proposal + reasons (**200**)".
- **Options:** (a) 201 Created — Table 2.8 is the API contract and a proposal is a new resource;
  (b) 200 OK as in the sequence diagram.
- **Recommendation:** (a).
- **Decision:** — **Date:** —

## Q2. Storage of plan versions and history

- **Gap:** Fig. B.11 gives `DailyPlan` only an integer `version`; there is no entity for earlier
  versions. Yet UC-02 postcondition says "history preserves the earlier version", NFR-09 requires
  "decision, new version and history are atomic", §2.3 keeps "stored plan/decision versions" in MVP
  scope, and CLAUDE.md says history is never rewritten. (Browsing history, US-21, is now Should, but
  storing it is still required.)
- **Options:**
  (a) one `daily_plan` row per version (`version`, `is_active`), items copied per version;
  (b) a `plan_version` snapshot table (e.g. JSON of items) next to the mutable active plan;
  (c) versioned `plan_item` rows with `valid_from_version` / `valid_to_version`.
- **Recommendation:** decide before the first `planning` migration; (a) is the simplest to make
  transactional and to test for 409.
- **Decision:** — **Date:** —

## Q3. Available ingredients; meal reference and macros on plan items

- **Gap:** US-15, US-10, G3 and persona Max rely on *available ingredients/products*, but
  `PlanningConstraint` has only `excluded_foods`. `PlanItem` stores `calories / duration` only — no
  meal/exercise reference and no macros, while US-07 compares macros against targets and ingredient
  infeasibility cannot be detected without knowing the meal.
- **Options:** (a) extend the model: `available_ingredients` on the constraint, meal/exercise
  reference and macros on plan items (traced to US-03/07/10/15); (b) narrow the interpretation
  (e.g. "ingredient unavailable" is user-flagged per item) and record it here.
- **Decision:** — **Date:** —

## Q4. Entities required by Must stories but absent from Fig. B.11

- Saved / recent meal templates — US-08 (≤3 actions, template not altered).
- Local food and exercise candidate catalogue — US-09, US-12, UC-01 alt. C ("local candidates"),
  NFR-02 (bounded set ≤100 + ≤100), R1 fallback.
- `recoveryRequired` flag and the deviation trigger that set it — US-15 (and US-21 "triggers").
- **Proposal:** treat these as part of the Must stories that need them (not new features) and
  extend the data model when those stories are implemented.
- **Decision:** — **Date:** —

## Q5. Food exclusions: permanent or per day?

- **Ambiguity:** `PlanningConstraint` is keyed by `date` and holds `excluded_foods`;
  `Profile.preferences` also exists. US-03 says "food exclusions, equipment and available time".
- **Options:** (a) permanent exclusions/preferences on the profile, daily time/equipment on
  `PlanningConstraint`; (b) everything per day, as in Fig. B.11.
- **Decision:** — **Date:** —

## Q6. LEG-4 access/export vs US-36 (Could)

- **Conflict:** corrected LEG-4 requires "access, correction, export and deletion" (erasure ≤30 days,
  backups ≤90 days). The only export story, US-36 (CSV), is Could; no Must story covers export.
- **Options:** (a) "access" = existing authenticated GET endpoints; export deferred with a note;
  (b) minimal JSON export of the user's records as part of US-04 / LEG-4;
  (c) BA authors amend LEG-4 or US-36 priority.
- **Decision:** — **Date:** —

## Q7. LEG-2 consent records

- **Gap:** LEG-2 — "where health-data consent is relied upon, obtain explicit, separately recorded
  and versioned consent"; §2.8 proposes Art. 9(2)(a) explicit consent. No story or entity covers it.
- **Options:** (a) a consent record (user, notice/consent version, timestamp) in `identity`, traced
  to LEG-2, captured at registration; (b) document that the MVP shows a privacy notice only (weak,
  since Art. 9 applies to health data).
- **Decision:** — **Date:** —

## Q8. Minimum user age (GDPR Art. 8)

- **Gap:** corrected §2.8 mentions national consent ages (13–16) and parental authorisation for
  children; no story defines an age rule.
- **Options:** (a) age self-declaration (≥16) at registration; (b) documented assumption
  "adults only" in terms/privacy notice, no field stored (LEG-1 data minimisation).
- **Decision:** — **Date:** —

## Q9. Module boundaries vs Fig. B.4 / Fig. B.12

- Fig. B.4 sends actual events to `RecoveryController`; in CLAUDE.md actual events are logged by
  `nutrition` (US-06) and `activity` (US-13), and `recovery` reacts to them.
- Fig. B.12 shows "Auth & Profile" as one box and has no separate `planning` or `shared` module;
  CLAUDE.md splits `identity`/`profile` and adds `planning` (daily plan, items, versioning) and
  `shared`.
- **Proposal:** keep the CLAUDE.md split (it keeps plan versioning in one owner and recovery
  separate, as the BA requires) and treat Fig. B.4's controller as the logical entry point.
- **Decision:** — **Date:** —

## Q10. Day summary content (US-20)

- **Ambiguity:** Table 2.8 — `GET /api/v1/days/{date}/summary` returns "plan, nutrition/activity
  totals, **history summary**"; US-20 acceptance criteria list only planned/actual nutrition,
  remaining totals, activity and the **active plan version**. History browsing (US-21) is Should.
- **Options:** (a) follow US-20: active plan version only; (b) include a minimal "number of versions /
  last decision" field.
- **Recommendation:** (a).
- **Decision:** — **Date:** —

## Q11. PostgreSQL version

- Project uses PostgreSQL 17 (Compose, Testcontainers). BA reference [23] cites the PostgreSQL 18
  documentation; Table 2.4 does not pin a version.
- **Options:** (a) keep 17, BA authors adjust the citation; (b) move the project to 18.
- **Decision:** — **Date:** —

---

## Known implementation gaps (not contradictions — planned work)

| Requirement | Current state |
|---|---|
| US-33 / NFR-16: CI runs coverage checks and an e2e recovery flow | CI runs build + tests only |
| US-32 / NFR-17: one Compose startup launches **API** and PostgreSQL | Compose starts PostgreSQL only |
| NFR-14: RFC 9457 Problem Details with stable `code` | Spring default error JSON |
| NFR-18: request IDs in logs/responses | not present |
| US-01 / US-30 / NFR-08: authentication, ownership checks | no Spring Security yet |
| US-29 / NFR-15: OpenAPI contract and typed TS client | no OpenAPI yet; location of the shared spec to be agreed |
