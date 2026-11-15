-- V6: Dynamic Plan Recovery records (EPIC 4: US-15..US-19, UC-01, Fig. B.4, Fig. B.11).
-- Proposal status is NOT stored: it is derived from the decision and the plan version
-- (view v_recovery_proposal_overview), so it can never contradict them. "old_value" of Fig. B.11 is
-- the referenced target plan item and is not duplicated.

CREATE TABLE deviation_event (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    daily_plan_id   uuid          NOT NULL,
    plan_item_id    uuid,
    deviation_type  varchar(25)   NOT NULL,
    kcal_difference numeric(7, 1),
    details         varchar(500),
    detected_at     timestamptz   NOT NULL DEFAULT now(),
    resolved_at     timestamptz,
    CONSTRAINT deviation_event_pk PRIMARY KEY (id),
    CONSTRAINT deviation_event_plan_fk FOREIGN KEY (daily_plan_id) REFERENCES daily_plan (id) ON DELETE CASCADE,
    CONSTRAINT deviation_event_item_fk FOREIGN KEY (plan_item_id) REFERENCES plan_item (id) ON DELETE CASCADE,
    CONSTRAINT deviation_event_type_check CHECK (deviation_type IN
        ('KCAL_DIFFERENCE', 'ITEM_SKIPPED', 'ITEM_MISSED', 'CONSTRAINT_INFEASIBLE')),
    CONSTRAINT deviation_event_kcal_only_for_kcal CHECK ((deviation_type = 'KCAL_DIFFERENCE') = (kcal_difference IS NOT NULL)),
    CONSTRAINT deviation_event_item_required CHECK (deviation_type = 'CONSTRAINT_INFEASIBLE' OR plan_item_id IS NOT NULL),
    CONSTRAINT deviation_event_details_for_constraint CHECK (deviation_type <> 'CONSTRAINT_INFEASIBLE' OR btrim(coalesce(details, '')) <> ''),
    CONSTRAINT deviation_event_resolved_after_detected CHECK (resolved_at IS NULL OR resolved_at >= detected_at)
);
COMMENT ON TABLE deviation_event IS 'Significant deviation that set recoveryRequired (US-15): kcal difference >= threshold, skipped/missed item, or a constraint making a future item infeasible.';
COMMENT ON COLUMN deviation_event.id IS 'Surrogate key.';
COMMENT ON COLUMN deviation_event.daily_plan_id IS 'Affected plan (FK daily_plan, cascade).';
COMMENT ON COLUMN deviation_event.plan_item_id IS 'Item that deviated; NULL only for CONSTRAINT_INFEASIBLE events not tied to one item.';
COMMENT ON COLUMN deviation_event.deviation_type IS 'KCAL_DIFFERENCE, ITEM_SKIPPED, ITEM_MISSED or CONSTRAINT_INFEASIBLE.';
COMMENT ON COLUMN deviation_event.kcal_difference IS 'Actual minus planned kcal; only for KCAL_DIFFERENCE.';
COMMENT ON COLUMN deviation_event.details IS 'Human-readable trigger description; required for CONSTRAINT_INFEASIBLE.';
COMMENT ON COLUMN deviation_event.detected_at IS 'When the deviation was detected.';
COMMENT ON COLUMN deviation_event.resolved_at IS 'When an accepted/edited recovery resolved it; NULL while open. Set once.';

CREATE INDEX deviation_event_plan_idx ON deviation_event (daily_plan_id, detected_at);
CREATE INDEX deviation_event_plan_item_idx ON deviation_event (plan_item_id) WHERE plan_item_id IS NOT NULL;
-- One open event per item and type (repeated detection does not duplicate it).
CREATE UNIQUE INDEX deviation_event_open_item_uq ON deviation_event (plan_item_id, deviation_type)
    WHERE resolved_at IS NULL AND plan_item_id IS NOT NULL;

CREATE TABLE recovery_proposal (
    id              uuid        NOT NULL DEFAULT gen_random_uuid(),
    daily_plan_id   uuid        NOT NULL,
    base_version_no integer     NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT recovery_proposal_pk PRIMARY KEY (id),
    CONSTRAINT recovery_proposal_plan_fk FOREIGN KEY (daily_plan_id) REFERENCES daily_plan (id) ON DELETE CASCADE,
    CONSTRAINT recovery_proposal_base_version_positive CHECK (base_version_no >= 1)
);
COMMENT ON TABLE recovery_proposal IS 'Recovery proposal for the remaining day (US-16). Immutable; status derived in v_recovery_proposal_overview.';
COMMENT ON COLUMN recovery_proposal.id IS 'Surrogate key (proposalId in the API).';
COMMENT ON COLUMN recovery_proposal.daily_plan_id IS 'Plan being recovered (FK daily_plan, cascade).';
COMMENT ON COLUMN recovery_proposal.base_version_no IS 'Plan version the proposal was computed from (baseVersion, US-18); must equal the current version when created.';
COMMENT ON COLUMN recovery_proposal.created_at IS 'When the proposal was generated.';

CREATE INDEX recovery_proposal_plan_idx ON recovery_proposal (daily_plan_id, created_at);

CREATE TABLE recovery_change (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),
    proposal_id       uuid          NOT NULL,
    change_no         smallint      NOT NULL,
    change_type       varchar(10)   NOT NULL,
    target_item_id    uuid,
    item_type         varchar(10),
    meal_slot_code    varchar(20),
    planned_time      time,
    meal_id           bigint,
    exercise_id       bigint,
    planned_kcal      numeric(7, 1),
    planned_protein_g numeric(6, 1),
    planned_fat_g     numeric(6, 1),
    planned_carbs_g   numeric(6, 1),
    duration_min      smallint,
    reason_category   varchar(12)   NOT NULL,
    reason_text       varchar(500)  NOT NULL,
    CONSTRAINT recovery_change_pk PRIMARY KEY (id),
    CONSTRAINT recovery_change_proposal_fk FOREIGN KEY (proposal_id) REFERENCES recovery_proposal (id) ON DELETE CASCADE,
    CONSTRAINT recovery_change_target_fk FOREIGN KEY (target_item_id) REFERENCES plan_item (id) ON DELETE CASCADE,
    CONSTRAINT recovery_change_meal_slot_fk FOREIGN KEY (meal_slot_code) REFERENCES meal_slot (code),
    CONSTRAINT recovery_change_meal_fk FOREIGN KEY (meal_id) REFERENCES meal (id),
    CONSTRAINT recovery_change_exercise_fk FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    CONSTRAINT recovery_change_no_uq UNIQUE (proposal_id, change_no),
    CONSTRAINT recovery_change_no_positive CHECK (change_no >= 1),
    CONSTRAINT recovery_change_type_check CHECK (change_type IN ('REPLACE', 'REMOVE', 'ADD')),
    CONSTRAINT recovery_change_target_by_type CHECK ((change_type = 'ADD') = (target_item_id IS NULL)),
    CONSTRAINT recovery_change_remove_has_no_item CHECK (change_type <> 'REMOVE' OR (
        item_type IS NULL AND meal_slot_code IS NULL AND planned_time IS NULL AND meal_id IS NULL
        AND exercise_id IS NULL AND planned_kcal IS NULL AND planned_protein_g IS NULL
        AND planned_fat_g IS NULL AND planned_carbs_g IS NULL AND duration_min IS NULL)),
    CONSTRAINT recovery_change_item_type_check CHECK (
        (change_type = 'REMOVE' AND item_type IS NULL)
        OR (change_type <> 'REMOVE' AND item_type IS NOT NULL AND item_type IN ('MEAL', 'WORKOUT'))),
    CONSTRAINT recovery_change_meal_shape CHECK (item_type IS DISTINCT FROM 'MEAL' OR (
        meal_slot_code IS NOT NULL AND meal_id IS NOT NULL AND exercise_id IS NULL AND duration_min IS NULL
        AND planned_kcal IS NOT NULL AND planned_protein_g IS NOT NULL
        AND planned_fat_g IS NOT NULL AND planned_carbs_g IS NOT NULL)),
    CONSTRAINT recovery_change_workout_shape CHECK (item_type IS DISTINCT FROM 'WORKOUT' OR (
        exercise_id IS NOT NULL AND duration_min IS NOT NULL AND meal_slot_code IS NULL AND meal_id IS NULL
        AND planned_kcal IS NULL AND planned_protein_g IS NULL
        AND planned_fat_g IS NULL AND planned_carbs_g IS NULL)),
    CONSTRAINT recovery_change_kcal_range CHECK (planned_kcal IS NULL OR planned_kcal BETWEEN 0 AND 10000),
    CONSTRAINT recovery_change_protein_range CHECK (planned_protein_g IS NULL OR planned_protein_g BETWEEN 0 AND 1000),
    CONSTRAINT recovery_change_fat_range CHECK (planned_fat_g IS NULL OR planned_fat_g BETWEEN 0 AND 1000),
    CONSTRAINT recovery_change_carbs_range CHECK (planned_carbs_g IS NULL OR planned_carbs_g BETWEEN 0 AND 1000),
    CONSTRAINT recovery_change_duration_range CHECK (duration_min IS NULL OR duration_min BETWEEN 1 AND 600),
    CONSTRAINT recovery_change_reason_category_check CHECK (reason_category IN
        ('NUTRITION', 'TIME', 'INGREDIENT', 'EQUIPMENT', 'OTHER')),
    CONSTRAINT recovery_change_reason_not_blank CHECK (btrim(reason_text) <> '')
);
COMMENT ON TABLE recovery_change IS 'One changed item of a proposal with its human-readable reason (Fig. B.11 RecoveryChange; US-17).';
COMMENT ON COLUMN recovery_change.id IS 'Surrogate key.';
COMMENT ON COLUMN recovery_change.proposal_id IS 'Proposal (FK recovery_proposal, cascade).';
COMMENT ON COLUMN recovery_change.change_no IS 'Order of the change within the proposal (>= 1).';
COMMENT ON COLUMN recovery_change.change_type IS 'REPLACE a planned item, REMOVE it, or ADD a new item.';
COMMENT ON COLUMN recovery_change.target_item_id IS 'Planned item of the base version being replaced/removed (old value); NULL for ADD. Must be PLANNED - completed items are frozen (US-16).';
COMMENT ON COLUMN recovery_change.item_type IS 'New item type (MEAL/WORKOUT) for REPLACE and ADD; NULL for REMOVE. REPLACE keeps the target''s type.';
COMMENT ON COLUMN recovery_change.meal_slot_code IS 'New MEAL slot (FK meal_slot).';
COMMENT ON COLUMN recovery_change.planned_time IS 'New planned time.';
COMMENT ON COLUMN recovery_change.meal_id IS 'New MEAL catalogue meal (FK meal).';
COMMENT ON COLUMN recovery_change.exercise_id IS 'New WORKOUT exercise (FK exercise).';
COMMENT ON COLUMN recovery_change.planned_kcal IS 'New MEAL planned kcal.';
COMMENT ON COLUMN recovery_change.planned_protein_g IS 'New MEAL planned protein, grams.';
COMMENT ON COLUMN recovery_change.planned_fat_g IS 'New MEAL planned fat, grams.';
COMMENT ON COLUMN recovery_change.planned_carbs_g IS 'New MEAL planned carbohydrates, grams.';
COMMENT ON COLUMN recovery_change.duration_min IS 'New WORKOUT duration, minutes.';
COMMENT ON COLUMN recovery_change.reason_category IS 'Constraint the reason is tied to: NUTRITION, TIME, INGREDIENT, EQUIPMENT or OTHER (US-17).';
COMMENT ON COLUMN recovery_change.reason_text IS 'Human-readable reason shown to the user (US-17).';

CREATE INDEX recovery_change_target_idx ON recovery_change (target_item_id) WHERE target_item_id IS NOT NULL;
CREATE UNIQUE INDEX recovery_change_target_uq ON recovery_change (proposal_id, target_item_id) WHERE target_item_id IS NOT NULL;

CREATE TABLE recovery_decision (
    id                   uuid        NOT NULL DEFAULT gen_random_uuid(),
    proposal_id          uuid        NOT NULL,
    decision             varchar(10) NOT NULL,
    resulting_version_id uuid,
    decided_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT recovery_decision_pk PRIMARY KEY (id),
    CONSTRAINT recovery_decision_proposal_fk FOREIGN KEY (proposal_id) REFERENCES recovery_proposal (id) ON DELETE CASCADE,
    CONSTRAINT recovery_decision_version_fk FOREIGN KEY (resulting_version_id) REFERENCES plan_version (id) ON DELETE CASCADE,
    CONSTRAINT recovery_decision_proposal_uq UNIQUE (proposal_id),
    CONSTRAINT recovery_decision_version_uq UNIQUE (resulting_version_id),
    CONSTRAINT recovery_decision_check CHECK (decision IN ('ACCEPT', 'EDIT', 'REJECT')),
    CONSTRAINT recovery_decision_version_by_decision CHECK ((decision = 'REJECT') = (resulting_version_id IS NULL))
);
COMMENT ON TABLE recovery_decision IS 'User decision on a proposal (US-18), at most one per proposal. Written only by apply_recovery_decision.';
COMMENT ON COLUMN recovery_decision.id IS 'Surrogate key.';
COMMENT ON COLUMN recovery_decision.proposal_id IS 'Decided proposal (FK recovery_proposal, unique: 1 : 0..1).';
COMMENT ON COLUMN recovery_decision.decision IS 'ACCEPT, EDIT or REJECT.';
COMMENT ON COLUMN recovery_decision.resulting_version_id IS 'Plan version created by ACCEPT/EDIT; NULL for REJECT (active plan unchanged).';
COMMENT ON COLUMN recovery_decision.decided_at IS 'When the decision was confirmed.';
