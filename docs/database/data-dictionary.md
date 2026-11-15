# VEYA data dictionary

> Generated from the PostgreSQL 17 catalog of schema `veya` by `infra/postgres/scripts/data_dictionary.sql`.
> Descriptions are the `COMMENT ON` texts from the Flyway migrations. Do not edit by hand - regenerate.

Schema version: **V10** (Flyway). Tables: **30**, views: **7**, routines: **24**, triggers: **26**.

Conventions: `uuid` keys for user-owned records (not guessable), identity `bigint`/`smallint` keys for catalogue
rows, natural code keys for reference vocabularies; `timestamptz` for instants, `date`/`time` for local plan
dates and times; `numeric` for nutrition values (exact decimal arithmetic).

**Layers.** One normalized OLTP layer (schema `veya`). There is no raw/staging/data-mart pipeline: VEYA has
no bulk ingestion or analytics workload in the MVP, so extra layers are not justified. Read models are views
(application views; masked/anonymised reporting views for `veya_readonly`). JSON is not stored in tables -
it is only a parameter format (`create_plan_version`) and the LEG-4 export result.

## Data quality expectations

| Dimension | Expectation | How it is enforced |
|---|---|---|
| Completeness | Mandatory attributes are always present (e-mail, adulthood confirmation, goal and targets, entry date, portion and confirmed nutrients, item shape by type, change reason). | `NOT NULL`; shape `CHECK`s (`plan_item_meal_shape`, `plan_item_workout_shape`, `recovery_change_*_shape`, `activity_entry_duration_by_status`). |
| Validity | Values lie in physically meaningful ranges and formats: kcal 0-10000 per entry, 0-900 per 100 g; macros <= 100 g per 100 g; portion 0-5000 g; duration 1-600 min; available time 0-1440 min; MET 1-25; e-mail format; adaptive password hash only; codes `^[A-Z][A-Z0-9_]+$`; statuses from fixed BA lists. | `CHECK` constraints; `CHECK`s are written so that `NULL` cannot pass. |
| Uniqueness | One account per e-mail (case-insensitive), one profile per user, one plan and one constraint set per user and date, gap-free version numbers, one decision per proposal, one active consent per document, no duplicate open deviation per item. | `PRIMARY KEY`, `UNIQUE`, partial unique indexes. |
| Referential integrity | Every reference points to an existing row; personal data is erased with the account; catalogue rows in use cannot be deleted; actual records survive plan erasure (unlinked). | `FOREIGN KEY` with explicit `CASCADE` / `RESTRICT` / `SET NULL`. |
| Consistency | Diary entries refer only to active-version items of the same user and date; plan versions are immutable and sequential; completed items are frozen; `recoveryRequired` always reflects significant deviations (US-15). | Triggers and routines (V7), see "Functions and procedures" and "Triggers". |
| Accuracy / provenance | Only user-confirmed values are stored as actual records (US-06); catalogue and MET values are estimates (LEG-6) with their `source` (LOCAL/PROVIDER) and `fetched_at`; planned values are snapshots of their version. | `input_source`, `source`, `external_ref`, snapshot columns. |
| Timeliness / audit | Creation and last-change instants are recorded; decisions, versions and deviations keep their timestamps. | `created_at`, `updated_at` (trigger), `decided_at`, `detected_at`/`resolved_at`. |
| Privacy | Direct identifiers (`app_user.email`, `password_hash`) are visible only to the application and admin roles; reporting uses masked/pseudonymised views. | Privileges (V9), `v_app_user_masked`, `v_analytics_daily_nutrition`. |

## Tables

- [`activity_entry`](#activity_entry) — Actual workout event (Fig. B.11 ActivityEntry; US-13). Recording it for a planned item sets that item's status.
- [`app_user`](#app_user) — User account (US-01). Deleting a row erases all personal records through ON DELETE CASCADE (US-04, LEG-4).
- [`consent_document`](#consent_document) — Versioned privacy notice / health-data consent texts that users accept (LEG-2, Q7). Rows are immutable once published.
- [`daily_constraint`](#daily_constraint) — Planning constraints for one user and date (Fig. B.11 PlanningConstraint; US-03, US-15, Q5).
- [`daily_constraint_equipment`](#daily_constraint_equipment) — Equipment available on that day (US-03, US-14). No rows = no equipment available.
- [`daily_constraint_ingredient`](#daily_constraint_ingredient) — Per-day ingredient availability: available products (Q3) and temporary exclusions (Q5).
- [`daily_plan`](#daily_plan) — Plan of one user for one date (Fig. B.11 DailyPlan). Content is versioned in plan_version.
- [`deviation_event`](#deviation_event) — Significant deviation that set recoveryRequired (US-15): kcal difference >= threshold, skipped/missed item, or a constraint making a future item infeasible.
- [`equipment`](#equipment) — Reference: training equipment that exercises may require and users may have available (US-03, US-14).
- [`exercise`](#exercise) — Exercise catalogue entry for workout planning and replacement (US-12, US-14). MET values are estimates (LEG-6).
- [`exercise_equipment`](#exercise_equipment) — Equipment required by an exercise; an exercise without rows needs no equipment (US-14).
- [`food_entry`](#food_entry) — Confirmed food diary entry (Fig. B.11 FoodEntry; US-06). Only user-confirmed values are stored.
- [`food_item`](#food_item) — Normalized food catalogue entry with nutrition per 100 g (US-05). Values are estimates (LEG-6).
- [`food_item_ingredient`](#food_item_ingredient) — Which ingredients a food item contains; used to apply exclusions and availability (US-03, US-10).
- [`goal_type`](#goal_type) — Reference: personal goal categories a profile can select (US-02).
- [`ingredient`](#ingredient) — Reference: ingredients / product groups used for food exclusions and ingredient availability (US-03, US-10, Q3, Q5).
- [`meal`](#meal) — Candidate meal (recipe) for meal planning and replacement (US-09, US-10). Nutrition is derived from its components (view v_meal_nutrition).
- [`meal_component`](#meal_component) — Food items and amounts that make up a candidate meal.
- [`meal_slot`](#meal_slot) — Reference: meal slots of a day (breakfast, lunch, ...) used by plans and the diary (US-06, US-09).
- [`plan_item`](#plan_item) — Planned meal or workout inside one plan version (Fig. B.11 PlanItem). Content is immutable; only status of items in the active version can be recorded.
- [`plan_version`](#plan_version) — Immutable version of a daily plan (Q2). Created only through create_plan_version / apply_recovery_decision.
- [`profile_food_exclusion`](#profile_food_exclusion) — Permanent food exclusions stored in the profile; hard constraints for planning and recovery (US-03, Q5).
- [`recovery_change`](#recovery_change) — One changed item of a proposal with its human-readable reason (Fig. B.11 RecoveryChange; US-17).
- [`recovery_decision`](#recovery_decision) — User decision on a proposal (US-18), at most one per proposal. Written only by apply_recovery_decision.
- [`recovery_proposal`](#recovery_proposal) — Recovery proposal for the remaining day (US-16). Immutable; status derived in v_recovery_proposal_overview.
- [`recovery_setting`](#recovery_setting) — Single-row configuration of deviation detection (US-15: configurable threshold, default 200 kcal).
- [`saved_meal`](#saved_meal) — User-defined meal template for quick re-logging (US-08). Reusing it never alters the template.
- [`saved_meal_item`](#saved_meal_item) — Lines of a saved meal template with the confirmed values to copy into the diary.
- [`user_consent`](#user_consent) — Separately recorded, versioned consent / notice acceptance (LEG-2, Q7). Append-only except a single withdrawal.
- [`user_profile`](#user_profile) — Profile and goal (US-02), 1:1 with app_user. Targets are stored user goals, not calculated (no BMR/TDEE).

### `activity_entry`

Actual workout event (Fig. B.11 ActivityEntry; US-13). Recording it for a planned item sets that item's status.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `user_id` | uuid | NOT NULL |  | FK → `app_user` | Owner (FK app_user, cascade). |
| `entry_date` | date | NOT NULL |  |  | Date of the workout. |
| `exercise_id` | bigint | NOT NULL |  | FK → `exercise` | Exercise actually performed or planned-and-skipped (FK exercise). |
| `plan_item_id` | uuid | NULL |  | UQ, FK → `plan_item` | Planned WORKOUT item this event refers to; at most one event per planned item. Set NULL if the plan is erased. |
| `status` | character varying(10) | NOT NULL |  |  | COMPLETED, SKIPPED or CHANGED (performed differently from the plan). |
| `duration_min` | smallint | NULL |  |  | Actual duration in minutes; NULL exactly when SKIPPED. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | When the event was recorded. |
| `updated_at` | timestamp with time zone | NOT NULL | `now()` |  | Last correction time (maintained by trigger). |

**Constraints**

- `activity_entry_plan_item_uq` (unique): `UNIQUE (plan_item_id)`
- `activity_entry_pk` (primary key): `PRIMARY KEY (id)`
- `activity_entry_exercise_fk` (foreign key): `FOREIGN KEY (exercise_id) REFERENCES exercise(id)`
- `activity_entry_plan_item_fk` (foreign key): `FOREIGN KEY (plan_item_id) REFERENCES plan_item(id) ON DELETE SET NULL`
- `activity_entry_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `activity_entry_duration_by_status` (check): `CHECK (((((status)::text = 'SKIPPED'::text) AND (duration_min IS NULL)) OR (((status)::text <> 'SKIPPED'::text) AND (duration_min IS NOT NULL) AND ((duration_min >= 1) AND (duration_min <= 600)))))`
- `activity_entry_status_check` (check): `CHECK (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'SKIPPED'::character varying, 'CHANGED'::character varying])::text[])))`
- `activity_entry_updated_after_created` (check): `CHECK ((updated_at >= created_at))`

**Indexes**

- `CREATE INDEX activity_entry_exercise_idx ON veya.activity_entry USING btree (exercise_id)`
- `CREATE UNIQUE INDEX activity_entry_pk ON veya.activity_entry USING btree (id)`
- `CREATE UNIQUE INDEX activity_entry_plan_item_uq ON veya.activity_entry USING btree (plan_item_id)`
- `CREATE INDEX activity_entry_user_date_idx ON veya.activity_entry USING btree (user_id, entry_date)`

### `app_user`

User account (US-01). Deleting a row erases all personal records through ON DELETE CASCADE (US-04, LEG-4).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key (random UUID; not guessable, NFR-08). |
| `email` | character varying(254) | NOT NULL |  |  | Sign-in e-mail; unique case-insensitively. Personal data - masked in v_app_user_masked. |
| `password_hash` | character varying(255) | NOT NULL |  |  | Adaptive password hash produced by the application (bcrypt or Argon2id, enforced by CHECK app_user_password_hash_format); never the password, never MD5/SHA-1 (LEG-5). |
| `adult_confirmed_at` | timestamp with time zone | NOT NULL |  |  | When the user explicitly confirmed being at least 18; required for registration (Q8, Q13). No date of birth is stored (LEG-1). |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Registration time. |
| `updated_at` | timestamp with time zone | NOT NULL | `now()` |  | Last modification time (maintained by trigger). |

**Constraints**

- `app_user_pk` (primary key): `PRIMARY KEY (id)`
- `app_user_email_format` (check): `CHECK ((((email)::text = btrim((email)::text)) AND ((email)::text ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'::text)))`
- `app_user_password_hash_format` (check): `CHECK (((password_hash)::text ~ '^(\{[A-Za-z0-9@_.]+\})?(\$2[aby]\$(0[4-9]\|[12][0-9]\|3[01])\$[./A-Za-z0-9]{53}\|\$argon2id\$v=19\$m=[0-9]+,t=[0-9]+,p=[0-9]+\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+)$'::text))`
- `app_user_updated_after_created` (check): `CHECK ((updated_at >= created_at))`

**Indexes**

- `CREATE UNIQUE INDEX app_user_email_uq ON veya.app_user USING btree (lower((email)::text))`
- `CREATE UNIQUE INDEX app_user_pk ON veya.app_user USING btree (id)`

### `consent_document`

Versioned privacy notice / health-data consent texts that users accept (LEG-2, Q7). Rows are immutable once published.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | smallint | NOT NULL | identity (always) | PK | Surrogate key. |
| `consent_type` | character varying(30) | NOT NULL |  | UQ | PRIVACY_NOTICE (acknowledgement) or HEALTH_DATA_CONSENT (explicit Art. 9(2)(a) consent). |
| `version` | character varying(20) | NOT NULL |  | UQ | Document version, format major.minor; unique per consent type. |
| `published_on` | date | NOT NULL |  |  | Date the version was published. |
| `summary` | character varying(500) | NOT NULL |  |  | Short description of the document version. |

**Constraints**

- `consent_document_type_version_uq` (unique): `UNIQUE (consent_type, version)`
- `consent_document_pk` (primary key): `PRIMARY KEY (id)`
- `consent_document_summary_not_blank` (check): `CHECK ((btrim((summary)::text) <> ''::text))`
- `consent_document_type_check` (check): `CHECK (((consent_type)::text = ANY ((ARRAY['PRIVACY_NOTICE'::character varying, 'HEALTH_DATA_CONSENT'::character varying])::text[])))`
- `consent_document_version_format` (check): `CHECK (((version)::text ~ '^[0-9]+\.[0-9]+$'::text))`

**Indexes**

- `CREATE UNIQUE INDEX consent_document_pk ON veya.consent_document USING btree (id)`
- `CREATE UNIQUE INDEX consent_document_type_version_uq ON veya.consent_document USING btree (consent_type, version)`

### `daily_constraint`

Planning constraints for one user and date (Fig. B.11 PlanningConstraint; US-03, US-15, Q5).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `user_id` | uuid | NOT NULL |  | UQ, FK → `app_user` | Owner (FK app_user, cascade). |
| `constraint_date` | date | NOT NULL |  | UQ | Date the constraints apply to; one row per user and date. |
| `available_time_min` | smallint | NULL |  |  | Minutes available for remaining activity/meal preparation that day; NULL = not restricted. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |
| `updated_at` | timestamp with time zone | NOT NULL | `now()` |  | Last modification time (maintained by trigger). |

**Constraints**

- `daily_constraint_user_date_uq` (unique): `UNIQUE (user_id, constraint_date)`
- `daily_constraint_pk` (primary key): `PRIMARY KEY (id)`
- `daily_constraint_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `daily_constraint_time_range` (check): `CHECK (((available_time_min IS NULL) OR ((available_time_min >= 0) AND (available_time_min <= 1440))))`
- `daily_constraint_updated_after_created` (check): `CHECK ((updated_at >= created_at))`

**Indexes**

- `CREATE UNIQUE INDEX daily_constraint_pk ON veya.daily_constraint USING btree (id)`
- `CREATE UNIQUE INDEX daily_constraint_user_date_uq ON veya.daily_constraint USING btree (user_id, constraint_date)`

### `daily_constraint_equipment`

Equipment available on that day (US-03, US-14). No rows = no equipment available.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `daily_constraint_id` | uuid | NOT NULL |  | PK, FK → `daily_constraint` | Daily constraint (FK daily_constraint, cascade). |
| `equipment_code` | character varying(30) | NOT NULL |  | PK, FK → `equipment` | Available equipment (FK equipment). |

**Constraints**

- `daily_constraint_equipment_pk` (primary key): `PRIMARY KEY (daily_constraint_id, equipment_code)`
- `daily_constraint_equipment_constraint_fk` (foreign key): `FOREIGN KEY (daily_constraint_id) REFERENCES daily_constraint(id) ON DELETE CASCADE`
- `daily_constraint_equipment_equipment_fk` (foreign key): `FOREIGN KEY (equipment_code) REFERENCES equipment(code)`

**Indexes**

- `CREATE UNIQUE INDEX daily_constraint_equipment_pk ON veya.daily_constraint_equipment USING btree (daily_constraint_id, equipment_code)`

### `daily_constraint_ingredient`

Per-day ingredient availability: available products (Q3) and temporary exclusions (Q5).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `daily_constraint_id` | uuid | NOT NULL |  | PK, FK → `daily_constraint` | Daily constraint (FK daily_constraint, cascade). |
| `ingredient_code` | character varying(30) | NOT NULL |  | PK, FK → `ingredient` | Ingredient (FK ingredient); at most one status per day. |
| `availability` | character varying(10) | NOT NULL |  |  | AVAILABLE = the user has it today; EXCLUDED = temporarily excluded/unavailable today. |

**Constraints**

- `daily_constraint_ingredient_pk` (primary key): `PRIMARY KEY (daily_constraint_id, ingredient_code)`
- `daily_constraint_ingredient_constraint_fk` (foreign key): `FOREIGN KEY (daily_constraint_id) REFERENCES daily_constraint(id) ON DELETE CASCADE`
- `daily_constraint_ingredient_ingredient_fk` (foreign key): `FOREIGN KEY (ingredient_code) REFERENCES ingredient(code)`
- `daily_constraint_ingredient_availability_check` (check): `CHECK (((availability)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'EXCLUDED'::character varying])::text[])))`

**Indexes**

- `CREATE UNIQUE INDEX daily_constraint_ingredient_pk ON veya.daily_constraint_ingredient USING btree (daily_constraint_id, ingredient_code)`

### `daily_plan`

Plan of one user for one date (Fig. B.11 DailyPlan). Content is versioned in plan_version.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key (planId in the API). |
| `user_id` | uuid | NOT NULL |  | UQ, FK → `app_user` | Owner (FK app_user, cascade). |
| `plan_date` | date | NOT NULL |  | UQ | Planned date; one plan per user and date. |
| `current_version_no` | integer | NOT NULL | `0` |  | Active version number (0 = no version yet). Maintained only by triggers; compared with baseVersion (US-18, NFR-10). |
| `recovery_required` | boolean | NOT NULL | `false` |  | TRUE after a significant deviation (US-15); cleared when a recovery proposal is accepted/edited. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |
| `updated_at` | timestamp with time zone | NOT NULL | `now()` |  | Last modification time (maintained by trigger). |

**Constraints**

- `daily_plan_user_date_uq` (unique): `UNIQUE (user_id, plan_date)`
- `daily_plan_pk` (primary key): `PRIMARY KEY (id)`
- `daily_plan_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `daily_plan_recovery_needs_version` (check): `CHECK (((NOT recovery_required) OR (current_version_no >= 1)))`
- `daily_plan_updated_after_created` (check): `CHECK ((updated_at >= created_at))`
- `daily_plan_version_non_negative` (check): `CHECK ((current_version_no >= 0))`

**Indexes**

- `CREATE UNIQUE INDEX daily_plan_pk ON veya.daily_plan USING btree (id)`
- `CREATE INDEX daily_plan_recovery_required_idx ON veya.daily_plan USING btree (user_id, plan_date) WHERE recovery_required`
- `CREATE UNIQUE INDEX daily_plan_user_date_uq ON veya.daily_plan USING btree (user_id, plan_date)`

### `deviation_event`

Significant deviation that set recoveryRequired (US-15): kcal difference >= threshold, skipped/missed item, or a constraint making a future item infeasible.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `daily_plan_id` | uuid | NOT NULL |  | FK → `daily_plan` | Affected plan (FK daily_plan, cascade). |
| `plan_item_id` | uuid | NULL |  | FK → `plan_item` | Item that deviated; NULL only for CONSTRAINT_INFEASIBLE events not tied to one item. |
| `deviation_type` | character varying(25) | NOT NULL |  |  | KCAL_DIFFERENCE, ITEM_SKIPPED, ITEM_MISSED or CONSTRAINT_INFEASIBLE. |
| `kcal_difference` | numeric(7,1) | NULL |  |  | Actual minus planned kcal; only for KCAL_DIFFERENCE. |
| `details` | character varying(500) | NULL |  |  | Human-readable trigger description; required for CONSTRAINT_INFEASIBLE. |
| `detected_at` | timestamp with time zone | NOT NULL | `now()` |  | When the deviation was detected. |
| `resolved_at` | timestamp with time zone | NULL |  |  | When an accepted/edited recovery resolved it; NULL while open. Set once. |

**Constraints**

- `deviation_event_pk` (primary key): `PRIMARY KEY (id)`
- `deviation_event_item_fk` (foreign key): `FOREIGN KEY (plan_item_id) REFERENCES plan_item(id) ON DELETE CASCADE`
- `deviation_event_plan_fk` (foreign key): `FOREIGN KEY (daily_plan_id) REFERENCES daily_plan(id) ON DELETE CASCADE`
- `deviation_event_details_for_constraint` (check): `CHECK ((((deviation_type)::text <> 'CONSTRAINT_INFEASIBLE'::text) OR (btrim((COALESCE(details, ''::character varying))::text) <> ''::text)))`
- `deviation_event_item_required` (check): `CHECK ((((deviation_type)::text = 'CONSTRAINT_INFEASIBLE'::text) OR (plan_item_id IS NOT NULL)))`
- `deviation_event_kcal_only_for_kcal` (check): `CHECK ((((deviation_type)::text = 'KCAL_DIFFERENCE'::text) = (kcal_difference IS NOT NULL)))`
- `deviation_event_resolved_after_detected` (check): `CHECK (((resolved_at IS NULL) OR (resolved_at >= detected_at)))`
- `deviation_event_type_check` (check): `CHECK (((deviation_type)::text = ANY ((ARRAY['KCAL_DIFFERENCE'::character varying, 'ITEM_SKIPPED'::character varying, 'ITEM_MISSED'::character varying, 'CONSTRAINT_INFEASIBLE'::character varying])::text[])))`

**Indexes**

- `CREATE UNIQUE INDEX deviation_event_open_item_uq ON veya.deviation_event USING btree (plan_item_id, deviation_type) WHERE ((resolved_at IS NULL) AND (plan_item_id IS NOT NULL))`
- `CREATE UNIQUE INDEX deviation_event_pk ON veya.deviation_event USING btree (id)`
- `CREATE INDEX deviation_event_plan_idx ON veya.deviation_event USING btree (daily_plan_id, detected_at)`
- `CREATE INDEX deviation_event_plan_item_idx ON veya.deviation_event USING btree (plan_item_id) WHERE (plan_item_id IS NOT NULL)`

### `equipment`

Reference: training equipment that exercises may require and users may have available (US-03, US-14).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `code` | character varying(30) | NOT NULL |  | PK | Stable equipment code, e.g. DUMBBELLS (primary key). |
| `name` | character varying(100) | NOT NULL |  |  | Human-readable equipment name. |

**Constraints**

- `equipment_pk` (primary key): `PRIMARY KEY (code)`
- `equipment_code_format` (check): `CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]{1,29}$'::text))`
- `equipment_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`

**Indexes**

- `CREATE UNIQUE INDEX equipment_pk ON veya.equipment USING btree (code)`

### `exercise`

Exercise catalogue entry for workout planning and replacement (US-12, US-14). MET values are estimates (LEG-6).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | bigint | NOT NULL | identity (always) | PK | Surrogate key. |
| `name` | character varying(200) | NOT NULL |  |  | Exercise name; unique (case-insensitive) among LOCAL items. |
| `met_value` | numeric(4,1) | NOT NULL |  |  | Metabolic equivalent (MET) estimate, 1.0-25.0 (2024 Adult Compendium style values). |
| `source` | character varying(20) | NOT NULL |  | UQ | LOCAL or PROVIDER (cached from an external exercise provider). |
| `external_ref` | character varying(100) | NULL |  | UQ | Provider identifier; required for PROVIDER items. |
| `fetched_at` | timestamp with time zone | NULL |  |  | When a PROVIDER item was fetched; NULL for LOCAL items. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |

**Constraints**

- `exercise_source_ref_uq` (unique): `UNIQUE (source, external_ref)`
- `exercise_pk` (primary key): `PRIMARY KEY (id)`
- `exercise_met_range` (check): `CHECK (((met_value >= 1.0) AND (met_value <= 25.0)))`
- `exercise_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`
- `exercise_provider_fetched` (check): `CHECK ((((source)::text = 'PROVIDER'::text) = (fetched_at IS NOT NULL)))`
- `exercise_provider_ref` (check): `CHECK ((((source)::text = 'PROVIDER'::text) = (external_ref IS NOT NULL)))`
- `exercise_source_check` (check): `CHECK (((source)::text = ANY ((ARRAY['LOCAL'::character varying, 'PROVIDER'::character varying])::text[])))`

**Indexes**

- `CREATE UNIQUE INDEX exercise_local_name_uq ON veya.exercise USING btree (lower((name)::text)) WHERE ((source)::text = 'LOCAL'::text)`
- `CREATE UNIQUE INDEX exercise_pk ON veya.exercise USING btree (id)`
- `CREATE UNIQUE INDEX exercise_source_ref_uq ON veya.exercise USING btree (source, external_ref)`

### `exercise_equipment`

Equipment required by an exercise; an exercise without rows needs no equipment (US-14).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `exercise_id` | bigint | NOT NULL |  | PK, FK → `exercise` | Exercise (FK exercise). |
| `equipment_code` | character varying(30) | NOT NULL |  | PK, FK → `equipment` | Required equipment (FK equipment). |

**Constraints**

- `exercise_equipment_pk` (primary key): `PRIMARY KEY (exercise_id, equipment_code)`
- `exercise_equipment_equipment_fk` (foreign key): `FOREIGN KEY (equipment_code) REFERENCES equipment(code)`
- `exercise_equipment_exercise_fk` (foreign key): `FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE CASCADE`

**Indexes**

- `CREATE INDEX exercise_equipment_equipment_idx ON veya.exercise_equipment USING btree (equipment_code)`
- `CREATE UNIQUE INDEX exercise_equipment_pk ON veya.exercise_equipment USING btree (exercise_id, equipment_code)`

### `food_entry`

Confirmed food diary entry (Fig. B.11 FoodEntry; US-06). Only user-confirmed values are stored.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `user_id` | uuid | NOT NULL |  | FK → `app_user` | Owner (FK app_user, cascade). |
| `entry_date` | date | NOT NULL |  |  | Date the food was eaten. |
| `meal_slot_code` | character varying(20) | NOT NULL |  | FK → `meal_slot` | Meal slot (FK meal_slot). |
| `input_source` | character varying(12) | NOT NULL |  |  | How the entry was created: CATALOG (search), MANUAL or SAVED_MEAL (US-05, US-06, US-08). Photo input is post-MVP (US-27/28). |
| `food_item_id` | bigint | NULL |  | FK → `food_item` | Catalogue item used; required for CATALOG entries (FK food_item, deletion restricted). |
| `saved_meal_id` | uuid | NULL |  | FK → `saved_meal` | Template the entry was created from (FK saved_meal, set NULL if the template is deleted). |
| `plan_item_id` | uuid | NULL |  | FK → `plan_item` | Planned MEAL item this entry fulfils; must be in the active version of the same user and date (trigger). Set NULL if the plan is erased - actual records are kept. |
| `food_name` | character varying(200) | NOT NULL |  |  | Food name as confirmed by the user. |
| `portion_g` | numeric(7,1) | NOT NULL |  |  | Eaten portion, grams. |
| `kcal` | numeric(7,1) | NOT NULL |  |  | Confirmed energy of the portion, kcal. |
| `protein_g` | numeric(6,1) | NOT NULL |  |  | Confirmed protein, grams. |
| `fat_g` | numeric(6,1) | NOT NULL |  |  | Confirmed fat, grams. |
| `carbs_g` | numeric(6,1) | NOT NULL |  |  | Confirmed carbohydrates, grams. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | When the entry was recorded. |
| `updated_at` | timestamp with time zone | NOT NULL | `now()` |  | Last correction time (maintained by trigger). |

**Constraints**

- `food_entry_pk` (primary key): `PRIMARY KEY (id)`
- `food_entry_food_item_fk` (foreign key): `FOREIGN KEY (food_item_id) REFERENCES food_item(id)`
- `food_entry_meal_slot_fk` (foreign key): `FOREIGN KEY (meal_slot_code) REFERENCES meal_slot(code)`
- `food_entry_plan_item_fk` (foreign key): `FOREIGN KEY (plan_item_id) REFERENCES plan_item(id) ON DELETE SET NULL`
- `food_entry_saved_meal_fk` (foreign key): `FOREIGN KEY (saved_meal_id) REFERENCES saved_meal(id) ON DELETE SET NULL`
- `food_entry_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `food_entry_carbs_range` (check): `CHECK (((carbs_g >= (0)::numeric) AND (carbs_g <= (1000)::numeric)))`
- `food_entry_catalog_needs_item` (check): `CHECK ((((input_source)::text <> 'CATALOG'::text) OR (food_item_id IS NOT NULL)))`
- `food_entry_fat_range` (check): `CHECK (((fat_g >= (0)::numeric) AND (fat_g <= (1000)::numeric)))`
- `food_entry_kcal_range` (check): `CHECK (((kcal >= (0)::numeric) AND (kcal <= (10000)::numeric)))`
- `food_entry_name_not_blank` (check): `CHECK ((btrim((food_name)::text) <> ''::text))`
- `food_entry_portion_range` (check): `CHECK (((portion_g > (0)::numeric) AND (portion_g <= (5000)::numeric)))`
- `food_entry_protein_range` (check): `CHECK (((protein_g >= (0)::numeric) AND (protein_g <= (1000)::numeric)))`
- `food_entry_source_check` (check): `CHECK (((input_source)::text = ANY ((ARRAY['CATALOG'::character varying, 'MANUAL'::character varying, 'SAVED_MEAL'::character varying])::text[])))`
- `food_entry_updated_after_created` (check): `CHECK ((updated_at >= created_at))`

**Indexes**

- `CREATE INDEX food_entry_food_item_idx ON veya.food_entry USING btree (food_item_id) WHERE (food_item_id IS NOT NULL)`
- `CREATE UNIQUE INDEX food_entry_pk ON veya.food_entry USING btree (id)`
- `CREATE INDEX food_entry_plan_item_idx ON veya.food_entry USING btree (plan_item_id) WHERE (plan_item_id IS NOT NULL)`
- `CREATE INDEX food_entry_saved_meal_idx ON veya.food_entry USING btree (saved_meal_id) WHERE (saved_meal_id IS NOT NULL)`
- `CREATE INDEX food_entry_user_date_idx ON veya.food_entry USING btree (user_id, entry_date)`
- `CREATE INDEX food_entry_user_recent_idx ON veya.food_entry USING btree (user_id, created_at DESC)`

### `food_item`

Normalized food catalogue entry with nutrition per 100 g (US-05). Values are estimates (LEG-6).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | bigint | NOT NULL | identity (always) | PK | Surrogate key. |
| `name` | character varying(200) | NOT NULL |  |  | Display name; unique (case-insensitive) among LOCAL items. |
| `source` | character varying(20) | NOT NULL |  | UQ | LOCAL (maintained in VEYA) or PROVIDER (cached from an external food provider, US-31). |
| `external_ref` | character varying(100) | NULL |  | UQ | Provider identifier; required for PROVIDER items, NULL for LOCAL items. |
| `kcal_per_100g` | numeric(6,1) | NOT NULL |  |  | Energy per 100 g, kcal (0-900). |
| `protein_per_100g` | numeric(5,1) | NOT NULL |  |  | Protein per 100 g, grams. |
| `fat_per_100g` | numeric(5,1) | NOT NULL |  |  | Fat per 100 g, grams. |
| `carbs_per_100g` | numeric(5,1) | NOT NULL |  |  | Carbohydrates per 100 g, grams. |
| `fetched_at` | timestamp with time zone | NULL |  |  | When a PROVIDER item was fetched/normalized; NULL for LOCAL items. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |

**Constraints**

- `food_item_source_ref_uq` (unique): `UNIQUE (source, external_ref)`
- `food_item_pk` (primary key): `PRIMARY KEY (id)`
- `food_item_carbs_range` (check): `CHECK (((carbs_per_100g >= (0)::numeric) AND (carbs_per_100g <= (100)::numeric)))`
- `food_item_fat_range` (check): `CHECK (((fat_per_100g >= (0)::numeric) AND (fat_per_100g <= (100)::numeric)))`
- `food_item_kcal_range` (check): `CHECK (((kcal_per_100g >= (0)::numeric) AND (kcal_per_100g <= (900)::numeric)))`
- `food_item_macros_max_100g` (check): `CHECK ((((protein_per_100g + fat_per_100g) + carbs_per_100g) <= (100)::numeric))`
- `food_item_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`
- `food_item_protein_range` (check): `CHECK (((protein_per_100g >= (0)::numeric) AND (protein_per_100g <= (100)::numeric)))`
- `food_item_provider_fetched` (check): `CHECK ((((source)::text = 'PROVIDER'::text) = (fetched_at IS NOT NULL)))`
- `food_item_provider_ref` (check): `CHECK ((((source)::text = 'PROVIDER'::text) = (external_ref IS NOT NULL)))`
- `food_item_source_check` (check): `CHECK (((source)::text = ANY ((ARRAY['LOCAL'::character varying, 'PROVIDER'::character varying])::text[])))`

**Indexes**

- `CREATE UNIQUE INDEX food_item_local_name_uq ON veya.food_item USING btree (lower((name)::text)) WHERE ((source)::text = 'LOCAL'::text)`
- `CREATE INDEX food_item_name_trgm_idx ON veya.food_item USING gin (lower((name)::text) gin_trgm_ops)`
- `CREATE UNIQUE INDEX food_item_pk ON veya.food_item USING btree (id)`
- `CREATE UNIQUE INDEX food_item_source_ref_uq ON veya.food_item USING btree (source, external_ref)`

### `food_item_ingredient`

Which ingredients a food item contains; used to apply exclusions and availability (US-03, US-10).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `food_item_id` | bigint | NOT NULL |  | PK, FK → `food_item` | Food item (FK food_item). |
| `ingredient_code` | character varying(30) | NOT NULL |  | PK, FK → `ingredient` | Contained ingredient (FK ingredient). |

**Constraints**

- `food_item_ingredient_pk` (primary key): `PRIMARY KEY (food_item_id, ingredient_code)`
- `food_item_ingredient_food_fk` (foreign key): `FOREIGN KEY (food_item_id) REFERENCES food_item(id) ON DELETE CASCADE`
- `food_item_ingredient_ingredient_fk` (foreign key): `FOREIGN KEY (ingredient_code) REFERENCES ingredient(code)`

**Indexes**

- `CREATE INDEX food_item_ingredient_ingredient_idx ON veya.food_item_ingredient USING btree (ingredient_code)`
- `CREATE UNIQUE INDEX food_item_ingredient_pk ON veya.food_item_ingredient USING btree (food_item_id, ingredient_code)`

### `goal_type`

Reference: personal goal categories a profile can select (US-02).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `code` | character varying(30) | NOT NULL |  | PK | Stable goal code, e.g. LOSE_WEIGHT (primary key). |
| `name` | character varying(100) | NOT NULL |  |  | Human-readable goal name. |

**Constraints**

- `goal_type_pk` (primary key): `PRIMARY KEY (code)`
- `goal_type_code_format` (check): `CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]{1,29}$'::text))`
- `goal_type_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`

**Indexes**

- `CREATE UNIQUE INDEX goal_type_pk ON veya.goal_type USING btree (code)`

### `ingredient`

Reference: ingredients / product groups used for food exclusions and ingredient availability (US-03, US-10, Q3, Q5).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `code` | character varying(30) | NOT NULL |  | PK | Stable ingredient code, e.g. PEANUT (primary key). |
| `name` | character varying(100) | NOT NULL |  |  | Human-readable ingredient name. |

**Constraints**

- `ingredient_pk` (primary key): `PRIMARY KEY (code)`
- `ingredient_code_format` (check): `CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]{1,29}$'::text))`
- `ingredient_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`

**Indexes**

- `CREATE UNIQUE INDEX ingredient_pk ON veya.ingredient USING btree (code)`

### `meal`

Candidate meal (recipe) for meal planning and replacement (US-09, US-10). Nutrition is derived from its components (view v_meal_nutrition).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | bigint | NOT NULL | identity (always) | PK | Surrogate key. |
| `name` | character varying(200) | NOT NULL |  |  | Meal name; unique case-insensitively. |
| `meal_slot_code` | character varying(20) | NULL |  | FK → `meal_slot` | Preferred meal slot (FK meal_slot); NULL = suitable for any slot. |
| `prep_time_min` | smallint | NOT NULL |  |  | Preparation time in minutes; used for time constraints (0-600). |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |

**Constraints**

- `meal_pk` (primary key): `PRIMARY KEY (id)`
- `meal_slot_fk` (foreign key): `FOREIGN KEY (meal_slot_code) REFERENCES meal_slot(code)`
- `meal_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`
- `meal_prep_time_range` (check): `CHECK (((prep_time_min >= 0) AND (prep_time_min <= 600)))`

**Indexes**

- `CREATE UNIQUE INDEX meal_name_uq ON veya.meal USING btree (lower((name)::text))`
- `CREATE UNIQUE INDEX meal_pk ON veya.meal USING btree (id)`

### `meal_component`

Food items and amounts that make up a candidate meal.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `meal_id` | bigint | NOT NULL |  | PK, FK → `meal` | Meal (FK meal). |
| `food_item_id` | bigint | NOT NULL |  | PK, FK → `food_item` | Food item used in the meal (FK food_item; deletion restricted while used). |
| `grams` | numeric(7,1) | NOT NULL |  |  | Amount of the food item in grams (0 < grams <= 3000). |

**Constraints**

- `meal_component_pk` (primary key): `PRIMARY KEY (meal_id, food_item_id)`
- `meal_component_food_fk` (foreign key): `FOREIGN KEY (food_item_id) REFERENCES food_item(id)`
- `meal_component_meal_fk` (foreign key): `FOREIGN KEY (meal_id) REFERENCES meal(id) ON DELETE CASCADE`
- `meal_component_grams_range` (check): `CHECK (((grams > (0)::numeric) AND (grams <= (3000)::numeric)))`

**Indexes**

- `CREATE INDEX meal_component_food_item_idx ON veya.meal_component USING btree (food_item_id)`
- `CREATE UNIQUE INDEX meal_component_pk ON veya.meal_component USING btree (meal_id, food_item_id)`

### `meal_slot`

Reference: meal slots of a day (breakfast, lunch, ...) used by plans and the diary (US-06, US-09).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `code` | character varying(20) | NOT NULL |  | PK | Stable slot code, e.g. BREAKFAST (primary key). |
| `name` | character varying(50) | NOT NULL |  |  | Human-readable slot name. |
| `sort_order` | smallint | NOT NULL |  | UQ | Display order within a day; unique. |

**Constraints**

- `meal_slot_sort_order_uq` (unique): `UNIQUE (sort_order)`
- `meal_slot_pk` (primary key): `PRIMARY KEY (code)`
- `meal_slot_code_format` (check): `CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]{1,19}$'::text))`
- `meal_slot_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`
- `meal_slot_sort_order_positive` (check): `CHECK ((sort_order > 0))`

**Indexes**

- `CREATE UNIQUE INDEX meal_slot_pk ON veya.meal_slot USING btree (code)`
- `CREATE UNIQUE INDEX meal_slot_sort_order_uq ON veya.meal_slot USING btree (sort_order)`

### `plan_item`

Planned meal or workout inside one plan version (Fig. B.11 PlanItem). Content is immutable; only status of items in the active version can be recorded.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key (unique per version). |
| `plan_version_id` | uuid | NOT NULL |  | UQ, FK → `plan_version` | Version containing the item (FK plan_version, cascade). |
| `item_key` | uuid | NOT NULL |  | UQ | Logical identity of the item across versions (frozen and replaced items keep their key); unique within a version. |
| `item_type` | character varying(10) | NOT NULL |  |  | MEAL or WORKOUT; determines which columns are filled (CHECK plan_item_meal_shape / plan_item_workout_shape). |
| `meal_slot_code` | character varying(20) | NULL |  | FK → `meal_slot` | MEAL: slot of the day (FK meal_slot). NULL for workouts. |
| `planned_time` | time without time zone | NULL |  |  | Planned local time of the item; NULL = no fixed time. |
| `meal_id` | bigint | NULL |  | FK → `meal` | MEAL: planned catalogue meal (FK meal). NULL for workouts. |
| `exercise_id` | bigint | NULL |  | FK → `exercise` | WORKOUT: planned exercise (FK exercise). NULL for meals. |
| `planned_kcal` | numeric(7,1) | NULL |  |  | MEAL: planned energy, kcal - snapshot at planning time so history does not change with the catalogue. |
| `planned_protein_g` | numeric(6,1) | NULL |  |  | MEAL: planned protein, grams (snapshot). |
| `planned_fat_g` | numeric(6,1) | NULL |  |  | MEAL: planned fat, grams (snapshot). |
| `planned_carbs_g` | numeric(6,1) | NULL |  |  | MEAL: planned carbohydrates, grams (snapshot). |
| `duration_min` | smallint | NULL |  |  | WORKOUT: planned duration in minutes (1-600). |
| `status` | character varying(10) | NOT NULL | `'PLANNED'::character varying` |  | PLANNED, COMPLETED, SKIPPED, MISSED or CHANGED (workouts only). Changes only from PLANNED and only in the active version (US-13). |

**Constraints**

- `plan_item_version_key_uq` (unique): `UNIQUE (plan_version_id, item_key)`
- `plan_item_pk` (primary key): `PRIMARY KEY (id)`
- `plan_item_exercise_fk` (foreign key): `FOREIGN KEY (exercise_id) REFERENCES exercise(id)`
- `plan_item_meal_fk` (foreign key): `FOREIGN KEY (meal_id) REFERENCES meal(id)`
- `plan_item_meal_slot_fk` (foreign key): `FOREIGN KEY (meal_slot_code) REFERENCES meal_slot(code)`
- `plan_item_version_fk` (foreign key): `FOREIGN KEY (plan_version_id) REFERENCES plan_version(id) ON DELETE CASCADE`
- `plan_item_carbs_range` (check): `CHECK (((planned_carbs_g IS NULL) OR ((planned_carbs_g >= (0)::numeric) AND (planned_carbs_g <= (1000)::numeric))))`
- `plan_item_duration_range` (check): `CHECK (((duration_min IS NULL) OR ((duration_min >= 1) AND (duration_min <= 600))))`
- `plan_item_fat_range` (check): `CHECK (((planned_fat_g IS NULL) OR ((planned_fat_g >= (0)::numeric) AND (planned_fat_g <= (1000)::numeric))))`
- `plan_item_kcal_range` (check): `CHECK (((planned_kcal IS NULL) OR ((planned_kcal >= (0)::numeric) AND (planned_kcal <= (10000)::numeric))))`
- `plan_item_meal_shape` (check): `CHECK ((((item_type)::text <> 'MEAL'::text) OR ((meal_slot_code IS NOT NULL) AND (meal_id IS NOT NULL) AND (exercise_id IS NULL) AND (duration_min IS NULL) AND (planned_kcal IS NOT NULL) AND (planned_protein_g IS NOT NULL) AND (planned_fat_g IS NOT NULL) AND (planned_carbs_g IS NOT NULL))))`
- `plan_item_meal_status` (check): `CHECK ((((item_type)::text <> 'MEAL'::text) OR ((status)::text <> 'CHANGED'::text)))`
- `plan_item_protein_range` (check): `CHECK (((planned_protein_g IS NULL) OR ((planned_protein_g >= (0)::numeric) AND (planned_protein_g <= (1000)::numeric))))`
- `plan_item_status_check` (check): `CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'COMPLETED'::character varying, 'SKIPPED'::character varying, 'MISSED'::character varying, 'CHANGED'::character varying])::text[])))`
- `plan_item_type_check` (check): `CHECK (((item_type)::text = ANY ((ARRAY['MEAL'::character varying, 'WORKOUT'::character varying])::text[])))`
- `plan_item_workout_shape` (check): `CHECK ((((item_type)::text <> 'WORKOUT'::text) OR ((exercise_id IS NOT NULL) AND (duration_min IS NOT NULL) AND (meal_slot_code IS NULL) AND (meal_id IS NULL) AND (planned_kcal IS NULL) AND (planned_protein_g IS NULL) AND (planned_fat_g IS NULL) AND (planned_carbs_g IS NULL))))`

**Indexes**

- `CREATE INDEX plan_item_exercise_idx ON veya.plan_item USING btree (exercise_id) WHERE (exercise_id IS NOT NULL)`
- `CREATE INDEX plan_item_meal_idx ON veya.plan_item USING btree (meal_id) WHERE (meal_id IS NOT NULL)`
- `CREATE UNIQUE INDEX plan_item_pk ON veya.plan_item USING btree (id)`
- `CREATE UNIQUE INDEX plan_item_version_key_uq ON veya.plan_item USING btree (plan_version_id, item_key)`

### `plan_version`

Immutable version of a daily plan (Q2). Created only through create_plan_version / apply_recovery_decision.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `daily_plan_id` | uuid | NOT NULL |  | UQ, FK → `daily_plan` | Plan this version belongs to (FK daily_plan, cascade). |
| `version_no` | integer | NOT NULL |  | UQ | Sequential number 1, 2, ... without gaps (enforced by trigger). |
| `change_reason` | character varying(30) | NOT NULL |  |  | Why the version was created: INITIAL_PLAN, MEAL_/EXERCISE_REPLACEMENT (US-10/US-14) or RECOVERY_ACCEPTED/EDITED (US-18). |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | When the version was created. |

**Constraints**

- `plan_version_plan_no_uq` (unique): `UNIQUE (daily_plan_id, version_no)`
- `plan_version_pk` (primary key): `PRIMARY KEY (id)`
- `plan_version_plan_fk` (foreign key): `FOREIGN KEY (daily_plan_id) REFERENCES daily_plan(id) ON DELETE CASCADE`
- `plan_version_initial_is_first` (check): `CHECK (((version_no = 1) = ((change_reason)::text = 'INITIAL_PLAN'::text)))`
- `plan_version_no_positive` (check): `CHECK ((version_no >= 1))`
- `plan_version_reason_check` (check): `CHECK (((change_reason)::text = ANY ((ARRAY['INITIAL_PLAN'::character varying, 'MEAL_REPLACEMENT'::character varying, 'EXERCISE_REPLACEMENT'::character varying, 'RECOVERY_ACCEPTED'::character varying, 'RECOVERY_EDITED'::character varying])::text[])))`

**Indexes**

- `CREATE UNIQUE INDEX plan_version_pk ON veya.plan_version USING btree (id)`
- `CREATE UNIQUE INDEX plan_version_plan_no_uq ON veya.plan_version USING btree (daily_plan_id, version_no)`

### `profile_food_exclusion`

Permanent food exclusions stored in the profile; hard constraints for planning and recovery (US-03, Q5).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `user_id` | uuid | NOT NULL |  | PK, FK → `user_profile` | Profile owner (FK user_profile). |
| `ingredient_code` | character varying(30) | NOT NULL |  | PK, FK → `ingredient` | Excluded ingredient (FK ingredient). |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | When the exclusion was added. |

**Constraints**

- `profile_food_exclusion_pk` (primary key): `PRIMARY KEY (user_id, ingredient_code)`
- `profile_food_exclusion_ingredient_fk` (foreign key): `FOREIGN KEY (ingredient_code) REFERENCES ingredient(code)`
- `profile_food_exclusion_profile_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES user_profile(user_id) ON DELETE CASCADE`

**Indexes**

- `CREATE UNIQUE INDEX profile_food_exclusion_pk ON veya.profile_food_exclusion USING btree (user_id, ingredient_code)`

### `recovery_change`

One changed item of a proposal with its human-readable reason (Fig. B.11 RecoveryChange; US-17).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `proposal_id` | uuid | NOT NULL |  | UQ, FK → `recovery_proposal` | Proposal (FK recovery_proposal, cascade). |
| `change_no` | smallint | NOT NULL |  | UQ | Order of the change within the proposal (>= 1). |
| `change_type` | character varying(10) | NOT NULL |  |  | REPLACE a planned item, REMOVE it, or ADD a new item. |
| `target_item_id` | uuid | NULL |  | FK → `plan_item` | Planned item of the base version being replaced/removed (old value); NULL for ADD. Must be PLANNED - completed items are frozen (US-16). |
| `item_type` | character varying(10) | NULL |  |  | New item type (MEAL/WORKOUT) for REPLACE and ADD; NULL for REMOVE. REPLACE keeps the target's type. |
| `meal_slot_code` | character varying(20) | NULL |  | FK → `meal_slot` | New MEAL slot (FK meal_slot). |
| `planned_time` | time without time zone | NULL |  |  | New planned time. |
| `meal_id` | bigint | NULL |  | FK → `meal` | New MEAL catalogue meal (FK meal). |
| `exercise_id` | bigint | NULL |  | FK → `exercise` | New WORKOUT exercise (FK exercise). |
| `planned_kcal` | numeric(7,1) | NULL |  |  | New MEAL planned kcal. |
| `planned_protein_g` | numeric(6,1) | NULL |  |  | New MEAL planned protein, grams. |
| `planned_fat_g` | numeric(6,1) | NULL |  |  | New MEAL planned fat, grams. |
| `planned_carbs_g` | numeric(6,1) | NULL |  |  | New MEAL planned carbohydrates, grams. |
| `duration_min` | smallint | NULL |  |  | New WORKOUT duration, minutes. |
| `reason_category` | character varying(12) | NOT NULL |  |  | Constraint the reason is tied to: NUTRITION, TIME, INGREDIENT, EQUIPMENT or OTHER (US-17). |
| `reason_text` | character varying(500) | NOT NULL |  |  | Human-readable reason shown to the user (US-17). |

**Constraints**

- `recovery_change_no_uq` (unique): `UNIQUE (proposal_id, change_no)`
- `recovery_change_pk` (primary key): `PRIMARY KEY (id)`
- `recovery_change_exercise_fk` (foreign key): `FOREIGN KEY (exercise_id) REFERENCES exercise(id)`
- `recovery_change_meal_fk` (foreign key): `FOREIGN KEY (meal_id) REFERENCES meal(id)`
- `recovery_change_meal_slot_fk` (foreign key): `FOREIGN KEY (meal_slot_code) REFERENCES meal_slot(code)`
- `recovery_change_proposal_fk` (foreign key): `FOREIGN KEY (proposal_id) REFERENCES recovery_proposal(id) ON DELETE CASCADE`
- `recovery_change_target_fk` (foreign key): `FOREIGN KEY (target_item_id) REFERENCES plan_item(id) ON DELETE CASCADE`
- `recovery_change_carbs_range` (check): `CHECK (((planned_carbs_g IS NULL) OR ((planned_carbs_g >= (0)::numeric) AND (planned_carbs_g <= (1000)::numeric))))`
- `recovery_change_duration_range` (check): `CHECK (((duration_min IS NULL) OR ((duration_min >= 1) AND (duration_min <= 600))))`
- `recovery_change_fat_range` (check): `CHECK (((planned_fat_g IS NULL) OR ((planned_fat_g >= (0)::numeric) AND (planned_fat_g <= (1000)::numeric))))`
- `recovery_change_item_type_check` (check): `CHECK (((((change_type)::text = 'REMOVE'::text) AND (item_type IS NULL)) OR (((change_type)::text <> 'REMOVE'::text) AND (item_type IS NOT NULL) AND ((item_type)::text = ANY ((ARRAY['MEAL'::character varying, 'WORKOUT'::character varying])::text[])))))`
- `recovery_change_kcal_range` (check): `CHECK (((planned_kcal IS NULL) OR ((planned_kcal >= (0)::numeric) AND (planned_kcal <= (10000)::numeric))))`
- `recovery_change_meal_shape` (check): `CHECK ((((item_type)::text IS DISTINCT FROM 'MEAL'::text) OR ((meal_slot_code IS NOT NULL) AND (meal_id IS NOT NULL) AND (exercise_id IS NULL) AND (duration_min IS NULL) AND (planned_kcal IS NOT NULL) AND (planned_protein_g IS NOT NULL) AND (planned_fat_g IS NOT NULL) AND (planned_carbs_g IS NOT NULL))))`
- `recovery_change_no_positive` (check): `CHECK ((change_no >= 1))`
- `recovery_change_protein_range` (check): `CHECK (((planned_protein_g IS NULL) OR ((planned_protein_g >= (0)::numeric) AND (planned_protein_g <= (1000)::numeric))))`
- `recovery_change_reason_category_check` (check): `CHECK (((reason_category)::text = ANY ((ARRAY['NUTRITION'::character varying, 'TIME'::character varying, 'INGREDIENT'::character varying, 'EQUIPMENT'::character varying, 'OTHER'::character varying])::text[])))`
- `recovery_change_reason_not_blank` (check): `CHECK ((btrim((reason_text)::text) <> ''::text))`
- `recovery_change_remove_has_no_item` (check): `CHECK ((((change_type)::text <> 'REMOVE'::text) OR ((item_type IS NULL) AND (meal_slot_code IS NULL) AND (planned_time IS NULL) AND (meal_id IS NULL) AND (exercise_id IS NULL) AND (planned_kcal IS NULL) AND (planned_protein_g IS NULL) AND (planned_fat_g IS NULL) AND (planned_carbs_g IS NULL) AND (duration_min IS NULL))))`
- `recovery_change_target_by_type` (check): `CHECK ((((change_type)::text = 'ADD'::text) = (target_item_id IS NULL)))`
- `recovery_change_type_check` (check): `CHECK (((change_type)::text = ANY ((ARRAY['REPLACE'::character varying, 'REMOVE'::character varying, 'ADD'::character varying])::text[])))`
- `recovery_change_workout_shape` (check): `CHECK ((((item_type)::text IS DISTINCT FROM 'WORKOUT'::text) OR ((exercise_id IS NOT NULL) AND (duration_min IS NOT NULL) AND (meal_slot_code IS NULL) AND (meal_id IS NULL) AND (planned_kcal IS NULL) AND (planned_protein_g IS NULL) AND (planned_fat_g IS NULL) AND (planned_carbs_g IS NULL))))`

**Indexes**

- `CREATE UNIQUE INDEX recovery_change_no_uq ON veya.recovery_change USING btree (proposal_id, change_no)`
- `CREATE UNIQUE INDEX recovery_change_pk ON veya.recovery_change USING btree (id)`
- `CREATE INDEX recovery_change_target_idx ON veya.recovery_change USING btree (target_item_id) WHERE (target_item_id IS NOT NULL)`
- `CREATE UNIQUE INDEX recovery_change_target_uq ON veya.recovery_change USING btree (proposal_id, target_item_id) WHERE (target_item_id IS NOT NULL)`

### `recovery_decision`

User decision on a proposal (US-18), at most one per proposal. Written only by apply_recovery_decision.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `proposal_id` | uuid | NOT NULL |  | UQ, FK → `recovery_proposal` | Decided proposal (FK recovery_proposal, unique: 1 : 0..1). |
| `decision` | character varying(10) | NOT NULL |  |  | ACCEPT, EDIT or REJECT. |
| `resulting_version_id` | uuid | NULL |  | UQ, FK → `plan_version` | Plan version created by ACCEPT/EDIT; NULL for REJECT (active plan unchanged). |
| `decided_at` | timestamp with time zone | NOT NULL | `now()` |  | When the decision was confirmed. |

**Constraints**

- `recovery_decision_proposal_uq` (unique): `UNIQUE (proposal_id)`
- `recovery_decision_version_uq` (unique): `UNIQUE (resulting_version_id)`
- `recovery_decision_pk` (primary key): `PRIMARY KEY (id)`
- `recovery_decision_proposal_fk` (foreign key): `FOREIGN KEY (proposal_id) REFERENCES recovery_proposal(id) ON DELETE CASCADE`
- `recovery_decision_version_fk` (foreign key): `FOREIGN KEY (resulting_version_id) REFERENCES plan_version(id) ON DELETE CASCADE`
- `recovery_decision_check` (check): `CHECK (((decision)::text = ANY ((ARRAY['ACCEPT'::character varying, 'EDIT'::character varying, 'REJECT'::character varying])::text[])))`
- `recovery_decision_version_by_decision` (check): `CHECK ((((decision)::text = 'REJECT'::text) = (resulting_version_id IS NULL)))`

**Indexes**

- `CREATE UNIQUE INDEX recovery_decision_pk ON veya.recovery_decision USING btree (id)`
- `CREATE UNIQUE INDEX recovery_decision_proposal_uq ON veya.recovery_decision USING btree (proposal_id)`
- `CREATE UNIQUE INDEX recovery_decision_version_uq ON veya.recovery_decision USING btree (resulting_version_id)`

### `recovery_proposal`

Recovery proposal for the remaining day (US-16). Immutable; status derived in v_recovery_proposal_overview.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key (proposalId in the API). |
| `daily_plan_id` | uuid | NOT NULL |  | FK → `daily_plan` | Plan being recovered (FK daily_plan, cascade). |
| `base_version_no` | integer | NOT NULL |  |  | Plan version the proposal was computed from (baseVersion, US-18); must equal the current version when created. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | When the proposal was generated. |

**Constraints**

- `recovery_proposal_pk` (primary key): `PRIMARY KEY (id)`
- `recovery_proposal_plan_fk` (foreign key): `FOREIGN KEY (daily_plan_id) REFERENCES daily_plan(id) ON DELETE CASCADE`
- `recovery_proposal_base_version_positive` (check): `CHECK ((base_version_no >= 1))`

**Indexes**

- `CREATE UNIQUE INDEX recovery_proposal_pk ON veya.recovery_proposal USING btree (id)`
- `CREATE INDEX recovery_proposal_plan_idx ON veya.recovery_proposal USING btree (daily_plan_id, created_at)`

### `recovery_setting`

Single-row configuration of deviation detection (US-15: configurable threshold, default 200 kcal).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | boolean | NOT NULL | `true` | PK | Always TRUE; together with the CHECK it guarantees exactly one configuration row. |
| `kcal_deviation_threshold` | smallint | NOT NULL |  |  | A confirmed meal differing from its plan by at least this many kcal is a significant deviation (US-15; default 200). |

**Constraints**

- `recovery_setting_pk` (primary key): `PRIMARY KEY (id)`
- `recovery_setting_single_row` (check): `CHECK (id)`
- `recovery_setting_threshold_range` (check): `CHECK (((kcal_deviation_threshold >= 1) AND (kcal_deviation_threshold <= 2000)))`

**Indexes**

- `CREATE UNIQUE INDEX recovery_setting_pk ON veya.recovery_setting USING btree (id)`

### `saved_meal`

User-defined meal template for quick re-logging (US-08). Reusing it never alters the template.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `user_id` | uuid | NOT NULL |  | UQ, FK → `app_user` | Owner (FK app_user, cascade). |
| `name` | character varying(100) | NOT NULL |  | UQ | Template name; unique per user. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |

**Constraints**

- `saved_meal_user_name_uq` (unique): `UNIQUE (user_id, name)`
- `saved_meal_pk` (primary key): `PRIMARY KEY (id)`
- `saved_meal_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `saved_meal_name_not_blank` (check): `CHECK ((btrim((name)::text) <> ''::text))`

**Indexes**

- `CREATE UNIQUE INDEX saved_meal_pk ON veya.saved_meal USING btree (id)`
- `CREATE UNIQUE INDEX saved_meal_user_name_uq ON veya.saved_meal USING btree (user_id, name)`

### `saved_meal_item`

Lines of a saved meal template with the confirmed values to copy into the diary.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `saved_meal_id` | uuid | NOT NULL |  | PK, FK → `saved_meal` | Template (FK saved_meal, cascade). |
| `line_no` | smallint | NOT NULL |  | PK | Line number within the template (>= 1). |
| `food_item_id` | bigint | NULL |  | FK → `food_item` | Catalogue item the line came from; NULL for manual lines (FK food_item, set NULL if the item is removed). |
| `food_name` | character varying(200) | NOT NULL |  |  | Food name as confirmed by the user. |
| `portion_g` | numeric(7,1) | NOT NULL |  |  | Portion, grams (0 < portion <= 5000). |
| `kcal` | numeric(7,1) | NOT NULL |  |  | Energy of the portion, kcal. |
| `protein_g` | numeric(6,1) | NOT NULL |  |  | Protein of the portion, grams. |
| `fat_g` | numeric(6,1) | NOT NULL |  |  | Fat of the portion, grams. |
| `carbs_g` | numeric(6,1) | NOT NULL |  |  | Carbohydrates of the portion, grams. |

**Constraints**

- `saved_meal_item_pk` (primary key): `PRIMARY KEY (saved_meal_id, line_no)`
- `saved_meal_item_food_fk` (foreign key): `FOREIGN KEY (food_item_id) REFERENCES food_item(id) ON DELETE SET NULL`
- `saved_meal_item_meal_fk` (foreign key): `FOREIGN KEY (saved_meal_id) REFERENCES saved_meal(id) ON DELETE CASCADE`
- `saved_meal_item_carbs_range` (check): `CHECK (((carbs_g >= (0)::numeric) AND (carbs_g <= (1000)::numeric)))`
- `saved_meal_item_fat_range` (check): `CHECK (((fat_g >= (0)::numeric) AND (fat_g <= (1000)::numeric)))`
- `saved_meal_item_kcal_range` (check): `CHECK (((kcal >= (0)::numeric) AND (kcal <= (10000)::numeric)))`
- `saved_meal_item_line_positive` (check): `CHECK ((line_no >= 1))`
- `saved_meal_item_name_not_blank` (check): `CHECK ((btrim((food_name)::text) <> ''::text))`
- `saved_meal_item_portion_range` (check): `CHECK (((portion_g > (0)::numeric) AND (portion_g <= (5000)::numeric)))`
- `saved_meal_item_protein_range` (check): `CHECK (((protein_g >= (0)::numeric) AND (protein_g <= (1000)::numeric)))`

**Indexes**

- `CREATE INDEX saved_meal_item_food_item_idx ON veya.saved_meal_item USING btree (food_item_id) WHERE (food_item_id IS NOT NULL)`
- `CREATE UNIQUE INDEX saved_meal_item_pk ON veya.saved_meal_item USING btree (saved_meal_id, line_no)`

### `user_consent`

Separately recorded, versioned consent / notice acceptance (LEG-2, Q7). Append-only except a single withdrawal.

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `id` | uuid | NOT NULL | `gen_random_uuid()` | PK | Surrogate key. |
| `user_id` | uuid | NOT NULL |  | FK → `app_user` | Consenting user (FK app_user, cascade on account deletion). |
| `consent_document_id` | smallint | NOT NULL |  | FK → `consent_document` | Accepted document version (FK consent_document). |
| `granted_at` | timestamp with time zone | NOT NULL | `now()` |  | When consent was given. |
| `withdrawn_at` | timestamp with time zone | NULL |  |  | When consent was withdrawn; NULL while active. Can be set once. |

**Constraints**

- `user_consent_pk` (primary key): `PRIMARY KEY (id)`
- `user_consent_document_fk` (foreign key): `FOREIGN KEY (consent_document_id) REFERENCES consent_document(id)`
- `user_consent_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `user_consent_withdrawn_after_granted` (check): `CHECK (((withdrawn_at IS NULL) OR (withdrawn_at >= granted_at)))`

**Indexes**

- `CREATE UNIQUE INDEX user_consent_active_uq ON veya.user_consent USING btree (user_id, consent_document_id) WHERE (withdrawn_at IS NULL)`
- `CREATE UNIQUE INDEX user_consent_pk ON veya.user_consent USING btree (id)`
- `CREATE INDEX user_consent_user_idx ON veya.user_consent USING btree (user_id)`

### `user_profile`

Profile and goal (US-02), 1:1 with app_user. Targets are stored user goals, not calculated (no BMR/TDEE).

| Column | Type | Null | Default | Keys | Description |
|---|---|---|---|---|---|
| `user_id` | uuid | NOT NULL |  | PK, FK → `app_user` | Owner (PK and FK app_user, cascade on deletion). |
| `goal_code` | character varying(30) | NOT NULL |  | FK → `goal_type` | Selected goal (FK goal_type). |
| `energy_target_kcal` | integer | NOT NULL |  |  | Daily energy target in kcal, entered by the user (technical sanity range 500-10000). |
| `protein_target_g` | numeric(5,1) | NOT NULL |  |  | Daily protein target, grams (US-07 macro comparison, Q3). |
| `fat_target_g` | numeric(5,1) | NOT NULL |  |  | Daily fat target, grams. |
| `carbs_target_g` | numeric(5,1) | NOT NULL |  |  | Daily carbohydrate target, grams. |
| `created_at` | timestamp with time zone | NOT NULL | `now()` |  | Row creation time. |
| `updated_at` | timestamp with time zone | NOT NULL | `now()` |  | Last modification time (maintained by trigger). |

**Constraints**

- `user_profile_pk` (primary key): `PRIMARY KEY (user_id)`
- `user_profile_goal_fk` (foreign key): `FOREIGN KEY (goal_code) REFERENCES goal_type(code)`
- `user_profile_user_fk` (foreign key): `FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- `user_profile_carbs_range` (check): `CHECK (((carbs_target_g >= (0)::numeric) AND (carbs_target_g <= (1000)::numeric)))`
- `user_profile_energy_range` (check): `CHECK (((energy_target_kcal >= 500) AND (energy_target_kcal <= 10000)))`
- `user_profile_fat_range` (check): `CHECK (((fat_target_g >= (0)::numeric) AND (fat_target_g <= (1000)::numeric)))`
- `user_profile_protein_range` (check): `CHECK (((protein_target_g >= (0)::numeric) AND (protein_target_g <= (1000)::numeric)))`
- `user_profile_updated_after_created` (check): `CHECK ((updated_at >= created_at))`

**Indexes**

- `CREATE UNIQUE INDEX user_profile_pk ON veya.user_profile USING btree (user_id)`

## Views

### `v_active_plan_item`

Items of the active version of each daily plan.

Columns: `user_id` (uuid), `daily_plan_id` (uuid), `plan_date` (date), `plan_version_id` (uuid), `version_no` (integer), `plan_item_id` (uuid), `item_key` (uuid), `item_type` (character varying(10)), `meal_slot_code` (character varying(20)), `planned_time` (time without time zone), `meal_id` (bigint), `meal_name` (character varying(200)), `exercise_id` (bigint), `exercise_name` (character varying(200)), `planned_kcal` (numeric(7,1)), `planned_protein_g` (numeric(6,1)), `planned_fat_g` (numeric(6,1)), `planned_carbs_g` (numeric(6,1)), `duration_min` (smallint), `status` (character varying(10))

### `v_analytics_daily_nutrition`

Pseudonymised daily nutrition totals for reporting (LEG-8); contains no user id or e-mail.

Columns: `user_ref` (text), `entry_date` (date), `consumed_kcal` (numeric), `consumed_protein_g` (numeric), `consumed_fat_g` (numeric), `consumed_carbs_g` (numeric), `target_kcal` (integer), `entry_count` (bigint)

### `v_app_user_masked`

Masked user list for read-only reporting: pseudonym, masked e-mail, registration month; no id, e-mail or password hash.

Columns: `user_ref` (text), `email_masked` (text), `registered_month` (date), `has_profile` (boolean), `goal_code` (character varying(30)), `active_consents` (bigint)

### `v_daily_nutrition_summary`

US-07: confirmed consumption, targets and remaining amounts per user and date (only days with entries).

Columns: `user_id` (uuid), `entry_date` (date), `consumed_kcal` (numeric), `consumed_protein_g` (numeric), `consumed_fat_g` (numeric), `consumed_carbs_g` (numeric), `target_kcal` (integer), `target_protein_g` (numeric(5,1)), `target_fat_g` (numeric(5,1)), `target_carbs_g` (numeric(5,1)), `remaining_kcal` (numeric), `remaining_protein_g` (numeric), `remaining_fat_g` (numeric), `remaining_carbs_g` (numeric), `entry_count` (bigint)

### `v_day_summary`

US-20 day dashboard: active version, planned vs consumed nutrition, remaining energy, workout minutes, open items, recoveryRequired.

Columns: `user_id` (uuid), `daily_plan_id` (uuid), `plan_date` (date), `active_version_no` (integer), `recovery_required` (boolean), `planned_kcal` (numeric), `consumed_kcal` (numeric), `consumed_protein_g` (numeric), `consumed_fat_g` (numeric), `consumed_carbs_g` (numeric), `target_kcal` (integer), `remaining_kcal` (numeric), `planned_workout_min` (bigint), `completed_workout_min` (bigint), `open_items` (bigint)

### `v_meal_nutrition`

Candidate meal nutrition and ingredient codes derived from meal_component (US-09, US-10).

Columns: `meal_id` (bigint), `meal_name` (character varying(200)), `meal_slot_code` (character varying(20)), `prep_time_min` (smallint), `kcal` (numeric), `protein_g` (numeric), `fat_g` (numeric), `carbs_g` (numeric), `ingredient_codes` (character varying[])

### `v_recovery_proposal_overview`

Proposal status (PENDING, STALE, ACCEPTED, EDITED, REJECTED) derived from the decision and the active version.

Columns: `proposal_id` (uuid), `daily_plan_id` (uuid), `user_id` (uuid), `plan_date` (date), `base_version_no` (integer), `current_version_no` (integer), `status` (text), `change_count` (bigint), `created_at` (timestamp with time zone), `decided_at` (timestamp with time zone)

## Functions and procedures

| Routine | Kind | Security | Description |
|---|---|---|---|
| `activity_entry_after_insert()` | function | DEFINER | Trigger function (see triggers below). |
| `activity_entry_guard()` | function | INVOKER | Trigger function (see triggers below). |
| `apply_recovery_decision(IN p_user_id uuid, IN p_proposal_id uuid, IN p_decision character varying, IN p_base_version integer, IN p_selected_change_ids uuid[], INOUT p_new_version_no integer)` | procedure | DEFINER | US-18: checks ownership and baseVersion, then records ACCEPT/EDIT together with the new plan version, or REJECT without changing the plan, in the caller's transaction (NFR-09). Stale baseVersion -> VY409 (NFR-10). |
| `create_plan_version(p_user_id uuid, p_plan_date date, p_expected_version integer, p_change_reason character varying, p_items jsonb)` | function | DEFINER | Creates the next immutable plan version atomically (Q2). Checks the expected version (409 on mismatch), copies recorded items unchanged (frozen, US-16) and inserts the supplied remaining items. Returns the new version number. |
| `daily_plan_guard()` | function | INVOKER | Trigger function (see triggers below). |
| `deviation_event_after_insert()` | function | DEFINER | Trigger function (see triggers below). |
| `deviation_event_before_insert()` | function | INVOKER | Trigger function (see triggers below). |
| `deviation_event_guard()` | function | INVOKER | Trigger function (see triggers below). |
| `evaluate_meal_deviation(p_plan_item_id uuid)` | function | DEFINER | US-15 rule 1: records a KCAL_DIFFERENCE deviation for a completed meal when the confirmed kcal differ from the plan by >= threshold. |
| `export_user_data(p_user_id uuid)` | function | INVOKER | LEG-4 / Q6: minimal JSON export of one user's own records (no password hash). NULL if the user does not exist. |
| `food_entry_after_change()` | function | DEFINER | Trigger function (see triggers below). |
| `food_entry_guard()` | function | INVOKER | Trigger function (see triggers below). |
| `is_significant_kcal_deviation(p_planned_kcal numeric, p_actual_kcal numeric)` | function | INVOKER | US-15: TRUE when \|actual - planned\| >= recovery_setting.kcal_deviation_threshold (default 200 kcal). |
| `mask_email(p_email text)` | function | INVOKER | Masks an e-mail address: anna.k@example.org -> a***@e***.org. |
| `plan_item_after_status_change()` | function | DEFINER | Trigger function (see triggers below). |
| `plan_item_guard()` | function | INVOKER | Trigger function (see triggers below). |
| `plan_version_after_insert()` | function | DEFINER | Trigger function (see triggers below). |
| `plan_version_before_insert()` | function | INVOKER | Trigger function (see triggers below). |
| `pseudonym(p_id uuid)` | function | INVOKER | Stable, non-reversible pseudonym of a user id (SHA-256) for anonymised reporting views. |
| `recovery_change_before_insert()` | function | INVOKER | Trigger function (see triggers below). |
| `recovery_proposal_before_insert()` | function | INVOKER | Trigger function (see triggers below). |
| `reject_update()` | function | INVOKER | Trigger: rejects UPDATE on append-only tables (history is never rewritten). |
| `set_updated_at()` | function | INVOKER | Trigger: maintains updated_at on every UPDATE. |
| `user_consent_guard()` | function | INVOKER | Trigger function (see triggers below). |

## Triggers

| Trigger | Table | Timing | Events | Function |
|---|---|---|---|---|
| `activity_entry_after_insert` | `activity_entry` | AFTER | INSERT | `activity_entry_after_insert` |
| `activity_entry_guard` | `activity_entry` | BEFORE | INSERT OR UPDATE | `activity_entry_guard` |
| `activity_entry_set_updated_at` | `activity_entry` | BEFORE | UPDATE | `set_updated_at` |
| `app_user_set_updated_at` | `app_user` | BEFORE | UPDATE | `set_updated_at` |
| `consent_document_immutable` | `consent_document` | BEFORE | UPDATE | `reject_update` |
| `daily_constraint_set_updated_at` | `daily_constraint` | BEFORE | UPDATE | `set_updated_at` |
| `daily_plan_guard` | `daily_plan` | BEFORE | UPDATE | `daily_plan_guard` |
| `daily_plan_set_updated_at` | `daily_plan` | BEFORE | UPDATE | `set_updated_at` |
| `deviation_event_after_insert` | `deviation_event` | AFTER | INSERT | `deviation_event_after_insert` |
| `deviation_event_before_insert` | `deviation_event` | BEFORE | INSERT | `deviation_event_before_insert` |
| `deviation_event_guard` | `deviation_event` | BEFORE | UPDATE | `deviation_event_guard` |
| `food_entry_after_change` | `food_entry` | AFTER | INSERT OR UPDATE | `food_entry_after_change` |
| `food_entry_guard` | `food_entry` | BEFORE | INSERT OR UPDATE | `food_entry_guard` |
| `food_entry_set_updated_at` | `food_entry` | BEFORE | UPDATE | `set_updated_at` |
| `plan_item_after_status_change` | `plan_item` | AFTER | UPDATE | `plan_item_after_status_change` |
| `plan_item_guard` | `plan_item` | BEFORE | INSERT OR UPDATE | `plan_item_guard` |
| `plan_version_after_insert` | `plan_version` | AFTER | INSERT | `plan_version_after_insert` |
| `plan_version_before_insert` | `plan_version` | BEFORE | INSERT | `plan_version_before_insert` |
| `plan_version_immutable` | `plan_version` | BEFORE | UPDATE | `reject_update` |
| `recovery_change_before_insert` | `recovery_change` | BEFORE | INSERT | `recovery_change_before_insert` |
| `recovery_change_immutable` | `recovery_change` | BEFORE | UPDATE | `reject_update` |
| `recovery_decision_immutable` | `recovery_decision` | BEFORE | UPDATE | `reject_update` |
| `recovery_proposal_before_insert` | `recovery_proposal` | BEFORE | INSERT | `recovery_proposal_before_insert` |
| `recovery_proposal_immutable` | `recovery_proposal` | BEFORE | UPDATE | `reject_update` |
| `user_consent_guard` | `user_consent` | BEFORE | UPDATE | `user_consent_guard` |
| `user_profile_set_updated_at` | `user_profile` | BEFORE | UPDATE | `set_updated_at` |

