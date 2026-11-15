-- V3: accounts, consent records, profile/goal and planning constraints.
-- Stories: US-01 (registration), US-02 (profile/goal), US-03 (exclusions, equipment, time),
-- US-04 (account deletion: ON DELETE CASCADE from app_user), LEG-1/LEG-2/LEG-4, decisions Q5, Q7, Q8.

CREATE TABLE app_user (
    id                 uuid         NOT NULL DEFAULT gen_random_uuid(),
    email              varchar(254) NOT NULL,
    password_hash      varchar(255) NOT NULL,
    adult_confirmed_at timestamptz  NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_user_pk PRIMARY KEY (id),
    CONSTRAINT app_user_email_format CHECK (email = btrim(email) AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    -- Only adaptive hashes (LEG-5): bcrypt ($2a/$2b/$2y, cost 04-31) or Argon2id (PHC string), optionally with a
    -- Spring Security {id} prefix. Plain text, MD5 or SHA-1 digests cannot be stored.
    CONSTRAINT app_user_password_hash_format CHECK (password_hash ~
        '^(\{[A-Za-z0-9@_.]+\})?(\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}|\$argon2id\$v=19\$m=[0-9]+,t=[0-9]+,p=[0-9]+\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+)$'),
    CONSTRAINT app_user_updated_after_created CHECK (updated_at >= created_at)
);
COMMENT ON TABLE app_user IS 'User account (US-01). Deleting a row erases all personal records through ON DELETE CASCADE (US-04, LEG-4).';
COMMENT ON COLUMN app_user.id IS 'Surrogate key (random UUID; not guessable, NFR-08).';
COMMENT ON COLUMN app_user.email IS 'Sign-in e-mail; unique case-insensitively. Personal data - masked in v_app_user_masked.';
COMMENT ON COLUMN app_user.password_hash IS 'Adaptive password hash produced by the application (bcrypt or Argon2id, enforced by CHECK app_user_password_hash_format); never the password, never MD5/SHA-1 (LEG-5).';
COMMENT ON COLUMN app_user.adult_confirmed_at IS 'When the user explicitly confirmed being at least 18; required for registration (Q8, Q13). No date of birth is stored (LEG-1).';
COMMENT ON COLUMN app_user.created_at IS 'Registration time.';
COMMENT ON COLUMN app_user.updated_at IS 'Last modification time (maintained by trigger).';

CREATE UNIQUE INDEX app_user_email_uq ON app_user (lower(email));

CREATE TABLE user_consent (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id             uuid        NOT NULL,
    consent_document_id smallint    NOT NULL,
    granted_at          timestamptz NOT NULL DEFAULT now(),
    withdrawn_at        timestamptz,
    CONSTRAINT user_consent_pk PRIMARY KEY (id),
    CONSTRAINT user_consent_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_consent_document_fk FOREIGN KEY (consent_document_id) REFERENCES consent_document (id),
    CONSTRAINT user_consent_withdrawn_after_granted CHECK (withdrawn_at IS NULL OR withdrawn_at >= granted_at)
);
COMMENT ON TABLE user_consent IS 'Separately recorded, versioned consent / notice acceptance (LEG-2, Q7). Append-only except a single withdrawal.';
COMMENT ON COLUMN user_consent.id IS 'Surrogate key.';
COMMENT ON COLUMN user_consent.user_id IS 'Consenting user (FK app_user, cascade on account deletion).';
COMMENT ON COLUMN user_consent.consent_document_id IS 'Accepted document version (FK consent_document).';
COMMENT ON COLUMN user_consent.granted_at IS 'When consent was given.';
COMMENT ON COLUMN user_consent.withdrawn_at IS 'When consent was withdrawn; NULL while active. Can be set once.';

-- At most one active (not withdrawn) consent per user and document version.
CREATE UNIQUE INDEX user_consent_active_uq ON user_consent (user_id, consent_document_id) WHERE withdrawn_at IS NULL;
CREATE INDEX user_consent_user_idx ON user_consent (user_id);

CREATE TABLE user_profile (
    user_id            uuid          NOT NULL,
    goal_code          varchar(30)   NOT NULL,
    energy_target_kcal integer       NOT NULL,
    protein_target_g   numeric(5, 1) NOT NULL,
    fat_target_g       numeric(5, 1) NOT NULL,
    carbs_target_g     numeric(5, 1) NOT NULL,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT user_profile_pk PRIMARY KEY (user_id),
    CONSTRAINT user_profile_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_profile_goal_fk FOREIGN KEY (goal_code) REFERENCES goal_type (code),
    CONSTRAINT user_profile_energy_range CHECK (energy_target_kcal BETWEEN 500 AND 10000),
    CONSTRAINT user_profile_protein_range CHECK (protein_target_g BETWEEN 0 AND 1000),
    CONSTRAINT user_profile_fat_range CHECK (fat_target_g BETWEEN 0 AND 1000),
    CONSTRAINT user_profile_carbs_range CHECK (carbs_target_g BETWEEN 0 AND 1000),
    CONSTRAINT user_profile_updated_after_created CHECK (updated_at >= created_at)
);
COMMENT ON TABLE user_profile IS 'Profile and goal (US-02), 1:1 with app_user. Targets are stored user goals, not calculated (no BMR/TDEE).';
COMMENT ON COLUMN user_profile.user_id IS 'Owner (PK and FK app_user, cascade on deletion).';
COMMENT ON COLUMN user_profile.goal_code IS 'Selected goal (FK goal_type).';
COMMENT ON COLUMN user_profile.energy_target_kcal IS 'Daily energy target in kcal, entered by the user (technical sanity range 500-10000).';
COMMENT ON COLUMN user_profile.protein_target_g IS 'Daily protein target, grams (US-07 macro comparison, Q3).';
COMMENT ON COLUMN user_profile.fat_target_g IS 'Daily fat target, grams.';
COMMENT ON COLUMN user_profile.carbs_target_g IS 'Daily carbohydrate target, grams.';
COMMENT ON COLUMN user_profile.created_at IS 'Row creation time.';
COMMENT ON COLUMN user_profile.updated_at IS 'Last modification time (maintained by trigger).';

CREATE TABLE profile_food_exclusion (
    user_id         uuid        NOT NULL,
    ingredient_code varchar(30) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT profile_food_exclusion_pk PRIMARY KEY (user_id, ingredient_code),
    CONSTRAINT profile_food_exclusion_profile_fk FOREIGN KEY (user_id) REFERENCES user_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT profile_food_exclusion_ingredient_fk FOREIGN KEY (ingredient_code) REFERENCES ingredient (code)
);
COMMENT ON TABLE profile_food_exclusion IS 'Permanent food exclusions stored in the profile; hard constraints for planning and recovery (US-03, Q5).';
COMMENT ON COLUMN profile_food_exclusion.user_id IS 'Profile owner (FK user_profile).';
COMMENT ON COLUMN profile_food_exclusion.ingredient_code IS 'Excluded ingredient (FK ingredient).';
COMMENT ON COLUMN profile_food_exclusion.created_at IS 'When the exclusion was added.';

CREATE TABLE daily_constraint (
    id                 uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id            uuid        NOT NULL,
    constraint_date    date        NOT NULL,
    available_time_min smallint,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT daily_constraint_pk PRIMARY KEY (id),
    CONSTRAINT daily_constraint_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT daily_constraint_time_range CHECK (available_time_min IS NULL OR available_time_min BETWEEN 0 AND 1440),
    CONSTRAINT daily_constraint_user_date_uq UNIQUE (user_id, constraint_date),
    CONSTRAINT daily_constraint_updated_after_created CHECK (updated_at >= created_at)
);
COMMENT ON TABLE daily_constraint IS 'Planning constraints for one user and date (Fig. B.11 PlanningConstraint; US-03, US-15, Q5).';
COMMENT ON COLUMN daily_constraint.id IS 'Surrogate key.';
COMMENT ON COLUMN daily_constraint.user_id IS 'Owner (FK app_user, cascade).';
COMMENT ON COLUMN daily_constraint.constraint_date IS 'Date the constraints apply to; one row per user and date.';
COMMENT ON COLUMN daily_constraint.available_time_min IS 'Minutes available for remaining activity/meal preparation that day; NULL = not restricted.';
COMMENT ON COLUMN daily_constraint.created_at IS 'Row creation time.';
COMMENT ON COLUMN daily_constraint.updated_at IS 'Last modification time (maintained by trigger).';

CREATE TABLE daily_constraint_ingredient (
    daily_constraint_id uuid        NOT NULL,
    ingredient_code     varchar(30) NOT NULL,
    availability        varchar(10) NOT NULL,
    CONSTRAINT daily_constraint_ingredient_pk PRIMARY KEY (daily_constraint_id, ingredient_code),
    CONSTRAINT daily_constraint_ingredient_constraint_fk FOREIGN KEY (daily_constraint_id) REFERENCES daily_constraint (id) ON DELETE CASCADE,
    CONSTRAINT daily_constraint_ingredient_ingredient_fk FOREIGN KEY (ingredient_code) REFERENCES ingredient (code),
    CONSTRAINT daily_constraint_ingredient_availability_check CHECK (availability IN ('AVAILABLE', 'EXCLUDED'))
);
COMMENT ON TABLE daily_constraint_ingredient IS 'Per-day ingredient availability: available products (Q3) and temporary exclusions (Q5).';
COMMENT ON COLUMN daily_constraint_ingredient.daily_constraint_id IS 'Daily constraint (FK daily_constraint, cascade).';
COMMENT ON COLUMN daily_constraint_ingredient.ingredient_code IS 'Ingredient (FK ingredient); at most one status per day.';
COMMENT ON COLUMN daily_constraint_ingredient.availability IS 'AVAILABLE = the user has it today; EXCLUDED = temporarily excluded/unavailable today.';

CREATE TABLE daily_constraint_equipment (
    daily_constraint_id uuid        NOT NULL,
    equipment_code      varchar(30) NOT NULL,
    CONSTRAINT daily_constraint_equipment_pk PRIMARY KEY (daily_constraint_id, equipment_code),
    CONSTRAINT daily_constraint_equipment_constraint_fk FOREIGN KEY (daily_constraint_id) REFERENCES daily_constraint (id) ON DELETE CASCADE,
    CONSTRAINT daily_constraint_equipment_equipment_fk FOREIGN KEY (equipment_code) REFERENCES equipment (code)
);
COMMENT ON TABLE daily_constraint_equipment IS 'Equipment available on that day (US-03, US-14). No rows = no equipment available.';
COMMENT ON COLUMN daily_constraint_equipment.daily_constraint_id IS 'Daily constraint (FK daily_constraint, cascade).';
COMMENT ON COLUMN daily_constraint_equipment.equipment_code IS 'Available equipment (FK equipment).';
