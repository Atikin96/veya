-- Read-only health check of a migrated VEYA database. Run as any VEYA login, e.g.:
--
--   docker exec veya-postgres sh -c 'PGPASSWORD="$VEYA_ADMIN_PASSWORD" psql -X -h localhost \
--       -U veya_admin_user -d veya -f /veya/scripts/verify_database.sql'

\echo '== 1. Flyway migrations (expect V1..V10, all successful)'
SELECT version, description, installed_by, success
FROM veya.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;

\echo '== 2. Roles (expect: no superuser, group roles cannot log in)'
SELECT rolname, rolsuper, rolcanlogin, rolcreaterole, rolcreatedb,
       (SELECT string_agg(b.rolname, ',') FROM pg_auth_members m JOIN pg_roles b ON b.oid = m.roleid WHERE m.member = r.oid) AS member_of
FROM pg_roles r WHERE rolname LIKE 'veya%' ORDER BY rolname;

\echo '== 3. Privilege matrix (selected objects)'
SELECT t.obj,
       has_table_privilege('veya_app', t.obj, 'SELECT')      AS app_select,
       has_table_privilege('veya_app', t.obj, 'INSERT')      AS app_insert,
       has_table_privilege('veya_app', t.obj, 'UPDATE')      AS app_update_table,   -- column-level grants (e.g. plan_item.status) show as false here
       has_table_privilege('veya_app', t.obj, 'DELETE')      AS app_delete,
       has_table_privilege('veya_readonly', t.obj, 'SELECT') AS ro_select,
       has_table_privilege('veya_admin', t.obj, 'DELETE')    AS admin_delete
FROM (VALUES ('veya.app_user'), ('veya.food_entry'), ('veya.plan_version'), ('veya.plan_item'),
             ('veya.recovery_decision'), ('veya.meal'), ('veya.v_app_user_masked'), ('veya.v_day_summary')) AS t (obj);

\echo '== 4. Objects in schema veya'
SELECT CASE c.relkind WHEN 'r' THEN 'table' WHEN 'v' THEN 'view' WHEN 'i' THEN 'index' END AS kind, count(*)
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'veya' AND c.relkind IN ('r', 'v', 'i') GROUP BY 1 ORDER BY 1;
SELECT count(*) AS triggers FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'veya' AND NOT t.tgisinternal;

\echo '== 5. Row counts'
SELECT 'app_user' AS table_name, count(*) FROM veya.app_user
UNION ALL SELECT 'food_item', count(*) FROM veya.food_item
UNION ALL SELECT 'meal', count(*) FROM veya.meal
UNION ALL SELECT 'exercise', count(*) FROM veya.exercise
UNION ALL SELECT 'daily_plan', count(*) FROM veya.daily_plan
UNION ALL SELECT 'plan_version', count(*) FROM veya.plan_version
UNION ALL SELECT 'food_entry', count(*) FROM veya.food_entry
UNION ALL SELECT 'deviation_event', count(*) FROM veya.deviation_event
UNION ALL SELECT 'recovery_proposal', count(*) FROM veya.recovery_proposal;

\echo '== 6. Masked reporting view (what veya_readonly sees)'
SELECT * FROM veya.v_app_user_masked ORDER BY user_ref;

\echo '== 7. Day dashboard (US-20) and proposal status'
SELECT plan_date, active_version_no, recovery_required, planned_kcal, consumed_kcal, remaining_kcal, open_items
FROM veya.v_day_summary ORDER BY plan_date, active_version_no;
SELECT plan_date, base_version_no, current_version_no, status, change_count
FROM veya.v_recovery_proposal_overview ORDER BY plan_date, created_at;
