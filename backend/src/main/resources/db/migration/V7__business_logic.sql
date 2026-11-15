-- V7: database-side business logic (functions, procedure, triggers).
--
-- Only rules that protect data integrity or must be atomic with the write that triggers them live here:
--   * immutability of plan versions, proposals, decisions and consent documents (Q2, US-18, LEG-2);
--   * gap-free plan version numbering and the active-version pointer (NFR-09, NFR-10);
--   * ownership/consistency of links between actual records and planned items;
--   * US-15 rules 1-2: kcal deviation >= threshold, skipped/missed item -> deviation event + recoveryRequired;
--   * atomic plan-version creation and recovery decisions (US-16 frozen items, US-18, NFR-09, NFR-10);
--   * LEG-4 data export (Q6) and masking helpers (LEG-5, LEG-8).
-- Candidate generation, scoring and US-15 rule 3 (constraint infeasibility) stay in the application,
-- which records the result as a CONSTRAINT_INFEASIBLE deviation event.
--
-- Application-facing error codes (SQLSTATE, mapped to HTTP by the API):
--   VY400 invalid request / business rule violated -> 400
--   VY404 resource not found or not owned           -> 404 (ownership is not disclosed)
--   VY409 stale baseVersion / already decided       -> 409
--   VY001 attempt to modify an immutable record     -> 409

-- ---------------------------------------------------------------------------------------------
-- Generic helpers
-- ---------------------------------------------------------------------------------------------

CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;
COMMENT ON FUNCTION set_updated_at() IS 'Trigger: maintains updated_at on every UPDATE.';

CREATE FUNCTION reject_update() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
BEGIN
    RAISE EXCEPTION 'Rows of table % are immutable', TG_TABLE_NAME
        USING ERRCODE = 'VY001', HINT = 'Create a new record instead of changing history.';
END;
$$;
COMMENT ON FUNCTION reject_update() IS 'Trigger: rejects UPDATE on append-only tables (history is never rewritten).';

CREATE FUNCTION is_significant_kcal_deviation(p_planned_kcal numeric, p_actual_kcal numeric) RETURNS boolean
    LANGUAGE sql
    STABLE
    SET search_path = veya, pg_temp
AS $$
    SELECT abs(p_actual_kcal - p_planned_kcal) >= s.kcal_deviation_threshold
    FROM recovery_setting s;
$$;
COMMENT ON FUNCTION is_significant_kcal_deviation(numeric, numeric) IS 'US-15: TRUE when |actual - planned| >= recovery_setting.kcal_deviation_threshold (default 200 kcal).';

-- ---------------------------------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------------------------------

CREATE TRIGGER app_user_set_updated_at BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER user_profile_set_updated_at BEFORE UPDATE ON user_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER daily_constraint_set_updated_at BEFORE UPDATE ON daily_constraint
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER daily_plan_set_updated_at BEFORE UPDATE ON daily_plan
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER food_entry_set_updated_at BEFORE UPDATE ON food_entry
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER activity_entry_set_updated_at BEFORE UPDATE ON activity_entry
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------------------------
-- Append-only tables
-- ---------------------------------------------------------------------------------------------

CREATE TRIGGER consent_document_immutable BEFORE UPDATE ON consent_document
    FOR EACH ROW EXECUTE FUNCTION reject_update();
CREATE TRIGGER plan_version_immutable BEFORE UPDATE ON plan_version
    FOR EACH ROW EXECUTE FUNCTION reject_update();
CREATE TRIGGER recovery_proposal_immutable BEFORE UPDATE ON recovery_proposal
    FOR EACH ROW EXECUTE FUNCTION reject_update();
CREATE TRIGGER recovery_change_immutable BEFORE UPDATE ON recovery_change
    FOR EACH ROW EXECUTE FUNCTION reject_update();
CREATE TRIGGER recovery_decision_immutable BEFORE UPDATE ON recovery_decision
    FOR EACH ROW EXECUTE FUNCTION reject_update();

-- user_consent: only a single withdrawal may be recorded (LEG-2).
CREATE FUNCTION user_consent_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF (to_jsonb(NEW) - 'withdrawn_at') IS DISTINCT FROM (to_jsonb(OLD) - 'withdrawn_at')
       OR OLD.withdrawn_at IS NOT NULL THEN
        RAISE EXCEPTION 'A consent record can only be withdrawn once; other fields are immutable'
            USING ERRCODE = 'VY001';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER user_consent_guard BEFORE UPDATE ON user_consent
    FOR EACH ROW EXECUTE FUNCTION user_consent_guard();

-- ---------------------------------------------------------------------------------------------
-- Plan versioning (Q2, NFR-09, NFR-10)
-- ---------------------------------------------------------------------------------------------

-- daily_plan: owner/date are fixed; the version pointer may only move to an existing next version.
CREATE FUNCTION daily_plan_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF NEW.user_id <> OLD.user_id OR NEW.plan_date <> OLD.plan_date THEN
        RAISE EXCEPTION 'The owner and date of a plan cannot change' USING ERRCODE = 'VY001';
    END IF;
    IF NEW.current_version_no <> OLD.current_version_no THEN
        IF NEW.current_version_no <> OLD.current_version_no + 1
           OR NOT EXISTS (SELECT 1 FROM plan_version
                          WHERE daily_plan_id = NEW.id AND version_no = NEW.current_version_no) THEN
            RAISE EXCEPTION 'current_version_no can only advance to the next existing version'
                USING ERRCODE = 'VY001';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER daily_plan_guard BEFORE UPDATE ON daily_plan
    FOR EACH ROW EXECUTE FUNCTION daily_plan_guard();

-- New version must be exactly current + 1 (serialised by the row lock on daily_plan).
CREATE FUNCTION plan_version_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_current integer;
BEGIN
    SELECT current_version_no INTO v_current
    FROM daily_plan WHERE id = NEW.daily_plan_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Plan % does not exist', NEW.daily_plan_id USING ERRCODE = 'VY404';
    END IF;
    IF NEW.version_no IS DISTINCT FROM v_current + 1 THEN
        RAISE EXCEPTION 'Plan is at version %, cannot create version %', v_current, NEW.version_no
            USING ERRCODE = 'VY409';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER plan_version_before_insert BEFORE INSERT ON plan_version
    FOR EACH ROW EXECUTE FUNCTION plan_version_before_insert();

-- The newly inserted version becomes the active one.
CREATE FUNCTION plan_version_after_insert() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
BEGIN
    UPDATE daily_plan SET current_version_no = NEW.version_no WHERE id = NEW.daily_plan_id;
    RETURN NULL;
END;
$$;
CREATE TRIGGER plan_version_after_insert AFTER INSERT ON plan_version
    FOR EACH ROW EXECUTE FUNCTION plan_version_after_insert();

-- plan_item: content is immutable; items are only added to the active version; status can be recorded
-- once (PLANNED -> other) and only for the active version (US-13). Superseded versions are frozen.
CREATE FUNCTION plan_item_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_is_active boolean;
BEGIN
    SELECT pv.version_no = dp.current_version_no INTO v_is_active
    FROM plan_version pv JOIN daily_plan dp ON dp.id = pv.daily_plan_id
    WHERE pv.id = NEW.plan_version_id;

    IF TG_OP = 'INSERT' THEN
        IF NOT coalesce(v_is_active, false) THEN
            RAISE EXCEPTION 'Items can only be added to the active plan version' USING ERRCODE = 'VY001';
        END IF;
        RETURN NEW;
    END IF;

    IF (to_jsonb(NEW) - 'status') IS DISTINCT FROM (to_jsonb(OLD) - 'status') THEN
        RAISE EXCEPTION 'Plan item content is immutable; create a new plan version' USING ERRCODE = 'VY001';
    END IF;
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;
    IF NOT v_is_active THEN
        RAISE EXCEPTION 'Status can only be recorded for items of the active plan version' USING ERRCODE = 'VY001';
    END IF;
    IF OLD.status <> 'PLANNED' OR NEW.status = 'PLANNED' THEN
        RAISE EXCEPTION 'Status % of this item is already recorded', OLD.status USING ERRCODE = 'VY400';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER plan_item_guard BEFORE INSERT OR UPDATE ON plan_item
    FOR EACH ROW EXECUTE FUNCTION plan_item_guard();

-- ---------------------------------------------------------------------------------------------
-- Deviation detection (US-15)
-- ---------------------------------------------------------------------------------------------

-- Recording a deviation sets recoveryRequired on its plan.
CREATE FUNCTION deviation_event_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF NEW.resolved_at IS NOT NULL THEN
        RAISE EXCEPTION 'A new deviation event cannot be already resolved' USING ERRCODE = 'VY400';
    END IF;
    IF NEW.plan_item_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM plan_item pi JOIN plan_version pv ON pv.id = pi.plan_version_id
        WHERE pi.id = NEW.plan_item_id AND pv.daily_plan_id = NEW.daily_plan_id) THEN
        RAISE EXCEPTION 'Plan item does not belong to plan %', NEW.daily_plan_id USING ERRCODE = 'VY400';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER deviation_event_before_insert BEFORE INSERT ON deviation_event
    FOR EACH ROW EXECUTE FUNCTION deviation_event_before_insert();

CREATE FUNCTION deviation_event_after_insert() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
BEGIN
    UPDATE daily_plan SET recovery_required = true
    WHERE id = NEW.daily_plan_id AND NOT recovery_required;
    RETURN NULL;
END;
$$;
CREATE TRIGGER deviation_event_after_insert AFTER INSERT ON deviation_event
    FOR EACH ROW EXECUTE FUNCTION deviation_event_after_insert();

-- Only resolution (once) may be recorded on a deviation event.
CREATE FUNCTION deviation_event_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF (to_jsonb(NEW) - 'resolved_at') IS DISTINCT FROM (to_jsonb(OLD) - 'resolved_at')
       OR OLD.resolved_at IS NOT NULL OR NEW.resolved_at IS NULL THEN
        RAISE EXCEPTION 'A deviation event can only be resolved once' USING ERRCODE = 'VY001';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER deviation_event_guard BEFORE UPDATE ON deviation_event
    FOR EACH ROW EXECUTE FUNCTION deviation_event_guard();

-- Rule 1: a confirmed (COMPLETED) meal whose linked entries differ from the plan by >= threshold.
CREATE FUNCTION evaluate_meal_deviation(p_plan_item_id uuid) RETURNS boolean
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_item   record;
    v_actual numeric;
BEGIN
    SELECT pi.id, pi.item_type, pi.status, pi.planned_kcal, pv.daily_plan_id INTO v_item
    FROM plan_item pi JOIN plan_version pv ON pv.id = pi.plan_version_id
    WHERE pi.id = p_plan_item_id;

    IF NOT FOUND OR v_item.item_type <> 'MEAL' OR v_item.status <> 'COMPLETED' THEN
        RETURN false;
    END IF;

    SELECT coalesce(sum(kcal), 0) INTO v_actual FROM food_entry WHERE plan_item_id = p_plan_item_id;

    IF NOT is_significant_kcal_deviation(v_item.planned_kcal, v_actual) THEN
        RETURN false;
    END IF;

    INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type, kcal_difference)
    VALUES (v_item.daily_plan_id, v_item.id, 'KCAL_DIFFERENCE', v_actual - v_item.planned_kcal)
    ON CONFLICT (plan_item_id, deviation_type) WHERE resolved_at IS NULL AND plan_item_id IS NOT NULL
    DO NOTHING;
    RETURN true;
END;
$$;
COMMENT ON FUNCTION evaluate_meal_deviation(uuid) IS 'US-15 rule 1: records a KCAL_DIFFERENCE deviation for a completed meal when the confirmed kcal differ from the plan by >= threshold.';

-- Rule 2: an item marked SKIPPED/MISSED; a completed meal is evaluated by rule 1.
CREATE FUNCTION plan_item_after_status_change() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF NEW.status IN ('SKIPPED', 'MISSED') THEN
        INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type)
        SELECT pv.daily_plan_id, NEW.id,
               CASE NEW.status WHEN 'SKIPPED' THEN 'ITEM_SKIPPED' ELSE 'ITEM_MISSED' END
        FROM plan_version pv WHERE pv.id = NEW.plan_version_id
        ON CONFLICT (plan_item_id, deviation_type) WHERE resolved_at IS NULL AND plan_item_id IS NOT NULL
        DO NOTHING;
    ELSIF NEW.status = 'COMPLETED' AND NEW.item_type = 'MEAL' THEN
        PERFORM evaluate_meal_deviation(NEW.id);
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER plan_item_after_status_change AFTER UPDATE OF status ON plan_item
    FOR EACH ROW WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION plan_item_after_status_change();

-- ---------------------------------------------------------------------------------------------
-- Actual records: ownership and plan links (LEG-3 defence in depth, US-06, US-13)
-- ---------------------------------------------------------------------------------------------

CREATE FUNCTION food_entry_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_item record;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.user_id <> OLD.user_id THEN
        RAISE EXCEPTION 'The owner of a food entry cannot change' USING ERRCODE = 'VY400';
    END IF;

    IF NEW.plan_item_id IS NOT NULL AND (TG_OP = 'INSERT' OR NEW.plan_item_id IS DISTINCT FROM OLD.plan_item_id
                                         OR NEW.entry_date IS DISTINCT FROM OLD.entry_date) THEN
        SELECT dp.user_id, dp.plan_date, pi.item_type, pv.version_no = dp.current_version_no AS is_active
        INTO v_item
        FROM plan_item pi
        JOIN plan_version pv ON pv.id = pi.plan_version_id
        JOIN daily_plan dp ON dp.id = pv.daily_plan_id
        WHERE pi.id = NEW.plan_item_id;

        IF NOT FOUND OR v_item.user_id <> NEW.user_id THEN
            RAISE EXCEPTION 'Planned item not found' USING ERRCODE = 'VY404';
        END IF;
        IF v_item.item_type <> 'MEAL' THEN
            RAISE EXCEPTION 'A food entry can only fulfil a planned MEAL' USING ERRCODE = 'VY400';
        END IF;
        IF v_item.plan_date <> NEW.entry_date THEN
            RAISE EXCEPTION 'Entry date % differs from the plan date %', NEW.entry_date, v_item.plan_date
                USING ERRCODE = 'VY400';
        END IF;
        IF NOT v_item.is_active THEN
            RAISE EXCEPTION 'Entries can only be linked to items of the active plan version' USING ERRCODE = 'VY001';
        END IF;
    END IF;

    IF NEW.saved_meal_id IS NOT NULL AND (TG_OP = 'INSERT' OR NEW.saved_meal_id IS DISTINCT FROM OLD.saved_meal_id)
       AND NOT EXISTS (SELECT 1 FROM saved_meal WHERE id = NEW.saved_meal_id AND user_id = NEW.user_id) THEN
        RAISE EXCEPTION 'Saved meal not found' USING ERRCODE = 'VY404';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER food_entry_guard BEFORE INSERT OR UPDATE ON food_entry
    FOR EACH ROW EXECUTE FUNCTION food_entry_guard();

-- Correcting entries of an already completed meal re-evaluates the deviation (US-06 + US-15).
CREATE FUNCTION food_entry_after_change() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF NEW.plan_item_id IS NOT NULL THEN
        PERFORM evaluate_meal_deviation(NEW.plan_item_id);
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER food_entry_after_change AFTER INSERT OR UPDATE OF kcal, plan_item_id ON food_entry
    FOR EACH ROW EXECUTE FUNCTION food_entry_after_change();

CREATE FUNCTION activity_entry_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_item record;
BEGIN
    -- Unlinking (plan_item_id -> NULL by ON DELETE SET NULL when a plan is erased) is the only allowed link change.
    IF TG_OP = 'UPDATE' AND (NEW.user_id <> OLD.user_id
                             OR (NEW.plan_item_id IS DISTINCT FROM OLD.plan_item_id AND NEW.plan_item_id IS NOT NULL)
                             OR NEW.status <> OLD.status) THEN
        RAISE EXCEPTION 'Owner, planned item and status of an activity entry cannot change'
            USING ERRCODE = 'VY400';
    END IF;

    IF TG_OP = 'INSERT' AND NEW.plan_item_id IS NOT NULL THEN
        SELECT dp.user_id, dp.plan_date, pi.item_type, pi.status, pv.version_no = dp.current_version_no AS is_active
        INTO v_item
        FROM plan_item pi
        JOIN plan_version pv ON pv.id = pi.plan_version_id
        JOIN daily_plan dp ON dp.id = pv.daily_plan_id
        WHERE pi.id = NEW.plan_item_id;

        IF NOT FOUND OR v_item.user_id <> NEW.user_id THEN
            RAISE EXCEPTION 'Planned item not found' USING ERRCODE = 'VY404';
        END IF;
        IF v_item.item_type <> 'WORKOUT' THEN
            RAISE EXCEPTION 'An activity entry can only refer to a planned WORKOUT' USING ERRCODE = 'VY400';
        END IF;
        IF v_item.plan_date <> NEW.entry_date THEN
            RAISE EXCEPTION 'Entry date % differs from the plan date %', NEW.entry_date, v_item.plan_date
                USING ERRCODE = 'VY400';
        END IF;
        IF NOT v_item.is_active THEN
            RAISE EXCEPTION 'Entries can only refer to items of the active plan version' USING ERRCODE = 'VY001';
        END IF;
        IF v_item.status <> 'PLANNED' THEN
            RAISE EXCEPTION 'Status of this workout is already recorded' USING ERRCODE = 'VY400';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER activity_entry_guard BEFORE INSERT OR UPDATE ON activity_entry
    FOR EACH ROW EXECUTE FUNCTION activity_entry_guard();

-- The actual workout event determines the planned item's status (single source, US-13).
CREATE FUNCTION activity_entry_after_insert() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
BEGIN
    IF NEW.plan_item_id IS NOT NULL THEN
        UPDATE plan_item SET status = NEW.status WHERE id = NEW.plan_item_id;
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER activity_entry_after_insert AFTER INSERT ON activity_entry
    FOR EACH ROW EXECUTE FUNCTION activity_entry_after_insert();

-- ---------------------------------------------------------------------------------------------
-- Recovery proposals (US-16, US-17)
-- ---------------------------------------------------------------------------------------------

CREATE FUNCTION recovery_proposal_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_plan record;
BEGIN
    SELECT current_version_no, recovery_required INTO v_plan FROM daily_plan WHERE id = NEW.daily_plan_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Plan not found' USING ERRCODE = 'VY404';
    END IF;
    IF NEW.base_version_no <> v_plan.current_version_no THEN
        RAISE EXCEPTION 'Proposal base version % is not the active version %', NEW.base_version_no, v_plan.current_version_no
            USING ERRCODE = 'VY409';
    END IF;
    IF NOT v_plan.recovery_required THEN
        RAISE EXCEPTION 'Recovery is not required for this plan (US-16 precondition)' USING ERRCODE = 'VY400';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER recovery_proposal_before_insert BEFORE INSERT ON recovery_proposal
    FOR EACH ROW EXECUTE FUNCTION recovery_proposal_before_insert();

-- Changes may only target PLANNED items of the proposal's base version: completed items are frozen.
CREATE FUNCTION recovery_change_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_proposal record;
    v_target   record;
BEGIN
    SELECT daily_plan_id, base_version_no INTO v_proposal FROM recovery_proposal WHERE id = NEW.proposal_id;
    IF EXISTS (SELECT 1 FROM recovery_decision WHERE proposal_id = NEW.proposal_id) THEN
        RAISE EXCEPTION 'The proposal is already decided' USING ERRCODE = 'VY001';
    END IF;
    IF NEW.target_item_id IS NOT NULL THEN
        SELECT pi.status, pi.item_type INTO v_target
        FROM plan_item pi JOIN plan_version pv ON pv.id = pi.plan_version_id
        WHERE pi.id = NEW.target_item_id
          AND pv.daily_plan_id = v_proposal.daily_plan_id
          AND pv.version_no = v_proposal.base_version_no;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Target item is not part of the proposal base version' USING ERRCODE = 'VY400';
        END IF;
        IF v_target.status <> 'PLANNED' THEN
            RAISE EXCEPTION 'Completed or recorded items are frozen and cannot be changed (US-16)'
                USING ERRCODE = 'VY400';
        END IF;
        IF NEW.change_type = 'REPLACE' AND NEW.item_type <> v_target.item_type THEN
            RAISE EXCEPTION 'A % can only be replaced by a %', v_target.item_type, v_target.item_type
                USING ERRCODE = 'VY400';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER recovery_change_before_insert BEFORE INSERT ON recovery_change
    FOR EACH ROW EXECUTE FUNCTION recovery_change_before_insert();

-- ---------------------------------------------------------------------------------------------
-- Atomic plan-version creation (US-09, US-10, US-14, US-16 frozen items)
-- ---------------------------------------------------------------------------------------------

CREATE FUNCTION create_plan_version(
    p_user_id          uuid,
    p_plan_date        date,
    p_expected_version integer,
    p_change_reason    varchar,
    p_items            jsonb DEFAULT '[]'::jsonb
) RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_plan_id    uuid;
    v_current    integer;
    v_new        integer;
    v_version_id uuid;
BEGIN
    IF p_user_id IS NULL OR p_plan_date IS NULL OR p_expected_version IS NULL OR p_expected_version < 0
       OR p_change_reason IS NULL OR p_items IS NULL OR jsonb_typeof(p_items) <> 'array' THEN
        RAISE EXCEPTION 'Invalid plan version request' USING ERRCODE = 'VY400';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM app_user WHERE id = p_user_id) THEN
        RAISE EXCEPTION 'User not found' USING ERRCODE = 'VY404';
    END IF;

    INSERT INTO daily_plan (user_id, plan_date) VALUES (p_user_id, p_plan_date)
    ON CONFLICT (user_id, plan_date) DO NOTHING;

    SELECT id, current_version_no INTO v_plan_id, v_current
    FROM daily_plan WHERE user_id = p_user_id AND plan_date = p_plan_date
    FOR UPDATE;

    IF v_current <> p_expected_version THEN
        RAISE EXCEPTION 'Stale plan version: expected %, current %', p_expected_version, v_current
            USING ERRCODE = 'VY409';
    END IF;
    IF (v_current = 0) <> (p_change_reason = 'INITIAL_PLAN') THEN
        RAISE EXCEPTION 'INITIAL_PLAN is required for the first version and only for it' USING ERRCODE = 'VY400';
    END IF;

    -- Supplied items may not re-use the key of a frozen (already recorded) item.
    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(p_items) e
        JOIN plan_item pi ON pi.item_key = (e ->> 'itemKey')::uuid
        JOIN plan_version pv ON pv.id = pi.plan_version_id
        WHERE pv.daily_plan_id = v_plan_id AND pv.version_no = v_current AND pi.status <> 'PLANNED') THEN
        RAISE EXCEPTION 'Completed or recorded items are frozen and cannot be replaced (US-16)'
            USING ERRCODE = 'VY400';
    END IF;

    v_new := v_current + 1;
    INSERT INTO plan_version (daily_plan_id, version_no, change_reason)
    VALUES (v_plan_id, v_new, p_change_reason)
    RETURNING id INTO v_version_id;

    -- Frozen items: everything already recorded in the previous version is carried over unchanged.
    INSERT INTO plan_item (plan_version_id, item_key, item_type, meal_slot_code, planned_time, meal_id, exercise_id,
                           planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g, duration_min, status)
    SELECT v_version_id, pi.item_key, pi.item_type, pi.meal_slot_code, pi.planned_time, pi.meal_id, pi.exercise_id,
           pi.planned_kcal, pi.planned_protein_g, pi.planned_fat_g, pi.planned_carbs_g, pi.duration_min, pi.status
    FROM plan_item pi JOIN plan_version pv ON pv.id = pi.plan_version_id
    WHERE pv.daily_plan_id = v_plan_id AND pv.version_no = v_current AND pi.status <> 'PLANNED';

    -- Remaining (future) items supplied by the caller.
    INSERT INTO plan_item (plan_version_id, item_key, item_type, meal_slot_code, planned_time, meal_id, exercise_id,
                           planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g, duration_min, status)
    SELECT v_version_id,
           coalesce((e ->> 'itemKey')::uuid, gen_random_uuid()),
           e ->> 'itemType',
           e ->> 'mealSlot',
           (e ->> 'plannedTime')::time,
           (e ->> 'mealId')::bigint,
           (e ->> 'exerciseId')::bigint,
           (e ->> 'kcal')::numeric,
           (e ->> 'proteinG')::numeric,
           (e ->> 'fatG')::numeric,
           (e ->> 'carbsG')::numeric,
           (e ->> 'durationMin')::smallint,
           'PLANNED'
    FROM jsonb_array_elements(p_items) e;

    RETURN v_new;
END;
$$;
COMMENT ON FUNCTION create_plan_version(uuid, date, integer, varchar, jsonb) IS
    'Creates the next immutable plan version atomically (Q2). Checks the expected version (409 on mismatch), copies recorded items unchanged (frozen, US-16) and inserts the supplied remaining items. Returns the new version number.';

-- ---------------------------------------------------------------------------------------------
-- Recovery decision (US-18, NFR-09, NFR-10) - one transaction, no partial plan
-- ---------------------------------------------------------------------------------------------

CREATE PROCEDURE apply_recovery_decision(
    p_user_id             uuid,
    p_proposal_id         uuid,
    p_decision            varchar,
    p_base_version        integer,
    p_selected_change_ids uuid[] DEFAULT NULL,
    INOUT p_new_version_no integer DEFAULT NULL
)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = veya, pg_temp
AS $$
DECLARE
    v_ctx        record;
    v_applied    uuid[];
    v_items      jsonb;
    v_version_id uuid;
BEGIN
    p_new_version_no := NULL;

    IF p_decision IS NULL OR p_decision NOT IN ('ACCEPT', 'EDIT', 'REJECT') THEN
        RAISE EXCEPTION 'Decision must be ACCEPT, EDIT or REJECT' USING ERRCODE = 'VY400';
    END IF;

    -- Lock the plan row: concurrent decisions for the same plan are serialised here.
    SELECT p.id AS proposal_id, p.base_version_no, dp.id AS plan_id, dp.user_id, dp.plan_date, dp.current_version_no
    INTO v_ctx
    FROM recovery_proposal p JOIN daily_plan dp ON dp.id = p.daily_plan_id
    WHERE p.id = p_proposal_id
    FOR UPDATE OF dp;

    IF NOT FOUND OR v_ctx.user_id IS DISTINCT FROM p_user_id THEN
        RAISE EXCEPTION 'Proposal not found' USING ERRCODE = 'VY404';
    END IF;
    IF EXISTS (SELECT 1 FROM recovery_decision WHERE proposal_id = p_proposal_id) THEN
        RAISE EXCEPTION 'Proposal is already decided' USING ERRCODE = 'VY409';
    END IF;
    IF p_base_version IS DISTINCT FROM v_ctx.base_version_no OR v_ctx.base_version_no <> v_ctx.current_version_no THEN
        RAISE EXCEPTION 'Stale baseVersion %: active plan version is %', p_base_version, v_ctx.current_version_no
            USING ERRCODE = 'VY409';
    END IF;

    IF p_decision = 'REJECT' THEN
        IF p_selected_change_ids IS NOT NULL THEN
            RAISE EXCEPTION 'REJECT does not take selected changes' USING ERRCODE = 'VY400';
        END IF;
        INSERT INTO recovery_decision (proposal_id, decision) VALUES (p_proposal_id, 'REJECT');
        RETURN;   -- active plan unchanged
    END IF;

    IF p_decision = 'ACCEPT' THEN
        IF p_selected_change_ids IS NOT NULL THEN
            RAISE EXCEPTION 'ACCEPT applies the whole proposal; use EDIT to select changes' USING ERRCODE = 'VY400';
        END IF;
        SELECT coalesce(array_agg(id), '{}') INTO v_applied FROM recovery_change WHERE proposal_id = p_proposal_id;
    ELSE
        -- EDIT (provisional interpretation, docs/ba-open-questions.md Q12): apply a non-empty subset of changes.
        IF p_selected_change_ids IS NULL OR cardinality(p_selected_change_ids) = 0
           OR (SELECT count(DISTINCT x) FROM unnest(p_selected_change_ids) x) <> cardinality(p_selected_change_ids)
           OR EXISTS (SELECT 1 FROM unnest(p_selected_change_ids) x
                      WHERE NOT EXISTS (SELECT 1 FROM recovery_change c
                                        WHERE c.id = x AND c.proposal_id = p_proposal_id)) THEN
            RAISE EXCEPTION 'Invalid edit: select one or more distinct changes of this proposal' USING ERRCODE = 'VY400';
        END IF;
        v_applied := p_selected_change_ids;
    END IF;

    -- Remaining items of the new version: PLANNED items of the active version with the applied changes.
    SELECT coalesce(jsonb_agg(item), '[]'::jsonb) INTO v_items
    FROM (
        SELECT CASE WHEN c.change_type = 'REPLACE' THEN
                   jsonb_build_object('itemKey', pi.item_key, 'itemType', c.item_type, 'mealSlot', c.meal_slot_code,
                                      'plannedTime', c.planned_time, 'mealId', c.meal_id, 'exerciseId', c.exercise_id,
                                      'kcal', c.planned_kcal, 'proteinG', c.planned_protein_g, 'fatG', c.planned_fat_g,
                                      'carbsG', c.planned_carbs_g, 'durationMin', c.duration_min)
               ELSE
                   jsonb_build_object('itemKey', pi.item_key, 'itemType', pi.item_type, 'mealSlot', pi.meal_slot_code,
                                      'plannedTime', pi.planned_time, 'mealId', pi.meal_id, 'exerciseId', pi.exercise_id,
                                      'kcal', pi.planned_kcal, 'proteinG', pi.planned_protein_g, 'fatG', pi.planned_fat_g,
                                      'carbsG', pi.planned_carbs_g, 'durationMin', pi.duration_min)
               END AS item
        FROM plan_item pi
        JOIN plan_version pv ON pv.id = pi.plan_version_id
        LEFT JOIN recovery_change c ON c.target_item_id = pi.id AND c.id = ANY (v_applied)
        WHERE pv.daily_plan_id = v_ctx.plan_id
          AND pv.version_no = v_ctx.current_version_no
          AND pi.status = 'PLANNED'
          AND c.change_type IS DISTINCT FROM 'REMOVE'
        UNION ALL
        SELECT jsonb_build_object('itemType', c.item_type, 'mealSlot', c.meal_slot_code,
                                  'plannedTime', c.planned_time, 'mealId', c.meal_id, 'exerciseId', c.exercise_id,
                                  'kcal', c.planned_kcal, 'proteinG', c.planned_protein_g, 'fatG', c.planned_fat_g,
                                  'carbsG', c.planned_carbs_g, 'durationMin', c.duration_min)
        FROM recovery_change c
        WHERE c.id = ANY (v_applied) AND c.change_type = 'ADD'
    ) s;

    p_new_version_no := create_plan_version(
        v_ctx.user_id, v_ctx.plan_date, v_ctx.current_version_no,
        CASE p_decision WHEN 'ACCEPT' THEN 'RECOVERY_ACCEPTED' ELSE 'RECOVERY_EDITED' END,
        v_items);

    SELECT id INTO v_version_id FROM plan_version
    WHERE daily_plan_id = v_ctx.plan_id AND version_no = p_new_version_no;

    INSERT INTO recovery_decision (proposal_id, decision, resulting_version_id)
    VALUES (p_proposal_id, p_decision, v_version_id);

    UPDATE deviation_event SET resolved_at = now() WHERE daily_plan_id = v_ctx.plan_id AND resolved_at IS NULL;
    UPDATE daily_plan SET recovery_required = false WHERE id = v_ctx.plan_id;
END;
$$;
COMMENT ON PROCEDURE apply_recovery_decision(uuid, uuid, varchar, integer, uuid[], integer) IS
    'US-18: checks ownership and baseVersion, then records ACCEPT/EDIT together with the new plan version, or REJECT without changing the plan, in the caller''s transaction (NFR-09). Stale baseVersion -> VY409 (NFR-10).';

-- ---------------------------------------------------------------------------------------------
-- Data export (LEG-4, Q6) and masking helpers (LEG-5, LEG-8)
-- ---------------------------------------------------------------------------------------------

CREATE FUNCTION export_user_data(p_user_id uuid) RETURNS jsonb
    LANGUAGE sql
    STABLE
    SET search_path = veya, pg_temp
AS $$
    SELECT jsonb_build_object(
        'exportedAt', now(),
        'account', jsonb_build_object('id', u.id, 'email', u.email, 'createdAt', u.created_at,
                                      'adultConfirmedAt', u.adult_confirmed_at),
        'profile', (SELECT to_jsonb(p) - 'user_id' FROM user_profile p WHERE p.user_id = u.id),
        'foodExclusions', (SELECT coalesce(jsonb_agg(e.ingredient_code ORDER BY e.ingredient_code), '[]')
                           FROM profile_food_exclusion e WHERE e.user_id = u.id),
        'consents', (SELECT coalesce(jsonb_agg(jsonb_build_object('type', d.consent_type, 'version', d.version,
                                                                  'grantedAt', c.granted_at, 'withdrawnAt', c.withdrawn_at)
                                               ORDER BY c.granted_at), '[]')
                     FROM user_consent c JOIN consent_document d ON d.id = c.consent_document_id
                     WHERE c.user_id = u.id),
        'dailyConstraints', (SELECT coalesce(jsonb_agg(to_jsonb(dc) - 'user_id' ORDER BY dc.constraint_date), '[]')
                             FROM daily_constraint dc WHERE dc.user_id = u.id),
        'foodEntries', (SELECT coalesce(jsonb_agg(to_jsonb(f) - 'user_id' ORDER BY f.entry_date, f.created_at), '[]')
                        FROM food_entry f WHERE f.user_id = u.id),
        'activityEntries', (SELECT coalesce(jsonb_agg(to_jsonb(a) - 'user_id' ORDER BY a.entry_date, a.created_at), '[]')
                            FROM activity_entry a WHERE a.user_id = u.id),
        'savedMeals', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', s.name, 'items',
                                         (SELECT jsonb_agg(to_jsonb(i) - 'saved_meal_id' ORDER BY i.line_no)
                                          FROM saved_meal_item i WHERE i.saved_meal_id = s.id)) ORDER BY s.name), '[]')
                       FROM saved_meal s WHERE s.user_id = u.id),
        'plans', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                         'date', dp.plan_date, 'activeVersion', dp.current_version_no,
                         'versions', (SELECT jsonb_agg(jsonb_build_object(
                                              'version', pv.version_no, 'reason', pv.change_reason, 'createdAt', pv.created_at,
                                              'items', (SELECT jsonb_agg(to_jsonb(pi) - 'plan_version_id' ORDER BY pi.planned_time)
                                                        FROM plan_item pi WHERE pi.plan_version_id = pv.id))
                                          ORDER BY pv.version_no)
                                      FROM plan_version pv WHERE pv.daily_plan_id = dp.id),
                         'decisions', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                                               'proposalId', rp.id, 'baseVersion', rp.base_version_no,
                                               'decision', rd.decision, 'decidedAt', rd.decided_at) ORDER BY rd.decided_at), '[]')
                                       FROM recovery_proposal rp JOIN recovery_decision rd ON rd.proposal_id = rp.id
                                       WHERE rp.daily_plan_id = dp.id))
                     ORDER BY dp.plan_date), '[]')
                  FROM daily_plan dp WHERE dp.user_id = u.id)
    )
    FROM app_user u
    WHERE u.id = p_user_id;
$$;
COMMENT ON FUNCTION export_user_data(uuid) IS 'LEG-4 / Q6: minimal JSON export of one user''s own records (no password hash). NULL if the user does not exist.';

CREATE FUNCTION mask_email(p_email text) RETURNS text
    LANGUAGE sql
    IMMUTABLE
    SET search_path = veya, pg_temp
AS $$
    SELECT CASE
        WHEN p_email IS NULL THEN NULL
        ELSE left(split_part(p_email, '@', 1), 1) || '***@'
             || left(split_part(p_email, '@', 2), 1) || '***'
             || coalesce('.' || substring(p_email FROM '\.([^.@]+)$'), '')
    END;
$$;
COMMENT ON FUNCTION mask_email(text) IS 'Masks an e-mail address: anna.k@example.org -> a***@e***.org.';

CREATE FUNCTION pseudonym(p_id uuid) RETURNS text
    LANGUAGE sql
    IMMUTABLE
    SET search_path = veya, pg_temp
AS $$
    SELECT 'U-' || upper(left(encode(sha256(convert_to('veya-pseudonym:' || p_id::text, 'UTF8')), 'hex'), 12));
$$;
COMMENT ON FUNCTION pseudonym(uuid) IS 'Stable, non-reversible pseudonym of a user id (SHA-256) for anonymised reporting views.';
