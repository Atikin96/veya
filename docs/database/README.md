# VEYA database (PostgreSQL 17, OLTP)

Design and operating documentation of the VEYA relational database. Requirements come from the BA
(`docs/BA_Malafei_Mikita.docx`) and the agreed interpretations in [`../ba-open-questions.md`](../ba-open-questions.md).

| Document / file | Content |
|---|---|
| [er-diagram.md](er-diagram.md) | Logical ER diagram (Mermaid) and mapping to BA Fig. B.11 |
| [data-dictionary.md](data-dictionary.md) | Every table, column, type, key, constraint, index, view, routine and trigger (generated from the catalog) |
| `backend/src/main/resources/db/migration/V1..V10` | Flyway migrations - the only way the schema changes |
| `infra/postgres/initdb/01-create-roles.sh` | Cluster roles (login/group), database-level privileges |
| `infra/postgres/seed/demo_data.sql` | Reproducible synthetic demo/test data (normal and edge cases) |
| `infra/postgres/scripts/verify_database.sql` | Read-only verification report |
| `infra/postgres/scripts/data_dictionary.sql` | Generator of `data-dictionary.md` |
| `backend/src/test/java/com/veya/db/*` | Integration tests against a real PostgreSQL 17 container |

## Assessment map (OLTP rubric)

| Rubric item | Where |
|---|---|
| **Minimum** — data dictionary (tables, columns, types, keys, data-quality expectations; layers) | [data-dictionary.md](data-dictionary.md) ("Layers", "Data quality expectations", one section per table) |
| Integrity and transaction handling described | §4, §5 |
| Logical schema with main blocks (colour-coded) / ER diagram | [er-diagram.md](er-diagram.md) §0 (blocks), §1–§2 (ER) |
| DDL script | Flyway migrations `V1..V10` ([folder](../../backend/src/main/resources/db/migration)) |
| Modern RDBMS; 3NF; enough tables and relationship types | PostgreSQL 17; §2; §1 (30 tables; 1:1, 1:0..1, 1:N, M:N) |
| PK/FK, NOT NULL/UNIQUE/CHECK, meaningful types for every table | §3, §4, data dictionary |
| Structure created by migrations, kept in Git | §10 |
| Test data incl. edge cases; reference-data scripts | `infra/postgres/seed/demo_data.sql`; `V10__reference_data.sql` |
| Roles app_read / app_write / admin; app not superuser; permissions for new objects | §9 (mapping, default privileges, `newObjectsCannotBeForgotten`) |
| Passwords only as modern hashes (no MD5/SHA-1); none in plain text | `app_user_password_hash_format` (§12), tests in `ConstraintAndRelationshipTests` |
| No JSON documents without a model | §2 (no JSON columns) |
| **Maximum** — several DBMS types / data layers "when needed" | not needed for the MVP: one OLTP workload, no ingestion/analytics (data dictionary "Layers") |
| Versioning of the structure | §10 |
| Indexes with justification | §7 (with `EXPLAIN ANALYZE`) |
| Triggers, procedures, functions with business logic | §4, §6 |
| Views | §8 |
| Masking / anonymisation of personal data | §9 |

## 1. Scope

The schema covers the data needed by the 27 Must stories: identity and consent (US-01, US-04, LEG-2),
profile and constraints (US-02, US-03), catalogue and diary (US-05–US-10), workouts (US-12–US-14),
versioned daily plans and Dynamic Plan Recovery (US-15–US-19), dashboard (US-20) and the LEG-4 export (Q6).
Post-MVP stories (photo recognition US-27/28, favourites US-34, history browsing US-21, analytics US-22,
CSV export US-36, calendar US-37, theme US-38, exercise filters US-11/US-35) have **no** tables.

Schema `veya`: 30 tables, 7 views, 24 routines (23 functions + 1 procedure), 26 triggers, 27 explicitly
created indexes (70 in total including the indexes behind PRIMARY KEY/UNIQUE constraints).

Relationship types: one-to-one (`app_user`–`user_profile`), one-to-zero-or-one (`recovery_proposal`–
`recovery_decision`), one-to-many (e.g. `daily_plan`–`plan_version`–`plan_item`) and many-to-many through
junction tables (`meal_component`, `food_item_ingredient`, `exercise_equipment`, `profile_food_exclusion`,
`daily_constraint_ingredient`, `daily_constraint_equipment`).

| Area | Tables |
|---|---|
| Reference | `goal_type`, `meal_slot`, `equipment`, `ingredient`, `consent_document`, `recovery_setting` |
| Catalogue | `food_item`, `food_item_ingredient`, `meal`, `meal_component`, `exercise`, `exercise_equipment` |
| Identity / profile | `app_user`, `user_consent`, `user_profile`, `profile_food_exclusion` |
| Constraints | `daily_constraint`, `daily_constraint_ingredient`, `daily_constraint_equipment` |
| Planning | `daily_plan`, `plan_version`, `plan_item` |
| Actual records | `food_entry`, `activity_entry`, `saved_meal`, `saved_meal_item` |
| Recovery | `deviation_event`, `recovery_proposal`, `recovery_change`, `recovery_decision` |

## 2. Normalization (3NF)

**1NF.** All attributes are atomic. Multi-valued attributes of Fig. B.11 (`PlanningConstraint.equipment`,
`excluded_foods`, `Profile.preferences`) are relations: `daily_constraint_equipment`,
`daily_constraint_ingredient`, `profile_food_exclusion`. No arrays or JSON columns are stored
(JSON is only used as a function parameter and in the export result).

**2NF.** Every table with a composite key (`meal_component`, `food_item_ingredient`, `exercise_equipment`,
`profile_food_exclusion`, `daily_constraint_*`, `saved_meal_item`) has only attributes that depend on the
whole key (e.g. `meal_component.grams` depends on meal *and* food item). All other tables have a single-column
key, so partial dependencies cannot occur.

**3NF.** No non-key attribute depends on another non-key attribute:

* Lookup names live in reference tables (`meal_slot.name`, `goal_type.name`, …), referenced by code.
* Meal nutrition is **not stored** on `meal`; it is derived from `meal_component × food_item` in
  `v_meal_nutrition`.
* Proposal status is **not stored**; it is derived from `recovery_decision` and the active plan version in
  `v_recovery_proposal_overview` (Fig. B.11 had `RecoveryProposal.status`).
* Fig. B.11 `RecoveryChange.old_value` is not copied - it is the referenced `target_item_id` row.

Deliberate, documented decisions that look like redundancy but are not 3NF violations:

| Column | Why it is stored |
|---|---|
| `food_entry.kcal/protein_g/...`, `saved_meal_item.*` | The values the user **confirmed** (US-06 "persisted unchanged"). They are facts of the entry, not a function of `food_item_id` - the user may correct them and the catalogue may change. |
| `plan_item.planned_kcal/...` | Snapshot of the plan at the time the immutable version was created (Q2); history must not change when the catalogue changes. |
| `daily_plan.current_version_no` | Equals `max(plan_version.version_no)` - a controlled redundancy used as the optimistic-locking value (baseVersion, NFR-10) and row-lock target. Maintained only by triggers (`plan_version_after_insert`) and protected by `daily_plan_guard`. |
| `daily_plan.recovery_required` | Fig. B.11 `recoveryRequired` flag (US-15). It is set by `deviation_event` triggers and cleared only by an accepted/edited recovery - not derivable from open events alone, because REJECT keeps the flag set. |

## 3. Keys and data types

* `uuid` (`gen_random_uuid()`) for user-owned records: not guessable in URLs (NFR-08, LEG-3), safe to generate
  in the application.
* `bigint`/`smallint GENERATED ALWAYS AS IDENTITY` for catalogue rows; natural `varchar` code keys for small
  reference vocabularies (stable, readable FKs such as `meal_slot_code = 'LUNCH'`).
* `numeric(p,s)` for nutrition (exact decimal arithmetic for sums and the 200 kcal threshold), `timestamptz`
  for instants, `date`/`time` for local plan dates and times, `varchar(n)` with `CHECK` for enumerations
  fixed by the BA (statuses, decisions, change types).

## 4. Data integrity

Declarative (see the data dictionary for every constraint):

* **PRIMARY KEY** on every table; **FOREIGN KEY** on every reference with an explicit delete rule:
  `CASCADE` from `app_user` to all personal data (US-04 erasure), `RESTRICT`/`NO ACTION` from catalogue rows
  still in use, `SET NULL` where actual records must survive (`food_entry.plan_item_id`, `saved_meal_id`).
* **UNIQUE**: `lower(email)`, one plan / one constraint row per user and date, gap-free
  `(daily_plan_id, version_no)`, one decision per proposal, one active consent per document, one open deviation
  per item and type (partial unique indexes).
* **NOT NULL** on all mandatory attributes; **CHECK** for ranges (portion, kcal, MET, minutes), formats
  (e-mail, codes, versions), enumerations and row shapes (a MEAL item has a meal and kcal, a WORKOUT item an
  exercise and a duration; SKIPPED activity has no duration; `KCAL_DIFFERENCE` has a difference, …).
  CHECKs are written so that `NULL` cannot slip through (`x IS NOT NULL AND x BETWEEN ...`).

Procedural (triggers, `V7__business_logic.sql`), used only where a declarative constraint cannot express the rule:

| Rule | Trigger / routine | Source |
|---|---|---|
| Plan versions, proposals, changes, decisions, consent documents are append-only | `reject_update` | Q2, US-18, LEG-2 |
| Plan item content immutable; status recorded once and only in the active version | `plan_item_guard` | Q2, US-13 |
| Version numbers gap-free (n+1), active pointer moves only to an existing next version | `plan_version_before_insert`, `plan_version_after_insert`, `daily_plan_guard` | NFR-09/10 |
| Diary entries may link only to the active version's items of the same user and date | `food_entry_guard`, `activity_entry_guard` | LEG-3, US-06, US-13 |
| Completed items are frozen; changes target PLANNED items of the base version | `recovery_change_before_insert`, `create_plan_version` | US-16 |
| Proposal requires `recoveryRequired` and the current version | `recovery_proposal_before_insert` | US-16, US-18 |
| Consent can be withdrawn once; deviation resolved once | `user_consent_guard`, `deviation_event_guard` | LEG-2, US-15 |
| `updated_at` maintenance | `set_updated_at` | — |

Application error codes raised by the database (`SQLSTATE`), mapped to HTTP by the API (NFR-14):
`VY400` → 400 invalid request, `VY404` → 404 not found / not owned (ownership is not disclosed),
`VY409` → 409 stale baseVersion or already decided, `VY001` → 409 attempt to modify immutable history.

## 5. Transactions and concurrency

PostgreSQL default isolation **READ COMMITTED** plus explicit row locks:

* `create_plan_version(user, date, expectedVersion, reason, items)` - one statement, therefore one atomic unit:
  locks the `daily_plan` row (`SELECT … FOR UPDATE`), compares `expectedVersion` with
  `current_version_no` (VY409 on mismatch), inserts version n+1, copies recorded items unchanged (frozen) and
  inserts the remaining items. Any error (CHECK, FK, VY4xx) rolls back everything.
* `CALL apply_recovery_decision(user, proposal, decision, baseVersion, selectedChanges)` - US-18 / NFR-09:
  locks the plan row, checks ownership (VY404), "already decided" and `baseVersion` = proposal base = active
  version (VY409), then **in the caller's transaction** inserts the decision and, for ACCEPT/EDIT, the new
  version, resolves deviation events and clears `recovery_required`. REJECT records only the decision.
* Two concurrent decisions on the same plan are serialised by the row lock; after the first commits, the
  second re-reads the locked row (READ COMMITTED re-check), sees the new version and fails with VY409.
  Verified by `RecoveryDecisionTests.concurrentDecisions` (two connections, one holds the lock).
* NFR-09 is verified by `RecoveryDecisionTests.injectedFailureLeavesNoPartialPlan`: a test trigger makes the
  decision insert fail **after** the version insert; the test asserts that no version, no decision and no
  flag change remain.
* The procedure contains no `COMMIT`/`ROLLBACK`; the application (Spring `@Transactional`) owns the
  transaction boundary.

## 6. Database-side logic and its justification

| Routine | Kind | Why in the database |
|---|---|---|
| `create_plan_version` | function, SECURITY DEFINER | Single entry point that keeps versions immutable and gap-free; the app role has no INSERT on `plan_version`/`plan_item`. |
| `apply_recovery_decision` | procedure, SECURITY DEFINER | Atomic decision + version (NFR-09) and stale check under a row lock (NFR-10) in one place. |
| `is_significant_kcal_deviation`, `evaluate_meal_deviation` | functions | US-15 rules 1–2 are evaluated atomically with the write that confirms a meal or records a skip, so `recoveryRequired` can never be out of date. |
| `export_user_data` | function | LEG-4 / Q6 consistent JSON snapshot of one user's data in one statement. |
| `mask_email`, `pseudonym` | functions | Masking/anonymisation for read-only reporting (§9). |
| Guard triggers | triggers | Integrity rules spanning rows/tables (§4). |

Not in the database (application, deterministic, BA §2.4): candidate generation, hard-constraint filtering,
scoring and ranking of recovery proposals, and US-15 rule 3 (detecting that changed time/ingredients/equipment
make a future item infeasible) - the app records the result as a `CONSTRAINT_INFEASIBLE` deviation event.

## 7. Indexes

PostgreSQL creates indexes for PRIMARY KEY/UNIQUE constraints only; all other indexes are explicit and tied
to a query or a foreign key (a test asserts that every FK to user data has a supporting index).

| Index | Serves | Story |
|---|---|---|
| `app_user_email_uq` (unique, `lower(email)`) | sign-in lookup, case-insensitive uniqueness | US-01 |
| `food_item_name_trgm_idx` (GIN, `pg_trgm`) | `lower(name) LIKE '%…%'` food search | US-05 |
| `food_entry_user_date_idx` (`user_id, entry_date`) | daily totals and dashboard | US-07, US-20 |
| `food_entry_user_recent_idx` (`user_id, created_at DESC`) | recent meals | US-08 |
| `food_entry_plan_item_idx` (partial) | sum of a planned meal's entries (deviation check) | US-15 |
| `activity_entry_user_date_idx` | dashboard workout minutes | US-20 |
| `daily_plan_user_date_uq` (unique) | plan of a user and date, version lock | US-09, US-18 |
| `daily_plan_recovery_required_idx` (partial) | plans waiting for recovery | US-15 |
| `plan_version_plan_no_uq`, `plan_item_version_key_uq` | active version and its items | US-20 |
| `deviation_event_open_item_uq` (partial unique) | no duplicate open deviation per item/type | US-15 |
| `deviation_event_plan_idx`, `recovery_proposal_plan_idx`, `recovery_change_no_uq` | FK lookups, history order | US-16–US-18 |
| catalogue FK indexes (`meal_component_food_item_idx`, `food_item_ingredient_ingredient_idx`, …) | exclusion filtering, FK checks | US-03, US-10 |

Evidence - `EXPLAIN (ANALYZE)` on the Compose database with temporary bulk data
(2,000 users, 200,000 diary entries, 50,000 provider foods; transaction rolled back):

```text
US-07  SELECT sum(kcal) FROM food_entry WHERE user_id = … AND entry_date = '2026-03-01'
       -> Index Scan using food_entry_user_date_idx on food_entry
US-08  SELECT food_name FROM food_entry WHERE user_id = … ORDER BY created_at DESC LIMIT 10
       -> Index Scan using food_entry_user_recent_idx on food_entry          (no sort node)
US-05  SELECT id, name FROM food_item WHERE lower(name) LIKE '%chicken%'
       -> Bitmap Index Scan on food_item_name_trgm_idx
US-01  SELECT id FROM app_user WHERE lower(email) = lower('Bulk77@example.org')
       -> Index Scan using app_user_email_uq on app_user
```

`SchemaStructureTests.foodSearchUsesTrigramIndex` re-checks the trigram index on every build.

## 8. Views

| View | Purpose | Granted to |
|---|---|---|
| `v_meal_nutrition` | derived meal nutrition + ingredient codes | app, readonly |
| `v_active_plan_item` | items of the active version of each plan | app |
| `v_daily_nutrition_summary` | consumed / target / remaining per user and day (US-07) | app |
| `v_day_summary` | US-20 dashboard (Q10: active version only) | app |
| `v_recovery_proposal_overview` | derived proposal status PENDING/STALE/ACCEPTED/EDITED/REJECTED | app |
| `v_app_user_masked` | masked user list (no id, e-mail, hash) | readonly |
| `v_analytics_daily_nutrition` | pseudonymised daily totals | readonly |

## 9. Roles, privileges and masking

Roles are created once per cluster by `infra/postgres/initdb/01-create-roles.sh` (passwords only from
environment variables); object privileges are granted by Flyway `V9`. **No role is a superuser**; the
bootstrap superuser `postgres` is used only by the init script.

| Role | Login | Purpose | Privileges |
|---|---|---|---|
| `veya_owner` | yes | Flyway migrations | owns schema `veya` and all objects; `CONNECT, CREATE` on the database; no CREATEROLE/CREATEDB |
| `veya_app` ← `veya_app_user` | via member | Spring Boot datasource | SELECT reference data; INSERT/UPDATE/DELETE on user records; `UPDATE (status)` on `plan_item`; INSERT-only on proposals/changes/deviations; **no** INSERT/UPDATE/DELETE on `plan_version`, `recovery_decision`, no DDL, no access to `flyway_schema_history`; EXECUTE on `create_plan_version`, `apply_recovery_decision`, `export_user_data` |
| `veya_readonly` ← `veya_readonly_user` | via member | reporting / support | SELECT on reference/catalogue tables and on the masked/anonymised views only; sessions are `default_transaction_read_only`, 30 s statement timeout |
| `veya_admin` ← `veya_admin_user` | via member | administration, seeding | full DML on all tables/views, EXECUTE on VEYA routines; no DDL; cannot modify `flyway_schema_history`; integrity triggers still apply |

Mapping to the assessment's minimum role set: **app_read** = `veya_readonly`, **app_write** = `veya_app`,
**admin** = `veya_admin` (plus `veya_owner` for schema migrations only). New objects: `veya_admin` receives DML
automatically (default privileges); every other grant is explicit in the migration, and
`RolePrivilegeTests.newObjectsCannotBeForgotten` fails if a table/view is not readable by the app/admin roles
or if the read-only role can see anything outside its allow-list.

Database-level: `CONNECT` revoked from `PUBLIC`; schema `public` closed (`REVOKE ALL … FROM PUBLIC`).
SECURITY DEFINER routines have a fixed `search_path = veya, pg_temp` and `EXECUTE` revoked from `PUBLIC`.

**Masking / anonymisation.** Read-only users never get the base tables with personal data. They read
`v_app_user_masked` (pseudonym `U-XXXXXXXXXXXX`, e-mail masked as `a***@e***.org`, registration month instead
of timestamp, no id or password hash) and `v_analytics_daily_nutrition` (pseudonym instead of user id). The
views run with the owner's privileges, so masking cannot be bypassed by the read-only role. `pseudonym()` is a
one-way SHA-256 of the random user UUID, which the read-only role cannot see. Demo/test data is synthetic
(`example.org` addresses; password hashes are real bcrypt hashes of random, discarded secrets, so no demo
account has a usable password - LEG-8).

## 10. Flyway versioning

* Location `classpath:db/migration`, schema `veya` (Flyway `default-schema`/`schemas`), history table
  `veya.flyway_schema_history`; migrations run as `veya_owner` (`spring.flyway.user`), the API as
  `veya_app_user` (`spring.datasource.username`). Hibernate runs with `ddl-auto: validate`.
* One migration per concern, in dependency order:

| Version | Content |
|---|---|
| V1 | baseline (kept unchanged) |
| V2 | `pg_trgm`, reference vocabularies, catalogue |
| V3 | accounts, consent, profile, daily constraints |
| V4 | daily plan, versions, items |
| V5 | diary, saved meals, activity records |
| V6 | deviation events, proposals, changes, decisions |
| V7 | functions, procedure, triggers |
| V8 | views |
| V9 | object privileges |
| V10 | reference and catalogue data |

* Rules: never edit a migration after it has been applied to a shared database or pushed; add `V11__…` instead.
  Every new table/column gets a `COMMENT`, and the data dictionary is regenerated
  (`SchemaStructureTests` fails otherwise). New objects need explicit grants in the same
  migration (`veya_admin` gets DML automatically through default privileges).
* Demo data is **not** a migration (it must never reach production); it is loaded explicitly with `psql`.

## 11. Verification

| What | How | Result (2026-11-14) |
|---|---|---|
| Clean environment | `docker compose … down -v && … up -d`, start API (Flyway as `veya_owner`), load seed as `veya_admin_user` | 10 migrations applied, API `UP` as `veya_app_user`, seed `COMMIT` |
| Verification report | `verify_database.sql` | V1–V10 success, 7 roles none superuser, privilege matrix as in §9 |
| Automated tests | `cd backend && ./mvnw verify` (Testcontainers PostgreSQL 17, same init script) | 146 tests, 0 failures |

Test classes (`backend/src/test/java/com/veya/db`): `SchemaStructureTests` (creation, PKs, FK indexes,
comments, dictionary, reference data, trigram index), `ConstraintAndRelationshipTests` (37 invalid-row cases incl. plain-text/MD5/SHA-1 passwords,
CHECK shapes, cascade erasure, SET NULL), `RolePrivilegeTests` (role attributes, ownership, 19 denied
operations for the app role, read-only and admin limits), `PlanVersioningTests`, `DeviationDetectionTests`
(200 kcal boundary in both directions, skipped/missed items), `RecoveryDecisionTests` (ACCEPT/EDIT/REJECT,
400/404/409, injected-failure rollback, concurrent decisions), `ExportAndMaskingTests`,
`CleanEnvironmentSeedTests` (fresh container: init script → Flyway → psql seed → scenario checks).

## 12. Limitations and items not verified

* **Row-level security** is not enabled: per-user isolation of the application role is enforced by the API
  (LEG-3, US-30) plus ownership checks in the routines and triggers. RLS would need a per-request user
  context and is not required by the BA.
* **LEG-4 retention timing** (erasure within 30 days, backup expiry within 90 days) is an operational
  policy; the database provides immediate erasure (`ON DELETE CASCADE`) and export, but backups/retention
  are not part of this repository.
* **LEG-5** HTTPS, password hashing and token handling are application/deployment concerns; the database only
  only accepts an adaptive hash: `app_user_password_hash_format` admits bcrypt (cost 04-31) or Argon2id and
  rejects plain text, MD5, SHA-1 and unsalted SHA-256 (tested).
* **NFR-01/NFR-02 performance targets** (p95 ≤ 500 ms / ≤ 2 s) are not measured here; only index usage was
  verified with `EXPLAIN ANALYZE` on bulk data.
* No password or key is stored in the repository: `infra/generate-env.sh` writes random passwords to the
  git-ignored `infra/.env`; tests generate random passwords per run.
* **Open: Q12 (meaning of EDIT).** The database implements EDIT as applying a selected subset of the proposal's
  changes; the BA (Fig. B.10) also shows editing individual proposed items. See `ba-open-questions.md`.
* Q13 is agreed: registration requires an explicit "at least 18" confirmation stored as `adult_confirmed_at`.
