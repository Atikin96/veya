-- VEYA demo / test dataset (synthetic, LEG-8). NOT a migration: load it only into development or test
-- databases, after Flyway has migrated the schema:
--
--   docker exec -i veya-postgres sh -c 'PGPASSWORD="$VEYA_ADMIN_PASSWORD" psql -v ON_ERROR_STOP=1 \
--       -h localhost -U veya_admin_user -d veya -f /veya/seed/demo_data.sql'
--
-- The script runs as veya_admin_user (no superuser) in one transaction and goes through the same
-- functions, procedure and triggers as the application, so every row satisfies the business rules.
-- It is deterministic (fixed user ids, item keys and dates) and must be loaded into an empty database.
-- E-mail addresses use the reserved example.org domain; password hashes are real bcrypt hashes of random, discarded secrets: no demo account has a usable password.
--
-- Scenarios (normal and edge cases):
--   Anna   2026-11-15  normal day: plan fully completed, diary matches the plan, saved-meal reuse (US-08).
--   Anna   2026-11-16  UC-01: lunch +250 kcal (deviation), workout SKIPPED (deviation), fish unavailable
--                      (CONSTRAINT_INFEASIBLE); two proposals; one ACCEPTED -> version 2, the other STALE.
--   Max    2026-11-16  boundaries: breakfast +199 kcal (no deviation), lunch -200 kcal (deviation);
--                      proposal REJECTED -> plan unchanged, recoveryRequired stays TRUE.
--   Max    2026-11-14  breakfast MISSED; proposal EDITED (one of two changes applied) -> version 2.
--   Nadia  (new user)  registered only: privacy notice, no profile, no plans (empty-state edge case).
--   Olga   (withdrawn) health-data consent withdrawn; unplanned diary/activity records; Unicode food name.

\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = veya;

-- ---------------------------------------------------------------------------------------------
-- Users, consents, profiles
-- ---------------------------------------------------------------------------------------------
INSERT INTO app_user (id, email, password_hash, adult_confirmed_at, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'anna.demo@example.org',  '$2a$10$BEqS06K2yEZ9WQo4WKwqUOq6Kx8Ctc237qy8Na.ehaYvoPnrVfp26', '2026-11-01 09:00+01', '2026-11-01 09:00+01', '2026-11-01 09:00+01'),
    ('a0000000-0000-4000-8000-000000000002', 'max.demo@example.org',   '$2a$10$UpgecL5vsIFUfSNZZIBdZO6AqF5jQfw34dMFxdLo1e2HIYgAsNuOm', '2026-11-02 18:30+01', '2026-11-02 18:30+01', '2026-11-02 18:30+01'),
    ('a0000000-0000-4000-8000-000000000003', 'nadia.new@example.org',  '$2a$10$0THbLmBuw1XQErvqiKjCuOYGAVIcNsDp2feGuVkneycQpsFLTGYQ.', '2026-11-13 21:15+01', '2026-11-13 21:15+01', '2026-11-13 21:15+01'),
    ('a0000000-0000-4000-8000-000000000004', 'olga.withdrawn@example.org', '$2a$10$jEiEt9Ug474IJH2WNDOzjuHUkjIKRx4Z0zMWz18gLhJTECnU4sSEu', '2026-11-05 07:45+01', '2026-11-05 07:45+01', '2026-11-05 07:45+01');

INSERT INTO user_consent (user_id, consent_document_id, granted_at)
SELECT x.user_id::uuid, d.id, x.granted_at::timestamptz
FROM (VALUES
    ('a0000000-0000-4000-8000-000000000001', 'PRIVACY_NOTICE',      '2026-11-01 09:00+01'),
    ('a0000000-0000-4000-8000-000000000001', 'HEALTH_DATA_CONSENT', '2026-11-01 09:01+01'),
    ('a0000000-0000-4000-8000-000000000002', 'PRIVACY_NOTICE',      '2026-11-02 18:30+01'),
    ('a0000000-0000-4000-8000-000000000002', 'HEALTH_DATA_CONSENT', '2026-11-02 18:31+01'),
    ('a0000000-0000-4000-8000-000000000003', 'PRIVACY_NOTICE',      '2026-11-13 21:15+01'),
    ('a0000000-0000-4000-8000-000000000004', 'PRIVACY_NOTICE',      '2026-11-05 07:45+01'),
    ('a0000000-0000-4000-8000-000000000004', 'HEALTH_DATA_CONSENT', '2026-11-05 07:46+01')
) AS x (user_id, consent_type, granted_at)
JOIN consent_document d ON d.consent_type = x.consent_type AND d.version = '1.0';

-- Olga withdraws her health-data consent (allowed exactly once).
UPDATE user_consent SET withdrawn_at = '2026-11-12 10:00+01'
WHERE user_id = 'a0000000-0000-4000-8000-000000000004'
  AND consent_document_id = (SELECT id FROM consent_document WHERE consent_type = 'HEALTH_DATA_CONSENT' AND version = '1.0');

INSERT INTO user_profile (user_id, goal_code, energy_target_kcal, protein_target_g, fat_target_g, carbs_target_g) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'LOSE_WEIGHT',     1900, 110.0, 60.0, 220.0),
    ('a0000000-0000-4000-8000-000000000002', 'GAIN_WEIGHT',     2800, 150.0, 85.0, 350.0),
    ('a0000000-0000-4000-8000-000000000004', 'MAINTAIN_WEIGHT', 2100, 100.0, 70.0, 260.0);

INSERT INTO profile_food_exclusion (user_id, ingredient_code) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'PEANUT'),
    ('a0000000-0000-4000-8000-000000000002', 'PORK');

-- ---------------------------------------------------------------------------------------------
-- Daily constraints
-- ---------------------------------------------------------------------------------------------
INSERT INTO daily_constraint (id, user_id, constraint_date, available_time_min) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-16', 40),
    ('d0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002', DATE '2026-11-16', 60),
    ('d0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', DATE '2026-11-14', NULL);

INSERT INTO daily_constraint_ingredient (daily_constraint_id, ingredient_code, availability) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'FISH',   'EXCLUDED'),   -- Anna: salmon unavailable today (temporary, Q5)
    ('d0000000-0000-4000-8000-000000000002', 'OAT',    'AVAILABLE'),  -- Max: products at home (Q3)
    ('d0000000-0000-4000-8000-000000000002', 'MILK',   'AVAILABLE'),
    ('d0000000-0000-4000-8000-000000000002', 'BANANA', 'AVAILABLE'),
    ('d0000000-0000-4000-8000-000000000002', 'WHEAT',  'AVAILABLE'),
    ('d0000000-0000-4000-8000-000000000002', 'BEEF',   'AVAILABLE'),
    ('d0000000-0000-4000-8000-000000000002', 'TOMATO', 'AVAILABLE');

INSERT INTO daily_constraint_equipment (daily_constraint_id, equipment_code) VALUES
    ('d0000000-0000-4000-8000-000000000002', 'DUMBBELLS');            -- Anna has no equipment on 2026-11-16

-- ---------------------------------------------------------------------------------------------
-- Saved meal (US-08)
-- ---------------------------------------------------------------------------------------------
INSERT INTO saved_meal (id, user_id, name) VALUES
    ('e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'My usual breakfast');

INSERT INTO saved_meal_item (saved_meal_id, line_no, food_item_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'e0000000-0000-4000-8000-000000000001', x.line_no, f.id, f.name, x.grams,
       round(f.kcal_per_100g * x.grams / 100, 1), round(f.protein_per_100g * x.grams / 100, 1),
       round(f.fat_per_100g * x.grams / 100, 1), round(f.carbs_per_100g * x.grams / 100, 1)
FROM (VALUES (1, 'Eggs', 120), (2, 'Whole wheat bread', 60)) AS x (line_no, food_name, grams)
JOIN food_item f ON f.name = x.food_name AND f.source = 'LOCAL';

-- ---------------------------------------------------------------------------------------------
-- Anna, 2026-11-15: a normal, fully completed day
-- ---------------------------------------------------------------------------------------------
SELECT create_plan_version('a0000000-0000-4000-8000-000000000001', DATE '2026-11-15', 0, 'INITIAL_PLAN',
    (SELECT jsonb_agg(item) FROM (
        SELECT jsonb_build_object('itemKey', x.item_key, 'itemType', 'MEAL', 'mealSlot', x.slot, 'plannedTime', x.t,
                                  'mealId', n.meal_id, 'kcal', n.kcal, 'proteinG', n.protein_g, 'fatG', n.fat_g, 'carbsG', n.carbs_g) AS item
        FROM (VALUES ('b0000000-0000-4000-8000-000000000101', 'BREAKFAST', '08:00', 'Scrambled eggs on toast'),
                     ('b0000000-0000-4000-8000-000000000102', 'LUNCH',     '13:00', 'Chicken rice bowl'),
                     ('b0000000-0000-4000-8000-000000000103', 'DINNER',    '19:00', 'Buckwheat with chicken')) AS x (item_key, slot, t, meal_name)
        JOIN v_meal_nutrition n ON n.meal_name = x.meal_name
        UNION ALL
        SELECT jsonb_build_object('itemKey', 'b0000000-0000-4000-8000-000000000104', 'itemType', 'WORKOUT', 'plannedTime', '18:00',
                                  'exerciseId', (SELECT id FROM exercise WHERE name = 'Brisk walking'), 'durationMin', 40)
    ) s));

-- Breakfast re-logged from the saved meal (US-08), lunch and dinner from catalogue components.
INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_item_id, saved_meal_id, plan_item_id,
                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-15', 'BREAKFAST', 'SAVED_MEAL', i.food_item_id, i.saved_meal_id,
       (SELECT plan_item_id FROM v_active_plan_item WHERE item_key = 'b0000000-0000-4000-8000-000000000101'),
       i.food_name, i.portion_g, i.kcal, i.protein_g, i.fat_g, i.carbs_g
FROM saved_meal_item i WHERE i.saved_meal_id = 'e0000000-0000-4000-8000-000000000001';

INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_item_id, plan_item_id,
                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-15', a.meal_slot_code, 'CATALOG', f.id, a.plan_item_id,
       f.name, mc.grams, round(f.kcal_per_100g * mc.grams / 100, 1), round(f.protein_per_100g * mc.grams / 100, 1),
       round(f.fat_per_100g * mc.grams / 100, 1), round(f.carbs_per_100g * mc.grams / 100, 1)
FROM v_active_plan_item a
JOIN meal_component mc ON mc.meal_id = a.meal_id
JOIN food_item f ON f.id = mc.food_item_id
WHERE a.item_key IN ('b0000000-0000-4000-8000-000000000102', 'b0000000-0000-4000-8000-000000000103');

UPDATE plan_item SET status = 'COMPLETED'
WHERE id IN (SELECT plan_item_id FROM v_active_plan_item
             WHERE item_key IN ('b0000000-0000-4000-8000-000000000101', 'b0000000-0000-4000-8000-000000000102',
                                'b0000000-0000-4000-8000-000000000103'));

INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status, duration_min)
SELECT 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-15', a.exercise_id, a.plan_item_id, 'COMPLETED', 40
FROM v_active_plan_item a WHERE a.item_key = 'b0000000-0000-4000-8000-000000000104';

-- ---------------------------------------------------------------------------------------------
-- Anna, 2026-11-16: UC-01 Dynamic Plan Recovery, ACCEPT
-- ---------------------------------------------------------------------------------------------
SELECT create_plan_version('a0000000-0000-4000-8000-000000000001', DATE '2026-11-16', 0, 'INITIAL_PLAN',
    (SELECT jsonb_agg(item) FROM (
        SELECT jsonb_build_object('itemKey', x.item_key, 'itemType', 'MEAL', 'mealSlot', x.slot, 'plannedTime', x.t,
                                  'mealId', n.meal_id, 'kcal', n.kcal, 'proteinG', n.protein_g, 'fatG', n.fat_g, 'carbsG', n.carbs_g) AS item
        FROM (VALUES ('b0000000-0000-4000-8000-000000000201', 'BREAKFAST', '08:00', 'Oatmeal with banana'),
                     ('b0000000-0000-4000-8000-000000000202', 'LUNCH',     '13:00', 'Chicken rice bowl'),
                     ('b0000000-0000-4000-8000-000000000203', 'DINNER',    '19:30', 'Salmon with potatoes')) AS x (item_key, slot, t, meal_name)
        JOIN v_meal_nutrition n ON n.meal_name = x.meal_name
        UNION ALL
        SELECT jsonb_build_object('itemKey', 'b0000000-0000-4000-8000-000000000204', 'itemType', 'WORKOUT', 'plannedTime', '18:00',
                                  'exerciseId', (SELECT id FROM exercise WHERE name = 'Running, 10 km/h'), 'durationMin', 30)
    ) s));

-- Breakfast exactly as planned -> no deviation.
INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_item_id, plan_item_id,
                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-16', 'BREAKFAST', 'CATALOG', f.id, a.plan_item_id,
       f.name, mc.grams, round(f.kcal_per_100g * mc.grams / 100, 1), round(f.protein_per_100g * mc.grams / 100, 1),
       round(f.fat_per_100g * mc.grams / 100, 1), round(f.carbs_per_100g * mc.grams / 100, 1)
FROM v_active_plan_item a
JOIN meal_component mc ON mc.meal_id = a.meal_id
JOIN food_item f ON f.id = mc.food_item_id
WHERE a.item_key = 'b0000000-0000-4000-8000-000000000201';

-- Long meeting: pizza instead of the planned lunch, +250 kcal -> KCAL_DIFFERENCE deviation once confirmed.
INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id,
                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-16', 'LUNCH', 'MANUAL', a.plan_item_id,
       'Office pizza (3 slices)', 330, a.planned_kcal + 250, 32.0, 38.0, 110.0
FROM v_active_plan_item a WHERE a.item_key = 'b0000000-0000-4000-8000-000000000202';

UPDATE plan_item SET status = 'COMPLETED'
WHERE id IN (SELECT plan_item_id FROM v_active_plan_item
             WHERE item_key IN ('b0000000-0000-4000-8000-000000000201', 'b0000000-0000-4000-8000-000000000202'));

-- The evening run is skipped -> ITEM_SKIPPED deviation.
INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status, duration_min)
SELECT 'a0000000-0000-4000-8000-000000000001', DATE '2026-11-16', a.exercise_id, a.plan_item_id, 'SKIPPED', NULL
FROM v_active_plan_item a WHERE a.item_key = 'b0000000-0000-4000-8000-000000000204';

-- Fish is unavailable today: the application detects that the salmon dinner is infeasible (US-15 rule 3).
INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type, details)
SELECT a.daily_plan_id, a.plan_item_id, 'CONSTRAINT_INFEASIBLE', 'Planned dinner contains fish, which is excluded for 2026-11-16.'
FROM v_active_plan_item a WHERE a.item_key = 'b0000000-0000-4000-8000-000000000203';

-- Two proposals on version 1.
INSERT INTO recovery_proposal (id, daily_plan_id, base_version_no)
SELECT x.id::uuid, dp.id, 1
FROM (VALUES ('c0000000-0000-4000-8000-000000000001'), ('c0000000-0000-4000-8000-000000000002')) AS x (id)
JOIN daily_plan dp ON dp.user_id = 'a0000000-0000-4000-8000-000000000001' AND dp.plan_date = DATE '2026-11-16';

INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, item_type, meal_slot_code, planned_time,
                             meal_id, planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g,
                             reason_category, reason_text)
SELECT 'c0000000-0000-4000-8000-000000000001', 1, 'REPLACE', a.plan_item_id, 'MEAL', 'DINNER', '19:30',
       n.meal_id, n.kcal, n.protein_g, n.fat_g, n.carbs_g, 'INGREDIENT',
       'Salmon is unavailable today; the tofu stir-fry contains no fish, takes 20 minutes and keeps the remaining protein target.'
FROM v_active_plan_item a, v_meal_nutrition n
WHERE a.item_key = 'b0000000-0000-4000-8000-000000000203' AND n.meal_name = 'Tofu vegetable stir-fry';

INSERT INTO recovery_change (proposal_id, change_no, change_type, item_type, planned_time, exercise_id, duration_min,
                             reason_category, reason_text)
SELECT 'c0000000-0000-4000-8000-000000000001', 2, 'ADD', 'WORKOUT', '20:30', e.id, 20, 'TIME',
       'The 30-minute run was skipped; a 20-minute bodyweight circuit fits the 40 available minutes and needs no equipment.'
FROM exercise e WHERE e.name = 'Bodyweight circuit';

INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, item_type, meal_slot_code, planned_time,
                             meal_id, planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g,
                             reason_category, reason_text)
SELECT 'c0000000-0000-4000-8000-000000000002', 1, 'REPLACE', a.plan_item_id, 'MEAL', 'DINNER', '19:30',
       n.meal_id, n.kcal, n.protein_g, n.fat_g, n.carbs_g, 'NUTRITION',
       'Lunch exceeded the plan by 250 kcal; lentil soup is a lighter fish-free dinner.'
FROM v_active_plan_item a, v_meal_nutrition n
WHERE a.item_key = 'b0000000-0000-4000-8000-000000000203' AND n.meal_name = 'Lentil soup';

-- Anna accepts proposal 1 -> version 2; proposal 2 becomes STALE (its base version is no longer active).
CALL apply_recovery_decision('a0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
                             'ACCEPT', 1, NULL, NULL);

-- ---------------------------------------------------------------------------------------------
-- Max, 2026-11-16: deviation boundaries and REJECT
-- ---------------------------------------------------------------------------------------------
SELECT create_plan_version('a0000000-0000-4000-8000-000000000002', DATE '2026-11-16', 0, 'INITIAL_PLAN',
    (SELECT jsonb_agg(item) FROM (
        SELECT jsonb_build_object('itemKey', x.item_key, 'itemType', 'MEAL', 'mealSlot', x.slot, 'plannedTime', x.t,
                                  'mealId', n.meal_id, 'kcal', n.kcal, 'proteinG', n.protein_g, 'fatG', n.fat_g, 'carbsG', n.carbs_g) AS item
        FROM (VALUES ('b0000000-0000-4000-8000-000000000301', 'BREAKFAST', '07:30', 'Oatmeal with banana'),
                     ('b0000000-0000-4000-8000-000000000302', 'LUNCH',     '12:30', 'Pasta bolognese')) AS x (item_key, slot, t, meal_name)
        JOIN v_meal_nutrition n ON n.meal_name = x.meal_name
        UNION ALL
        SELECT jsonb_build_object('itemKey', 'b0000000-0000-4000-8000-000000000303', 'itemType', 'WORKOUT', 'plannedTime', '17:00',
                                  'exerciseId', (SELECT id FROM exercise WHERE name = 'Dumbbell strength training'), 'durationMin', 45)
    ) s));

-- Breakfast +199 kcal: below the 200 kcal threshold -> no deviation (US-15 boundary).
INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id,
                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'a0000000-0000-4000-8000-000000000002', DATE '2026-11-16', 'BREAKFAST', 'MANUAL', a.plan_item_id,
       'Large oatmeal with banana and honey', 520, a.planned_kcal + 199, 20.0, 12.0, 120.0
FROM v_active_plan_item a WHERE a.item_key = 'b0000000-0000-4000-8000-000000000301';

-- Lunch -200 kcal: exactly the threshold -> deviation (US-15 boundary).
INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id,
                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
SELECT 'a0000000-0000-4000-8000-000000000002', DATE '2026-11-16', 'LUNCH', 'MANUAL', a.plan_item_id,
       'Small pasta bolognese', 250, a.planned_kcal - 200, 22.0, 9.0, 40.0
FROM v_active_plan_item a WHERE a.item_key = 'b0000000-0000-4000-8000-000000000302';

UPDATE plan_item SET status = 'COMPLETED'
WHERE id IN (SELECT plan_item_id FROM v_active_plan_item
             WHERE item_key IN ('b0000000-0000-4000-8000-000000000301', 'b0000000-0000-4000-8000-000000000302'));

INSERT INTO recovery_proposal (id, daily_plan_id, base_version_no)
SELECT 'c0000000-0000-4000-8000-000000000003', dp.id, 1
FROM daily_plan dp WHERE dp.user_id = 'a0000000-0000-4000-8000-000000000002' AND dp.plan_date = DATE '2026-11-16';

INSERT INTO recovery_change (proposal_id, change_no, change_type, item_type, meal_slot_code, planned_time,
                             meal_id, planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g,
                             reason_category, reason_text)
SELECT 'c0000000-0000-4000-8000-000000000003', 1, 'ADD', 'MEAL', 'SNACK', '15:30',
       n.meal_id, n.kcal, n.protein_g, n.fat_g, n.carbs_g, 'NUTRITION',
       'Lunch was 200 kcal below the plan; a yogurt snack restores the remaining energy and protein budget.'
FROM v_meal_nutrition n WHERE n.meal_name = 'Greek yogurt with apple';

-- Max rejects: no new version, recoveryRequired stays TRUE.
CALL apply_recovery_decision('a0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000003',
                             'REJECT', 1, NULL, NULL);

-- ---------------------------------------------------------------------------------------------
-- Max, 2026-11-14: missed meal and EDIT (subset of changes)
-- ---------------------------------------------------------------------------------------------
SELECT create_plan_version('a0000000-0000-4000-8000-000000000002', DATE '2026-11-14', 0, 'INITIAL_PLAN',
    (SELECT jsonb_agg(item) FROM (
        SELECT jsonb_build_object('itemKey', x.item_key, 'itemType', 'MEAL', 'mealSlot', x.slot, 'plannedTime', x.t,
                                  'mealId', n.meal_id, 'kcal', n.kcal, 'proteinG', n.protein_g, 'fatG', n.fat_g, 'carbsG', n.carbs_g) AS item
        FROM (VALUES ('b0000000-0000-4000-8000-000000000401', 'BREAKFAST', '07:30', 'Scrambled eggs on toast'),
                     ('b0000000-0000-4000-8000-000000000402', 'LUNCH',     '12:30', 'Lentil soup'),
                     ('b0000000-0000-4000-8000-000000000403', 'DINNER',    '19:00', 'Tofu vegetable stir-fry')) AS x (item_key, slot, t, meal_name)
        JOIN v_meal_nutrition n ON n.meal_name = x.meal_name
        UNION ALL
        SELECT jsonb_build_object('itemKey', 'b0000000-0000-4000-8000-000000000404', 'itemType', 'WORKOUT', 'plannedTime', '18:00',
                                  'exerciseId', (SELECT id FROM exercise WHERE name = 'Jump rope'), 'durationMin', 15)
    ) s));

UPDATE plan_item SET status = 'MISSED'
WHERE id = (SELECT plan_item_id FROM v_active_plan_item WHERE item_key = 'b0000000-0000-4000-8000-000000000401');

INSERT INTO recovery_proposal (id, daily_plan_id, base_version_no)
SELECT 'c0000000-0000-4000-8000-000000000004', dp.id, 1
FROM daily_plan dp WHERE dp.user_id = 'a0000000-0000-4000-8000-000000000002' AND dp.plan_date = DATE '2026-11-14';

INSERT INTO recovery_change (id, proposal_id, change_no, change_type, target_item_id, item_type, meal_slot_code, planned_time,
                             meal_id, planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g,
                             reason_category, reason_text)
SELECT 'f0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000004', 1, 'REPLACE', a.plan_item_id,
       'MEAL', 'LUNCH', '12:30', n.meal_id, n.kcal, n.protein_g, n.fat_g, n.carbs_g, 'NUTRITION',
       'Breakfast was missed; the chicken rice bowl covers more of the remaining energy and protein.'
FROM v_active_plan_item a, v_meal_nutrition n
WHERE a.item_key = 'b0000000-0000-4000-8000-000000000402' AND n.meal_name = 'Chicken rice bowl';

INSERT INTO recovery_change (id, proposal_id, change_no, change_type, target_item_id, item_type, planned_time,
                             exercise_id, duration_min, reason_category, reason_text)
SELECT 'f0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000004', 2, 'REPLACE', a.plan_item_id,
       'WORKOUT', '18:00', e.id, 30, 'EQUIPMENT',
       'No jump rope is listed for today; brisk walking needs no equipment.'
FROM v_active_plan_item a, exercise e
WHERE a.item_key = 'b0000000-0000-4000-8000-000000000404' AND e.name = 'Brisk walking';

-- Max edits the proposal and applies only the lunch change -> version 2 (RECOVERY_EDITED).
CALL apply_recovery_decision('a0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000004',
                             'EDIT', 1, ARRAY['f0000000-0000-4000-8000-000000000001']::uuid[], NULL);

-- ---------------------------------------------------------------------------------------------
-- Olga: unplanned records (no plan), Unicode food name
-- ---------------------------------------------------------------------------------------------
INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
VALUES ('a0000000-0000-4000-8000-000000000004', DATE '2026-11-16', 'SNACK', 'MANUAL', 'Crème brûlée', 150, 450.0, 6.0, 33.0, 32.0);

INSERT INTO activity_entry (user_id, entry_date, exercise_id, status, duration_min)
SELECT 'a0000000-0000-4000-8000-000000000004', DATE '2026-11-16', e.id, 'COMPLETED', 25
FROM exercise e WHERE e.name = 'Brisk walking';

COMMIT;

-- Summary printed after loading.
SELECT 'users' AS what, count(*) AS rows FROM veya.app_user
UNION ALL SELECT 'plans', count(*) FROM veya.daily_plan
UNION ALL SELECT 'plan versions', count(*) FROM veya.plan_version
UNION ALL SELECT 'food entries', count(*) FROM veya.food_entry
UNION ALL SELECT 'deviation events', count(*) FROM veya.deviation_event
UNION ALL SELECT 'proposals', count(*) FROM veya.recovery_proposal
UNION ALL SELECT 'decisions', count(*) FROM veya.recovery_decision;
