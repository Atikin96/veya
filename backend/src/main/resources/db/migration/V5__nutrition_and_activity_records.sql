-- V5: confirmed actual records (diary) and saved meal templates.
-- Stories: US-06 (log/correct food), US-07 (totals), US-08 (saved/recent meals, <= 3 actions),
-- US-13 (workout completed/skipped/changed). Actual records stay distinct from planned items.
--
-- Nutrition values in food_entry / saved_meal_item are the values the user confirmed (US-06:
-- "persisted unchanged"). They are facts of the entry, not derived from the catalogue item, because the
-- user may correct them and the catalogue may change later.

CREATE TABLE saved_meal (
    id         uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL,
    name       varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT saved_meal_pk PRIMARY KEY (id),
    CONSTRAINT saved_meal_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT saved_meal_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT saved_meal_user_name_uq UNIQUE (user_id, name)
);
COMMENT ON TABLE saved_meal IS 'User-defined meal template for quick re-logging (US-08). Reusing it never alters the template.';
COMMENT ON COLUMN saved_meal.id IS 'Surrogate key.';
COMMENT ON COLUMN saved_meal.user_id IS 'Owner (FK app_user, cascade).';
COMMENT ON COLUMN saved_meal.name IS 'Template name; unique per user.';
COMMENT ON COLUMN saved_meal.created_at IS 'Row creation time.';

CREATE TABLE saved_meal_item (
    saved_meal_id uuid          NOT NULL,
    line_no       smallint      NOT NULL,
    food_item_id  bigint,
    food_name     varchar(200)  NOT NULL,
    portion_g     numeric(7, 1) NOT NULL,
    kcal          numeric(7, 1) NOT NULL,
    protein_g     numeric(6, 1) NOT NULL,
    fat_g         numeric(6, 1) NOT NULL,
    carbs_g       numeric(6, 1) NOT NULL,
    CONSTRAINT saved_meal_item_pk PRIMARY KEY (saved_meal_id, line_no),
    CONSTRAINT saved_meal_item_meal_fk FOREIGN KEY (saved_meal_id) REFERENCES saved_meal (id) ON DELETE CASCADE,
    CONSTRAINT saved_meal_item_food_fk FOREIGN KEY (food_item_id) REFERENCES food_item (id) ON DELETE SET NULL,
    CONSTRAINT saved_meal_item_line_positive CHECK (line_no >= 1),
    CONSTRAINT saved_meal_item_name_not_blank CHECK (btrim(food_name) <> ''),
    CONSTRAINT saved_meal_item_portion_range CHECK (portion_g > 0 AND portion_g <= 5000),
    CONSTRAINT saved_meal_item_kcal_range CHECK (kcal BETWEEN 0 AND 10000),
    CONSTRAINT saved_meal_item_protein_range CHECK (protein_g BETWEEN 0 AND 1000),
    CONSTRAINT saved_meal_item_fat_range CHECK (fat_g BETWEEN 0 AND 1000),
    CONSTRAINT saved_meal_item_carbs_range CHECK (carbs_g BETWEEN 0 AND 1000)
);
COMMENT ON TABLE saved_meal_item IS 'Lines of a saved meal template with the confirmed values to copy into the diary.';
COMMENT ON COLUMN saved_meal_item.saved_meal_id IS 'Template (FK saved_meal, cascade).';
COMMENT ON COLUMN saved_meal_item.line_no IS 'Line number within the template (>= 1).';
COMMENT ON COLUMN saved_meal_item.food_item_id IS 'Catalogue item the line came from; NULL for manual lines (FK food_item, set NULL if the item is removed).';
COMMENT ON COLUMN saved_meal_item.food_name IS 'Food name as confirmed by the user.';
COMMENT ON COLUMN saved_meal_item.portion_g IS 'Portion, grams (0 < portion <= 5000).';
COMMENT ON COLUMN saved_meal_item.kcal IS 'Energy of the portion, kcal.';
COMMENT ON COLUMN saved_meal_item.protein_g IS 'Protein of the portion, grams.';
COMMENT ON COLUMN saved_meal_item.fat_g IS 'Fat of the portion, grams.';
COMMENT ON COLUMN saved_meal_item.carbs_g IS 'Carbohydrates of the portion, grams.';

CREATE INDEX saved_meal_item_food_item_idx ON saved_meal_item (food_item_id) WHERE food_item_id IS NOT NULL;

CREATE TABLE food_entry (
    id             uuid          NOT NULL DEFAULT gen_random_uuid(),
    user_id        uuid          NOT NULL,
    entry_date     date          NOT NULL,
    meal_slot_code varchar(20)   NOT NULL,
    input_source   varchar(12)   NOT NULL,
    food_item_id   bigint,
    saved_meal_id  uuid,
    plan_item_id   uuid,
    food_name      varchar(200)  NOT NULL,
    portion_g      numeric(7, 1) NOT NULL,
    kcal           numeric(7, 1) NOT NULL,
    protein_g      numeric(6, 1) NOT NULL,
    fat_g          numeric(6, 1) NOT NULL,
    carbs_g        numeric(6, 1) NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT food_entry_pk PRIMARY KEY (id),
    CONSTRAINT food_entry_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT food_entry_meal_slot_fk FOREIGN KEY (meal_slot_code) REFERENCES meal_slot (code),
    CONSTRAINT food_entry_food_item_fk FOREIGN KEY (food_item_id) REFERENCES food_item (id),
    CONSTRAINT food_entry_saved_meal_fk FOREIGN KEY (saved_meal_id) REFERENCES saved_meal (id) ON DELETE SET NULL,
    CONSTRAINT food_entry_plan_item_fk FOREIGN KEY (plan_item_id) REFERENCES plan_item (id) ON DELETE SET NULL,
    CONSTRAINT food_entry_source_check CHECK (input_source IN ('CATALOG', 'MANUAL', 'SAVED_MEAL')),
    CONSTRAINT food_entry_catalog_needs_item CHECK (input_source <> 'CATALOG' OR food_item_id IS NOT NULL),
    CONSTRAINT food_entry_name_not_blank CHECK (btrim(food_name) <> ''),
    CONSTRAINT food_entry_portion_range CHECK (portion_g > 0 AND portion_g <= 5000),
    CONSTRAINT food_entry_kcal_range CHECK (kcal BETWEEN 0 AND 10000),
    CONSTRAINT food_entry_protein_range CHECK (protein_g BETWEEN 0 AND 1000),
    CONSTRAINT food_entry_fat_range CHECK (fat_g BETWEEN 0 AND 1000),
    CONSTRAINT food_entry_carbs_range CHECK (carbs_g BETWEEN 0 AND 1000),
    CONSTRAINT food_entry_updated_after_created CHECK (updated_at >= created_at)
);
COMMENT ON TABLE food_entry IS 'Confirmed food diary entry (Fig. B.11 FoodEntry; US-06). Only user-confirmed values are stored.';
COMMENT ON COLUMN food_entry.id IS 'Surrogate key.';
COMMENT ON COLUMN food_entry.user_id IS 'Owner (FK app_user, cascade).';
COMMENT ON COLUMN food_entry.entry_date IS 'Date the food was eaten.';
COMMENT ON COLUMN food_entry.meal_slot_code IS 'Meal slot (FK meal_slot).';
COMMENT ON COLUMN food_entry.input_source IS 'How the entry was created: CATALOG (search), MANUAL or SAVED_MEAL (US-05, US-06, US-08). Photo input is post-MVP (US-27/28).';
COMMENT ON COLUMN food_entry.food_item_id IS 'Catalogue item used; required for CATALOG entries (FK food_item, deletion restricted).';
COMMENT ON COLUMN food_entry.saved_meal_id IS 'Template the entry was created from (FK saved_meal, set NULL if the template is deleted).';
COMMENT ON COLUMN food_entry.plan_item_id IS 'Planned MEAL item this entry fulfils; must be in the active version of the same user and date (trigger). Set NULL if the plan is erased - actual records are kept.';
COMMENT ON COLUMN food_entry.food_name IS 'Food name as confirmed by the user.';
COMMENT ON COLUMN food_entry.portion_g IS 'Eaten portion, grams.';
COMMENT ON COLUMN food_entry.kcal IS 'Confirmed energy of the portion, kcal.';
COMMENT ON COLUMN food_entry.protein_g IS 'Confirmed protein, grams.';
COMMENT ON COLUMN food_entry.fat_g IS 'Confirmed fat, grams.';
COMMENT ON COLUMN food_entry.carbs_g IS 'Confirmed carbohydrates, grams.';
COMMENT ON COLUMN food_entry.created_at IS 'When the entry was recorded.';
COMMENT ON COLUMN food_entry.updated_at IS 'Last correction time (maintained by trigger).';

CREATE INDEX food_entry_user_date_idx ON food_entry (user_id, entry_date);
CREATE INDEX food_entry_user_recent_idx ON food_entry (user_id, created_at DESC);
CREATE INDEX food_entry_plan_item_idx ON food_entry (plan_item_id) WHERE plan_item_id IS NOT NULL;
CREATE INDEX food_entry_food_item_idx ON food_entry (food_item_id) WHERE food_item_id IS NOT NULL;
CREATE INDEX food_entry_saved_meal_idx ON food_entry (saved_meal_id) WHERE saved_meal_id IS NOT NULL;

CREATE TABLE activity_entry (
    id           uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL,
    entry_date   date        NOT NULL,
    exercise_id  bigint      NOT NULL,
    plan_item_id uuid,
    status       varchar(10) NOT NULL,
    duration_min smallint,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT activity_entry_pk PRIMARY KEY (id),
    CONSTRAINT activity_entry_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT activity_entry_exercise_fk FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    CONSTRAINT activity_entry_plan_item_fk FOREIGN KEY (plan_item_id) REFERENCES plan_item (id) ON DELETE SET NULL,
    CONSTRAINT activity_entry_plan_item_uq UNIQUE (plan_item_id),
    CONSTRAINT activity_entry_status_check CHECK (status IN ('COMPLETED', 'SKIPPED', 'CHANGED')),
    CONSTRAINT activity_entry_duration_by_status CHECK (
        (status = 'SKIPPED' AND duration_min IS NULL)
        OR (status <> 'SKIPPED' AND duration_min IS NOT NULL AND duration_min BETWEEN 1 AND 600)),
    CONSTRAINT activity_entry_updated_after_created CHECK (updated_at >= created_at)
);
COMMENT ON TABLE activity_entry IS 'Actual workout event (Fig. B.11 ActivityEntry; US-13). Recording it for a planned item sets that item''s status.';
COMMENT ON COLUMN activity_entry.id IS 'Surrogate key.';
COMMENT ON COLUMN activity_entry.user_id IS 'Owner (FK app_user, cascade).';
COMMENT ON COLUMN activity_entry.entry_date IS 'Date of the workout.';
COMMENT ON COLUMN activity_entry.exercise_id IS 'Exercise actually performed or planned-and-skipped (FK exercise).';
COMMENT ON COLUMN activity_entry.plan_item_id IS 'Planned WORKOUT item this event refers to; at most one event per planned item. Set NULL if the plan is erased.';
COMMENT ON COLUMN activity_entry.status IS 'COMPLETED, SKIPPED or CHANGED (performed differently from the plan).';
COMMENT ON COLUMN activity_entry.duration_min IS 'Actual duration in minutes; NULL exactly when SKIPPED.';
COMMENT ON COLUMN activity_entry.created_at IS 'When the event was recorded.';
COMMENT ON COLUMN activity_entry.updated_at IS 'Last correction time (maintained by trigger).';

CREATE INDEX activity_entry_user_date_idx ON activity_entry (user_id, entry_date);
CREATE INDEX activity_entry_exercise_idx ON activity_entry (exercise_id);
