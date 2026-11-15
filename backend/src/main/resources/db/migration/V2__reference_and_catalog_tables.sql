-- V2: reference vocabularies and the local food / meal / exercise catalogue.
-- Stories: US-03 (exclusions, equipment), US-05 (food search), US-09/US-10 (candidate meals),
-- US-12/US-14 (exercises), US-15 (deviation threshold), US-31 / R1 (local fallback when providers fail),
-- NFR-02 (bounded candidate set), LEG-2 (versioned consent documents).
-- All objects are created in schema "veya" (Flyway default schema, owned by veya_owner).

-- Trigram matching for case-insensitive substring food search (US-05). Trusted extension (PG13+).
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA veya;

-- ---------------------------------------------------------------------------------------------
-- Reference vocabularies (natural code keys: stable, readable, referenced by code)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE goal_type (
    code varchar(30)  NOT NULL,
    name varchar(100) NOT NULL,
    CONSTRAINT goal_type_pk PRIMARY KEY (code),
    CONSTRAINT goal_type_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT goal_type_name_not_blank CHECK (btrim(name) <> '')
);
COMMENT ON TABLE goal_type IS 'Reference: personal goal categories a profile can select (US-02).';
COMMENT ON COLUMN goal_type.code IS 'Stable goal code, e.g. LOSE_WEIGHT (primary key).';
COMMENT ON COLUMN goal_type.name IS 'Human-readable goal name.';

CREATE TABLE meal_slot (
    code       varchar(20)  NOT NULL,
    name       varchar(50)  NOT NULL,
    sort_order smallint     NOT NULL,
    CONSTRAINT meal_slot_pk PRIMARY KEY (code),
    CONSTRAINT meal_slot_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),
    CONSTRAINT meal_slot_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT meal_slot_sort_order_positive CHECK (sort_order > 0),
    CONSTRAINT meal_slot_sort_order_uq UNIQUE (sort_order)
);
COMMENT ON TABLE meal_slot IS 'Reference: meal slots of a day (breakfast, lunch, ...) used by plans and the diary (US-06, US-09).';
COMMENT ON COLUMN meal_slot.code IS 'Stable slot code, e.g. BREAKFAST (primary key).';
COMMENT ON COLUMN meal_slot.name IS 'Human-readable slot name.';
COMMENT ON COLUMN meal_slot.sort_order IS 'Display order within a day; unique.';

CREATE TABLE equipment (
    code varchar(30)  NOT NULL,
    name varchar(100) NOT NULL,
    CONSTRAINT equipment_pk PRIMARY KEY (code),
    CONSTRAINT equipment_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT equipment_name_not_blank CHECK (btrim(name) <> '')
);
COMMENT ON TABLE equipment IS 'Reference: training equipment that exercises may require and users may have available (US-03, US-14).';
COMMENT ON COLUMN equipment.code IS 'Stable equipment code, e.g. DUMBBELLS (primary key).';
COMMENT ON COLUMN equipment.name IS 'Human-readable equipment name.';

CREATE TABLE ingredient (
    code varchar(30)  NOT NULL,
    name varchar(100) NOT NULL,
    CONSTRAINT ingredient_pk PRIMARY KEY (code),
    CONSTRAINT ingredient_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT ingredient_name_not_blank CHECK (btrim(name) <> '')
);
COMMENT ON TABLE ingredient IS 'Reference: ingredients / product groups used for food exclusions and ingredient availability (US-03, US-10, Q3, Q5).';
COMMENT ON COLUMN ingredient.code IS 'Stable ingredient code, e.g. PEANUT (primary key).';
COMMENT ON COLUMN ingredient.name IS 'Human-readable ingredient name.';

CREATE TABLE recovery_setting (
    id                       boolean  NOT NULL DEFAULT true,
    kcal_deviation_threshold smallint NOT NULL,
    CONSTRAINT recovery_setting_pk PRIMARY KEY (id),
    CONSTRAINT recovery_setting_single_row CHECK (id),
    CONSTRAINT recovery_setting_threshold_range CHECK (kcal_deviation_threshold BETWEEN 1 AND 2000)
);
COMMENT ON TABLE recovery_setting IS 'Single-row configuration of deviation detection (US-15: configurable threshold, default 200 kcal).';
COMMENT ON COLUMN recovery_setting.id IS 'Always TRUE; together with the CHECK it guarantees exactly one configuration row.';
COMMENT ON COLUMN recovery_setting.kcal_deviation_threshold IS 'A confirmed meal differing from its plan by at least this many kcal is a significant deviation (US-15; default 200).';

CREATE TABLE consent_document (
    id           smallint     GENERATED ALWAYS AS IDENTITY,
    consent_type varchar(30)  NOT NULL,
    version      varchar(20)  NOT NULL,
    published_on date         NOT NULL,
    summary      varchar(500) NOT NULL,
    CONSTRAINT consent_document_pk PRIMARY KEY (id),
    CONSTRAINT consent_document_type_check CHECK (consent_type IN ('PRIVACY_NOTICE', 'HEALTH_DATA_CONSENT')),
    CONSTRAINT consent_document_version_format CHECK (version ~ '^[0-9]+\.[0-9]+$'),
    CONSTRAINT consent_document_summary_not_blank CHECK (btrim(summary) <> ''),
    CONSTRAINT consent_document_type_version_uq UNIQUE (consent_type, version)
);
COMMENT ON TABLE consent_document IS 'Versioned privacy notice / health-data consent texts that users accept (LEG-2, Q7). Rows are immutable once published.';
COMMENT ON COLUMN consent_document.id IS 'Surrogate key.';
COMMENT ON COLUMN consent_document.consent_type IS 'PRIVACY_NOTICE (acknowledgement) or HEALTH_DATA_CONSENT (explicit Art. 9(2)(a) consent).';
COMMENT ON COLUMN consent_document.version IS 'Document version, format major.minor; unique per consent type.';
COMMENT ON COLUMN consent_document.published_on IS 'Date the version was published.';
COMMENT ON COLUMN consent_document.summary IS 'Short description of the document version.';

-- ---------------------------------------------------------------------------------------------
-- Local catalogue (cached provider data or locally maintained items; US-05, US-31, R1)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE food_item (
    id               bigint        GENERATED ALWAYS AS IDENTITY,
    name             varchar(200)  NOT NULL,
    source           varchar(20)   NOT NULL,
    external_ref     varchar(100),
    kcal_per_100g    numeric(6, 1) NOT NULL,
    protein_per_100g numeric(5, 1) NOT NULL,
    fat_per_100g     numeric(5, 1) NOT NULL,
    carbs_per_100g   numeric(5, 1) NOT NULL,
    fetched_at       timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT food_item_pk PRIMARY KEY (id),
    CONSTRAINT food_item_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT food_item_source_check CHECK (source IN ('LOCAL', 'PROVIDER')),
    CONSTRAINT food_item_provider_ref CHECK ((source = 'PROVIDER') = (external_ref IS NOT NULL)),
    CONSTRAINT food_item_provider_fetched CHECK ((source = 'PROVIDER') = (fetched_at IS NOT NULL)),
    CONSTRAINT food_item_kcal_range CHECK (kcal_per_100g BETWEEN 0 AND 900),
    CONSTRAINT food_item_protein_range CHECK (protein_per_100g BETWEEN 0 AND 100),
    CONSTRAINT food_item_fat_range CHECK (fat_per_100g BETWEEN 0 AND 100),
    CONSTRAINT food_item_carbs_range CHECK (carbs_per_100g BETWEEN 0 AND 100),
    CONSTRAINT food_item_macros_max_100g CHECK (protein_per_100g + fat_per_100g + carbs_per_100g <= 100),
    CONSTRAINT food_item_source_ref_uq UNIQUE (source, external_ref)
);
COMMENT ON TABLE food_item IS 'Normalized food catalogue entry with nutrition per 100 g (US-05). Values are estimates (LEG-6).';
COMMENT ON COLUMN food_item.id IS 'Surrogate key.';
COMMENT ON COLUMN food_item.name IS 'Display name; unique (case-insensitive) among LOCAL items.';
COMMENT ON COLUMN food_item.source IS 'LOCAL (maintained in VEYA) or PROVIDER (cached from an external food provider, US-31).';
COMMENT ON COLUMN food_item.external_ref IS 'Provider identifier; required for PROVIDER items, NULL for LOCAL items.';
COMMENT ON COLUMN food_item.kcal_per_100g IS 'Energy per 100 g, kcal (0-900).';
COMMENT ON COLUMN food_item.protein_per_100g IS 'Protein per 100 g, grams.';
COMMENT ON COLUMN food_item.fat_per_100g IS 'Fat per 100 g, grams.';
COMMENT ON COLUMN food_item.carbs_per_100g IS 'Carbohydrates per 100 g, grams.';
COMMENT ON COLUMN food_item.fetched_at IS 'When a PROVIDER item was fetched/normalized; NULL for LOCAL items.';
COMMENT ON COLUMN food_item.created_at IS 'Row creation time.';

CREATE UNIQUE INDEX food_item_local_name_uq ON food_item (lower(name)) WHERE source = 'LOCAL';
CREATE INDEX food_item_name_trgm_idx ON food_item USING gin (lower(name) gin_trgm_ops);

CREATE TABLE food_item_ingredient (
    food_item_id    bigint      NOT NULL,
    ingredient_code varchar(30) NOT NULL,
    CONSTRAINT food_item_ingredient_pk PRIMARY KEY (food_item_id, ingredient_code),
    CONSTRAINT food_item_ingredient_food_fk FOREIGN KEY (food_item_id) REFERENCES food_item (id) ON DELETE CASCADE,
    CONSTRAINT food_item_ingredient_ingredient_fk FOREIGN KEY (ingredient_code) REFERENCES ingredient (code)
);
COMMENT ON TABLE food_item_ingredient IS 'Which ingredients a food item contains; used to apply exclusions and availability (US-03, US-10).';
COMMENT ON COLUMN food_item_ingredient.food_item_id IS 'Food item (FK food_item).';
COMMENT ON COLUMN food_item_ingredient.ingredient_code IS 'Contained ingredient (FK ingredient).';

CREATE INDEX food_item_ingredient_ingredient_idx ON food_item_ingredient (ingredient_code);

CREATE TABLE meal (
    id             bigint       GENERATED ALWAYS AS IDENTITY,
    name           varchar(200) NOT NULL,
    meal_slot_code varchar(20),
    prep_time_min  smallint     NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT meal_pk PRIMARY KEY (id),
    CONSTRAINT meal_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT meal_prep_time_range CHECK (prep_time_min BETWEEN 0 AND 600),
    CONSTRAINT meal_slot_fk FOREIGN KEY (meal_slot_code) REFERENCES meal_slot (code)
);
COMMENT ON TABLE meal IS 'Candidate meal (recipe) for meal planning and replacement (US-09, US-10). Nutrition is derived from its components (view v_meal_nutrition).';
COMMENT ON COLUMN meal.id IS 'Surrogate key.';
COMMENT ON COLUMN meal.name IS 'Meal name; unique case-insensitively.';
COMMENT ON COLUMN meal.meal_slot_code IS 'Preferred meal slot (FK meal_slot); NULL = suitable for any slot.';
COMMENT ON COLUMN meal.prep_time_min IS 'Preparation time in minutes; used for time constraints (0-600).';
COMMENT ON COLUMN meal.created_at IS 'Row creation time.';

CREATE UNIQUE INDEX meal_name_uq ON meal (lower(name));

CREATE TABLE meal_component (
    meal_id      bigint        NOT NULL,
    food_item_id bigint        NOT NULL,
    grams        numeric(7, 1) NOT NULL,
    CONSTRAINT meal_component_pk PRIMARY KEY (meal_id, food_item_id),
    CONSTRAINT meal_component_meal_fk FOREIGN KEY (meal_id) REFERENCES meal (id) ON DELETE CASCADE,
    CONSTRAINT meal_component_food_fk FOREIGN KEY (food_item_id) REFERENCES food_item (id),
    CONSTRAINT meal_component_grams_range CHECK (grams > 0 AND grams <= 3000)
);
COMMENT ON TABLE meal_component IS 'Food items and amounts that make up a candidate meal.';
COMMENT ON COLUMN meal_component.meal_id IS 'Meal (FK meal).';
COMMENT ON COLUMN meal_component.food_item_id IS 'Food item used in the meal (FK food_item; deletion restricted while used).';
COMMENT ON COLUMN meal_component.grams IS 'Amount of the food item in grams (0 < grams <= 3000).';

CREATE INDEX meal_component_food_item_idx ON meal_component (food_item_id);

CREATE TABLE exercise (
    id           bigint        GENERATED ALWAYS AS IDENTITY,
    name         varchar(200)  NOT NULL,
    met_value    numeric(4, 1) NOT NULL,
    source       varchar(20)   NOT NULL,
    external_ref varchar(100),
    fetched_at   timestamptz,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT exercise_pk PRIMARY KEY (id),
    CONSTRAINT exercise_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT exercise_met_range CHECK (met_value BETWEEN 1.0 AND 25.0),
    CONSTRAINT exercise_source_check CHECK (source IN ('LOCAL', 'PROVIDER')),
    CONSTRAINT exercise_provider_ref CHECK ((source = 'PROVIDER') = (external_ref IS NOT NULL)),
    CONSTRAINT exercise_provider_fetched CHECK ((source = 'PROVIDER') = (fetched_at IS NOT NULL)),
    CONSTRAINT exercise_source_ref_uq UNIQUE (source, external_ref)
);
COMMENT ON TABLE exercise IS 'Exercise catalogue entry for workout planning and replacement (US-12, US-14). MET values are estimates (LEG-6).';
COMMENT ON COLUMN exercise.id IS 'Surrogate key.';
COMMENT ON COLUMN exercise.name IS 'Exercise name; unique (case-insensitive) among LOCAL items.';
COMMENT ON COLUMN exercise.met_value IS 'Metabolic equivalent (MET) estimate, 1.0-25.0 (2024 Adult Compendium style values).';
COMMENT ON COLUMN exercise.source IS 'LOCAL or PROVIDER (cached from an external exercise provider).';
COMMENT ON COLUMN exercise.external_ref IS 'Provider identifier; required for PROVIDER items.';
COMMENT ON COLUMN exercise.fetched_at IS 'When a PROVIDER item was fetched; NULL for LOCAL items.';
COMMENT ON COLUMN exercise.created_at IS 'Row creation time.';

CREATE UNIQUE INDEX exercise_local_name_uq ON exercise (lower(name)) WHERE source = 'LOCAL';

CREATE TABLE exercise_equipment (
    exercise_id    bigint      NOT NULL,
    equipment_code varchar(30) NOT NULL,
    CONSTRAINT exercise_equipment_pk PRIMARY KEY (exercise_id, equipment_code),
    CONSTRAINT exercise_equipment_exercise_fk FOREIGN KEY (exercise_id) REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT exercise_equipment_equipment_fk FOREIGN KEY (equipment_code) REFERENCES equipment (code)
);
COMMENT ON TABLE exercise_equipment IS 'Equipment required by an exercise; an exercise without rows needs no equipment (US-14).';
COMMENT ON COLUMN exercise_equipment.exercise_id IS 'Exercise (FK exercise).';
COMMENT ON COLUMN exercise_equipment.equipment_code IS 'Required equipment (FK equipment).';

CREATE INDEX exercise_equipment_equipment_idx ON exercise_equipment (equipment_code);
