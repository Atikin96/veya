-- V8: views.
-- Application views (granted to veya_app): active plan, nutrition summary, day summary, meal nutrition,
-- proposal overview. Reporting views (granted to veya_readonly): masked users and anonymised daily
-- nutrition - they run with the owner's privileges, so read-only users never need the base tables.

-- Nutrition of catalogue meals, derived from components (keeps meal itself in 3NF).
CREATE VIEW v_meal_nutrition AS
SELECT m.id                                                      AS meal_id,
       m.name                                                    AS meal_name,
       m.meal_slot_code,
       m.prep_time_min,
       round(sum(f.kcal_per_100g * mc.grams / 100), 1)           AS kcal,
       round(sum(f.protein_per_100g * mc.grams / 100), 1)        AS protein_g,
       round(sum(f.fat_per_100g * mc.grams / 100), 1)            AS fat_g,
       round(sum(f.carbs_per_100g * mc.grams / 100), 1)          AS carbs_g,
       (SELECT coalesce(array_agg(DISTINCT fi.ingredient_code ORDER BY fi.ingredient_code), '{}')
        FROM meal_component mc2 JOIN food_item_ingredient fi ON fi.food_item_id = mc2.food_item_id
        WHERE mc2.meal_id = m.id)                                AS ingredient_codes
FROM meal m
JOIN meal_component mc ON mc.meal_id = m.id
JOIN food_item f ON f.id = mc.food_item_id
GROUP BY m.id, m.name, m.meal_slot_code, m.prep_time_min;
COMMENT ON VIEW v_meal_nutrition IS 'Candidate meal nutrition and ingredient codes derived from meal_component (US-09, US-10).';

-- Items of the active version of every plan (US-20 "active plan version").
CREATE VIEW v_active_plan_item AS
SELECT dp.user_id,
       dp.id          AS daily_plan_id,
       dp.plan_date,
       pv.id          AS plan_version_id,
       pv.version_no,
       pi.id          AS plan_item_id,
       pi.item_key,
       pi.item_type,
       pi.meal_slot_code,
       pi.planned_time,
       pi.meal_id,
       m.name         AS meal_name,
       pi.exercise_id,
       e.name         AS exercise_name,
       pi.planned_kcal,
       pi.planned_protein_g,
       pi.planned_fat_g,
       pi.planned_carbs_g,
       pi.duration_min,
       pi.status
FROM daily_plan dp
JOIN plan_version pv ON pv.daily_plan_id = dp.id AND pv.version_no = dp.current_version_no
JOIN plan_item pi ON pi.plan_version_id = pv.id
LEFT JOIN meal m ON m.id = pi.meal_id
LEFT JOIN exercise e ON e.id = pi.exercise_id;
COMMENT ON VIEW v_active_plan_item IS 'Items of the active version of each daily plan.';

-- Consumed vs target vs remaining nutrition per user and day (US-07).
CREATE VIEW v_daily_nutrition_summary AS
SELECT f.user_id,
       f.entry_date,
       sum(f.kcal)                                  AS consumed_kcal,
       sum(f.protein_g)                             AS consumed_protein_g,
       sum(f.fat_g)                                 AS consumed_fat_g,
       sum(f.carbs_g)                               AS consumed_carbs_g,
       p.energy_target_kcal                         AS target_kcal,
       p.protein_target_g                           AS target_protein_g,
       p.fat_target_g                               AS target_fat_g,
       p.carbs_target_g                             AS target_carbs_g,
       p.energy_target_kcal - sum(f.kcal)           AS remaining_kcal,
       p.protein_target_g - sum(f.protein_g)        AS remaining_protein_g,
       p.fat_target_g - sum(f.fat_g)                AS remaining_fat_g,
       p.carbs_target_g - sum(f.carbs_g)            AS remaining_carbs_g,
       count(*)                                     AS entry_count
FROM food_entry f
LEFT JOIN user_profile p ON p.user_id = f.user_id
GROUP BY f.user_id, f.entry_date, p.energy_target_kcal, p.protein_target_g, p.fat_target_g, p.carbs_target_g;
COMMENT ON VIEW v_daily_nutrition_summary IS 'US-07: confirmed consumption, targets and remaining amounts per user and date (only days with entries).';

-- Dashboard of a planned day (US-20, Q10: active version only, no history browsing).
CREATE VIEW v_day_summary AS
SELECT dp.user_id,
       dp.id                                        AS daily_plan_id,
       dp.plan_date,
       dp.current_version_no                        AS active_version_no,
       dp.recovery_required,
       coalesce(pm.planned_kcal, 0)                 AS planned_kcal,
       coalesce(ns.consumed_kcal, 0)                AS consumed_kcal,
       coalesce(ns.consumed_protein_g, 0)           AS consumed_protein_g,
       coalesce(ns.consumed_fat_g, 0)               AS consumed_fat_g,
       coalesce(ns.consumed_carbs_g, 0)             AS consumed_carbs_g,
       up.energy_target_kcal                        AS target_kcal,
       up.energy_target_kcal - coalesce(ns.consumed_kcal, 0) AS remaining_kcal,
       coalesce(pm.planned_workout_min, 0)          AS planned_workout_min,
       coalesce(ac.completed_workout_min, 0)        AS completed_workout_min,
       coalesce(pm.open_items, 0)                   AS open_items
FROM daily_plan dp
LEFT JOIN user_profile up ON up.user_id = dp.user_id
LEFT JOIN v_daily_nutrition_summary ns ON ns.user_id = dp.user_id AND ns.entry_date = dp.plan_date
LEFT JOIN LATERAL (
    SELECT sum(pi.planned_kcal)                                        AS planned_kcal,
           sum(pi.duration_min)                                        AS planned_workout_min,
           count(*) FILTER (WHERE pi.status = 'PLANNED')               AS open_items
    FROM plan_version pv JOIN plan_item pi ON pi.plan_version_id = pv.id
    WHERE pv.daily_plan_id = dp.id AND pv.version_no = dp.current_version_no
) pm ON true
LEFT JOIN LATERAL (
    SELECT sum(a.duration_min) AS completed_workout_min
    FROM activity_entry a
    WHERE a.user_id = dp.user_id AND a.entry_date = dp.plan_date AND a.status IN ('COMPLETED', 'CHANGED')
) ac ON true;
COMMENT ON VIEW v_day_summary IS 'US-20 day dashboard: active version, planned vs consumed nutrition, remaining energy, workout minutes, open items, recoveryRequired.';

-- Proposal status derived from decision and plan version (never stored, cannot become inconsistent).
CREATE VIEW v_recovery_proposal_overview AS
SELECT rp.id                     AS proposal_id,
       rp.daily_plan_id,
       dp.user_id,
       dp.plan_date,
       rp.base_version_no,
       dp.current_version_no,
       CASE
           WHEN rd.decision = 'ACCEPT' THEN 'ACCEPTED'
           WHEN rd.decision = 'EDIT' THEN 'EDITED'
           WHEN rd.decision = 'REJECT' THEN 'REJECTED'
           WHEN rp.base_version_no <> dp.current_version_no THEN 'STALE'
           ELSE 'PENDING'
       END                       AS status,
       (SELECT count(*) FROM recovery_change c WHERE c.proposal_id = rp.id) AS change_count,
       rp.created_at,
       rd.decided_at
FROM recovery_proposal rp
JOIN daily_plan dp ON dp.id = rp.daily_plan_id
LEFT JOIN recovery_decision rd ON rd.proposal_id = rp.id;
COMMENT ON VIEW v_recovery_proposal_overview IS 'Proposal status (PENDING, STALE, ACCEPTED, EDITED, REJECTED) derived from the decision and the active version.';

-- Reporting: users with masked e-mail and no credentials (dynamic masking for veya_readonly).
CREATE VIEW v_app_user_masked AS
SELECT pseudonym(u.id)                                   AS user_ref,
       mask_email(u.email)                               AS email_masked,
       date_trunc('month', u.created_at)::date           AS registered_month,
       (p.user_id IS NOT NULL)                           AS has_profile,
       p.goal_code,
       (SELECT count(*) FROM user_consent c WHERE c.user_id = u.id AND c.withdrawn_at IS NULL) AS active_consents
FROM app_user u
LEFT JOIN user_profile p ON p.user_id = u.id;
COMMENT ON VIEW v_app_user_masked IS 'Masked user list for read-only reporting: pseudonym, masked e-mail, registration month; no id, e-mail or password hash.';

-- Reporting: anonymised daily nutrition (pseudonym instead of user id).
CREATE VIEW v_analytics_daily_nutrition AS
SELECT pseudonym(ns.user_id) AS user_ref,
       ns.entry_date,
       ns.consumed_kcal,
       ns.consumed_protein_g,
       ns.consumed_fat_g,
       ns.consumed_carbs_g,
       ns.target_kcal,
       ns.entry_count
FROM v_daily_nutrition_summary ns;
COMMENT ON VIEW v_analytics_daily_nutrition IS 'Pseudonymised daily nutrition totals for reporting (LEG-8); contains no user id or e-mail.';
