-- V9: object privileges for the VEYA roles (least privilege).
-- Roles are cluster objects created by infra/postgres/initdb/01-create-roles.sh (they cannot be created
-- by veya_owner, which has no CREATEROLE). This migration only grants/revokes on objects it owns.
--
--   veya_app      : SELECT on reference data; DML on user-owned records; no UPDATE/DELETE on history
--                   (plan versions, proposals, decisions); plan versions only through create_plan_version /
--                   apply_recovery_decision; no DDL.
--   veya_readonly : SELECT on reference/catalogue data and on masked/anonymised views only.
--   veya_admin    : full DML on all tables and views, EXECUTE on all routines; no DDL (schema changes
--                   only through Flyway as veya_owner).

DO $$
BEGIN
    IF (SELECT count(*) FROM pg_roles WHERE rolname IN ('veya_app', 'veya_readonly', 'veya_admin')) <> 3 THEN
        RAISE EXCEPTION 'Roles veya_app, veya_readonly and veya_admin must exist; they are created by infra/postgres/initdb/01-create-roles.sh';
    END IF;
END;
$$;

-- Schema ---------------------------------------------------------------------------------------
REVOKE ALL ON SCHEMA veya FROM PUBLIC;
GRANT USAGE ON SCHEMA veya TO veya_app, veya_readonly, veya_admin;

-- Routines: nobody executes VEYA routines unless granted (SECURITY DEFINER functions especially).
REVOKE EXECUTE ON FUNCTION
    set_updated_at(), reject_update(), user_consent_guard(), daily_plan_guard(),
    plan_version_before_insert(), plan_version_after_insert(), plan_item_guard(),
    deviation_event_before_insert(), deviation_event_after_insert(), deviation_event_guard(),
    evaluate_meal_deviation(uuid), plan_item_after_status_change(), food_entry_guard(), food_entry_after_change(),
    activity_entry_guard(), activity_entry_after_insert(), recovery_proposal_before_insert(),
    recovery_change_before_insert(), create_plan_version(uuid, date, integer, varchar, jsonb),
    export_user_data(uuid), mask_email(text), pseudonym(uuid), is_significant_kcal_deviation(numeric, numeric)
FROM PUBLIC;
REVOKE EXECUTE ON PROCEDURE apply_recovery_decision(uuid, uuid, varchar, integer, uuid[], integer) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    create_plan_version(uuid, date, integer, varchar, jsonb),
    export_user_data(uuid),
    is_significant_kcal_deviation(numeric, numeric)
TO veya_app, veya_admin;
GRANT EXECUTE ON PROCEDURE apply_recovery_decision(uuid, uuid, varchar, integer, uuid[], integer) TO veya_app, veya_admin;
GRANT EXECUTE ON FUNCTION mask_email(text), pseudonym(uuid) TO veya_app, veya_readonly, veya_admin;
GRANT EXECUTE ON FUNCTION evaluate_meal_deviation(uuid) TO veya_admin;

-- Reference data and catalogue ------------------------------------------------------------------
GRANT SELECT ON goal_type, meal_slot, equipment, ingredient, recovery_setting, consent_document,
                food_item, food_item_ingredient, meal, meal_component, exercise, exercise_equipment
    TO veya_app, veya_readonly;
-- The integration module caches normalized provider items locally (US-05, US-31); no deletion.
GRANT INSERT, UPDATE ON food_item, exercise TO veya_app;
GRANT INSERT ON food_item_ingredient, exercise_equipment TO veya_app;

-- Identity, consent, profile, constraints ---------------------------------------------------------
GRANT SELECT, INSERT, DELETE ON app_user TO veya_app;                 -- DELETE: account deletion (US-04)
GRANT UPDATE (email, password_hash) ON app_user TO veya_app;
GRANT SELECT, INSERT ON user_consent TO veya_app;
GRANT UPDATE (withdrawn_at) ON user_consent TO veya_app;
GRANT SELECT, INSERT, UPDATE ON user_profile TO veya_app;
GRANT SELECT, INSERT, DELETE ON profile_food_exclusion TO veya_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON daily_constraint, daily_constraint_ingredient, daily_constraint_equipment TO veya_app;

-- Diary and saved meals --------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON saved_meal, saved_meal_item, food_entry TO veya_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON activity_entry TO veya_app;

-- Planning: read + status only; versions are created by SECURITY DEFINER routines -----------------
GRANT SELECT ON daily_plan, plan_version, plan_item TO veya_app;
GRANT UPDATE (status) ON plan_item TO veya_app;

-- Recovery ----------------------------------------------------------------------------------------
GRANT SELECT, INSERT ON deviation_event TO veya_app;                  -- CONSTRAINT_INFEASIBLE (US-15 rule 3)
GRANT SELECT, INSERT ON recovery_proposal, recovery_change TO veya_app;
GRANT SELECT ON recovery_decision TO veya_app;                        -- written by apply_recovery_decision

-- Views -------------------------------------------------------------------------------------------
GRANT SELECT ON v_meal_nutrition, v_active_plan_item, v_daily_nutrition_summary, v_day_summary,
                v_recovery_proposal_overview TO veya_app;
GRANT SELECT ON v_meal_nutrition, v_app_user_masked, v_analytics_daily_nutrition TO veya_readonly;

-- Administration ----------------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA veya TO veya_admin;
-- Routine privileges for veya_admin are granted explicitly above (not ON ALL FUNCTIONS): the pg_trgm
-- functions in this schema belong to the bootstrap superuser and cannot be granted by veya_owner.

-- Flyway history: readable for administration/reporting, never writable by app/admin.
REVOKE INSERT, UPDATE, DELETE ON flyway_schema_history FROM veya_admin;
GRANT SELECT ON flyway_schema_history TO veya_readonly;

-- Future objects created by veya_owner: admin gets DML automatically; routines are not public.
ALTER DEFAULT PRIVILEGES IN SCHEMA veya GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO veya_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA veya REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA veya GRANT EXECUTE ON FUNCTIONS TO veya_admin;
