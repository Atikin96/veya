-- V4: daily plans with immutable versions (Q2).
-- Stories: US-09 (daily meal plan), US-10/US-14 (replacement creates the next version), US-12 (workouts),
-- US-13 (item status), US-15 (recoveryRequired), US-18 (baseVersion), US-20 (active version), NFR-09/NFR-10.
--
-- A daily_plan identifies "the plan of user U for date D". Its content lives in plan_version rows;
-- every change creates version n+1 and earlier versions are never modified. daily_plan.current_version_no
-- is the active version and the optimistic-locking value compared with a proposal's baseVersion.

CREATE TABLE daily_plan (
    id                 uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id            uuid        NOT NULL,
    plan_date          date        NOT NULL,
    current_version_no integer     NOT NULL DEFAULT 0,
    recovery_required  boolean     NOT NULL DEFAULT false,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT daily_plan_pk PRIMARY KEY (id),
    CONSTRAINT daily_plan_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT daily_plan_user_date_uq UNIQUE (user_id, plan_date),
    CONSTRAINT daily_plan_version_non_negative CHECK (current_version_no >= 0),
    CONSTRAINT daily_plan_recovery_needs_version CHECK (NOT recovery_required OR current_version_no >= 1),
    CONSTRAINT daily_plan_updated_after_created CHECK (updated_at >= created_at)
);
COMMENT ON TABLE daily_plan IS 'Plan of one user for one date (Fig. B.11 DailyPlan). Content is versioned in plan_version.';
COMMENT ON COLUMN daily_plan.id IS 'Surrogate key (planId in the API).';
COMMENT ON COLUMN daily_plan.user_id IS 'Owner (FK app_user, cascade).';
COMMENT ON COLUMN daily_plan.plan_date IS 'Planned date; one plan per user and date.';
COMMENT ON COLUMN daily_plan.current_version_no IS 'Active version number (0 = no version yet). Maintained only by triggers; compared with baseVersion (US-18, NFR-10).';
COMMENT ON COLUMN daily_plan.recovery_required IS 'TRUE after a significant deviation (US-15); cleared when a recovery proposal is accepted/edited.';
COMMENT ON COLUMN daily_plan.created_at IS 'Row creation time.';
COMMENT ON COLUMN daily_plan.updated_at IS 'Last modification time (maintained by trigger).';

CREATE INDEX daily_plan_recovery_required_idx ON daily_plan (user_id, plan_date) WHERE recovery_required;

CREATE TABLE plan_version (
    id            uuid        NOT NULL DEFAULT gen_random_uuid(),
    daily_plan_id uuid        NOT NULL,
    version_no    integer     NOT NULL,
    change_reason varchar(30) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT plan_version_pk PRIMARY KEY (id),
    CONSTRAINT plan_version_plan_fk FOREIGN KEY (daily_plan_id) REFERENCES daily_plan (id) ON DELETE CASCADE,
    CONSTRAINT plan_version_plan_no_uq UNIQUE (daily_plan_id, version_no),
    CONSTRAINT plan_version_no_positive CHECK (version_no >= 1),
    CONSTRAINT plan_version_reason_check CHECK (change_reason IN
        ('INITIAL_PLAN', 'MEAL_REPLACEMENT', 'EXERCISE_REPLACEMENT', 'RECOVERY_ACCEPTED', 'RECOVERY_EDITED')),
    CONSTRAINT plan_version_initial_is_first CHECK ((version_no = 1) = (change_reason = 'INITIAL_PLAN'))
);
COMMENT ON TABLE plan_version IS 'Immutable version of a daily plan (Q2). Created only through create_plan_version / apply_recovery_decision.';
COMMENT ON COLUMN plan_version.id IS 'Surrogate key.';
COMMENT ON COLUMN plan_version.daily_plan_id IS 'Plan this version belongs to (FK daily_plan, cascade).';
COMMENT ON COLUMN plan_version.version_no IS 'Sequential number 1, 2, ... without gaps (enforced by trigger).';
COMMENT ON COLUMN plan_version.change_reason IS 'Why the version was created: INITIAL_PLAN, MEAL_/EXERCISE_REPLACEMENT (US-10/US-14) or RECOVERY_ACCEPTED/EDITED (US-18).';
COMMENT ON COLUMN plan_version.created_at IS 'When the version was created.';

CREATE TABLE plan_item (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),
    plan_version_id   uuid          NOT NULL,
    item_key          uuid          NOT NULL,
    item_type         varchar(10)   NOT NULL,
    meal_slot_code    varchar(20),
    planned_time      time,
    meal_id           bigint,
    exercise_id       bigint,
    planned_kcal      numeric(7, 1),
    planned_protein_g numeric(6, 1),
    planned_fat_g     numeric(6, 1),
    planned_carbs_g   numeric(6, 1),
    duration_min      smallint,
    status            varchar(10)   NOT NULL DEFAULT 'PLANNED',
    CONSTRAINT plan_item_pk PRIMARY KEY (id),
    CONSTRAINT plan_item_version_fk FOREIGN KEY (plan_version_id) REFERENCES plan_version (id) ON DELETE CASCADE,
    CONSTRAINT plan_item_meal_slot_fk FOREIGN KEY (meal_slot_code) REFERENCES meal_slot (code),
    CONSTRAINT plan_item_meal_fk FOREIGN KEY (meal_id) REFERENCES meal (id),
    CONSTRAINT plan_item_exercise_fk FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    CONSTRAINT plan_item_version_key_uq UNIQUE (plan_version_id, item_key),
    CONSTRAINT plan_item_type_check CHECK (item_type IN ('MEAL', 'WORKOUT')),
    CONSTRAINT plan_item_status_check CHECK (status IN ('PLANNED', 'COMPLETED', 'SKIPPED', 'MISSED', 'CHANGED')),
    CONSTRAINT plan_item_meal_shape CHECK (item_type <> 'MEAL' OR (
        meal_slot_code IS NOT NULL AND meal_id IS NOT NULL AND exercise_id IS NULL AND duration_min IS NULL
        AND planned_kcal IS NOT NULL AND planned_protein_g IS NOT NULL
        AND planned_fat_g IS NOT NULL AND planned_carbs_g IS NOT NULL)),
    CONSTRAINT plan_item_workout_shape CHECK (item_type <> 'WORKOUT' OR (
        exercise_id IS NOT NULL AND duration_min IS NOT NULL AND meal_slot_code IS NULL AND meal_id IS NULL
        AND planned_kcal IS NULL AND planned_protein_g IS NULL
        AND planned_fat_g IS NULL AND planned_carbs_g IS NULL)),
    CONSTRAINT plan_item_meal_status CHECK (item_type <> 'MEAL' OR status <> 'CHANGED'),
    CONSTRAINT plan_item_kcal_range CHECK (planned_kcal IS NULL OR planned_kcal BETWEEN 0 AND 10000),
    CONSTRAINT plan_item_protein_range CHECK (planned_protein_g IS NULL OR planned_protein_g BETWEEN 0 AND 1000),
    CONSTRAINT plan_item_fat_range CHECK (planned_fat_g IS NULL OR planned_fat_g BETWEEN 0 AND 1000),
    CONSTRAINT plan_item_carbs_range CHECK (planned_carbs_g IS NULL OR planned_carbs_g BETWEEN 0 AND 1000),
    CONSTRAINT plan_item_duration_range CHECK (duration_min IS NULL OR duration_min BETWEEN 1 AND 600)
);
COMMENT ON TABLE plan_item IS 'Planned meal or workout inside one plan version (Fig. B.11 PlanItem). Content is immutable; only status of items in the active version can be recorded.';
COMMENT ON COLUMN plan_item.id IS 'Surrogate key (unique per version).';
COMMENT ON COLUMN plan_item.plan_version_id IS 'Version containing the item (FK plan_version, cascade).';
COMMENT ON COLUMN plan_item.item_key IS 'Logical identity of the item across versions (frozen and replaced items keep their key); unique within a version.';
COMMENT ON COLUMN plan_item.item_type IS 'MEAL or WORKOUT; determines which columns are filled (CHECK plan_item_meal_shape / plan_item_workout_shape).';
COMMENT ON COLUMN plan_item.meal_slot_code IS 'MEAL: slot of the day (FK meal_slot). NULL for workouts.';
COMMENT ON COLUMN plan_item.planned_time IS 'Planned local time of the item; NULL = no fixed time.';
COMMENT ON COLUMN plan_item.meal_id IS 'MEAL: planned catalogue meal (FK meal). NULL for workouts.';
COMMENT ON COLUMN plan_item.exercise_id IS 'WORKOUT: planned exercise (FK exercise). NULL for meals.';
COMMENT ON COLUMN plan_item.planned_kcal IS 'MEAL: planned energy, kcal - snapshot at planning time so history does not change with the catalogue.';
COMMENT ON COLUMN plan_item.planned_protein_g IS 'MEAL: planned protein, grams (snapshot).';
COMMENT ON COLUMN plan_item.planned_fat_g IS 'MEAL: planned fat, grams (snapshot).';
COMMENT ON COLUMN plan_item.planned_carbs_g IS 'MEAL: planned carbohydrates, grams (snapshot).';
COMMENT ON COLUMN plan_item.duration_min IS 'WORKOUT: planned duration in minutes (1-600).';
COMMENT ON COLUMN plan_item.status IS 'PLANNED, COMPLETED, SKIPPED, MISSED or CHANGED (workouts only). Changes only from PLANNED and only in the active version (US-13).';

CREATE INDEX plan_item_meal_idx ON plan_item (meal_id) WHERE meal_id IS NOT NULL;
CREATE INDEX plan_item_exercise_idx ON plan_item (exercise_id) WHERE exercise_id IS NOT NULL;
