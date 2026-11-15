-- Generates docs/database/data-dictionary.md from the live catalog (table/column comments come from the
-- Flyway migrations). Regenerate after every schema migration:
--
--   docker exec veya-postgres sh -c 'PGPASSWORD="$VEYA_ADMIN_PASSWORD" psql -X -q -At -h localhost \
--       -U veya_admin_user -d veya -f /veya/scripts/data_dictionary.sql' > docs/database/data-dictionary.md
--
-- The integration test DataDictionaryTests fails when a table or column is missing from the document.

SET search_path = veya;

WITH tbl AS (
    SELECT c.oid, c.relname, obj_description(c.oid, 'pg_class') AS descr
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'veya' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
),
vw AS (
    SELECT c.oid, c.relname, obj_description(c.oid, 'pg_class') AS descr
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'veya' AND c.relkind = 'v'
),
fn AS (
    SELECT p.oid, p.proname, p.prokind, p.prosecdef, pg_get_function_identity_arguments(p.oid) AS args,
           obj_description(p.oid, 'pg_proc') AS descr
    FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    LEFT JOIN pg_depend dep ON dep.objid = p.oid AND dep.deptype = 'e'
    WHERE n.nspname = 'veya' AND dep.objid IS NULL
),
trg AS (
    SELECT t.tgname, c.relname, p.proname,
           CASE WHEN t.tgtype & 2 = 2 THEN 'BEFORE' ELSE 'AFTER' END AS timing,
           concat_ws(' OR ',
               CASE WHEN t.tgtype & 4 = 4 THEN 'INSERT' END,
               CASE WHEN t.tgtype & 8 = 8 THEN 'DELETE' END,
               CASE WHEN t.tgtype & 16 = 16 THEN 'UPDATE' END) AS events
    FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_proc p ON p.oid = t.tgfoid
    WHERE n.nspname = 'veya' AND NOT t.tgisinternal
),
table_blocks AS (
    SELECT t.relname,
           format(E'### `%s`\n\n%s\n\n| Column | Type | Null | Default | Keys | Description |\n|---|---|---|---|---|---|\n%s\n\n**Constraints**\n\n%s\n\n**Indexes**\n\n%s\n',
                  t.relname, coalesce(t.descr, '_(no description)_'), cols.txt, cons.txt, idx.txt) AS block
    FROM tbl t
    CROSS JOIN LATERAL (
        SELECT string_agg(format('| `%s` | %s | %s | %s | %s | %s |',
                   a.attname,
                   format_type(a.atttypid, a.atttypmod),
                   CASE WHEN a.attnotnull THEN 'NOT NULL' ELSE 'NULL' END,
                   CASE WHEN a.attidentity = 'a' THEN 'identity (always)'
                        WHEN d.adbin IS NOT NULL THEN '`' || pg_get_expr(d.adbin, d.adrelid) || '`'
                        ELSE '' END,
                   coalesce(k.keys, ''),
                   replace(coalesce(col_description(t.oid, a.attnum), ''), '|', '\|')),
                   E'\n' ORDER BY a.attnum) AS txt
        FROM pg_attribute a
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        LEFT JOIN LATERAL (
            SELECT string_agg(CASE con.contype
                                  WHEN 'p' THEN 'PK'
                                  WHEN 'u' THEN 'UQ'
                                  WHEN 'f' THEN 'FK → `' || con.confrelid::regclass::text || '`'
                              END, ', ' ORDER BY con.contype DESC) AS keys
            FROM pg_constraint con
            WHERE con.conrelid = t.oid AND con.contype IN ('p', 'u', 'f') AND a.attnum = ANY (con.conkey)
        ) k ON true
        WHERE a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped
    ) cols
    CROSS JOIN LATERAL (
        SELECT coalesce(string_agg(format('- `%s` (%s): `%s`', con.conname,
                   CASE con.contype WHEN 'p' THEN 'primary key' WHEN 'u' THEN 'unique' WHEN 'f' THEN 'foreign key'
                                    WHEN 'c' THEN 'check' ELSE con.contype::text END,
                   replace(pg_get_constraintdef(con.oid), '|', '\|')),
                   E'\n' ORDER BY con.contype DESC, con.conname), '- none') AS txt
        FROM pg_constraint con WHERE con.conrelid = t.oid
    ) cons
    CROSS JOIN LATERAL (
        SELECT coalesce(string_agg(format('- `%s`', pg_get_indexdef(i.indexrelid)), E'\n' ORDER BY ic.relname), '- none') AS txt
        FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid
        WHERE i.indrelid = t.oid
    ) idx
),
view_blocks AS (
    SELECT v.relname,
           format(E'### `%s`\n\n%s\n\nColumns: %s\n', v.relname, coalesce(v.descr, '_(no description)_'),
                  (SELECT string_agg(format('`%s` (%s)', a.attname, format_type(a.atttypid, a.atttypmod)), ', ' ORDER BY a.attnum)
                   FROM pg_attribute a WHERE a.attrelid = v.oid AND a.attnum > 0)) AS block
    FROM vw v
)
SELECT concat_ws(E'\n',
    '# VEYA data dictionary',
    '',
    '> Generated from the PostgreSQL 17 catalog of schema `veya` by `infra/postgres/scripts/data_dictionary.sql`.',
    '> Descriptions are the `COMMENT ON` texts from the Flyway migrations. Do not edit by hand - regenerate.',
    '',
    format('Schema version: **V%s** (Flyway). Tables: **%s**, views: **%s**, routines: **%s**, triggers: **%s**.',
           (SELECT max(version::int) FROM flyway_schema_history WHERE success),
           (SELECT count(*) FROM tbl), (SELECT count(*) FROM vw), (SELECT count(*) FROM fn), (SELECT count(*) FROM trg)),
    '',
    'Conventions: `uuid` keys for user-owned records (not guessable), identity `bigint`/`smallint` keys for catalogue',
    'rows, natural code keys for reference vocabularies; `timestamptz` for instants, `date`/`time` for local plan',
    'dates and times; `numeric` for nutrition values (exact decimal arithmetic).',
    '',
    '**Layers.** One normalized OLTP layer (schema `veya`). There is no raw/staging/data-mart pipeline: VEYA has',
    'no bulk ingestion or analytics workload in the MVP, so extra layers are not justified. Read models are views',
    '(application views; masked/anonymised reporting views for `veya_readonly`). JSON is not stored in tables -',
    'it is only a parameter format (`create_plan_version`) and the LEG-4 export result.',
    '',
    '## Data quality expectations',
    '',
    '| Dimension | Expectation | How it is enforced |',
    '|---|---|---|',
    '| Completeness | Mandatory attributes are always present (e-mail, adulthood confirmation, goal and targets, entry date, portion and confirmed nutrients, item shape by type, change reason). | `NOT NULL`; shape `CHECK`s (`plan_item_meal_shape`, `plan_item_workout_shape`, `recovery_change_*_shape`, `activity_entry_duration_by_status`). |',
    '| Validity | Values lie in physically meaningful ranges and formats: kcal 0-10000 per entry, 0-900 per 100 g; macros <= 100 g per 100 g; portion 0-5000 g; duration 1-600 min; available time 0-1440 min; MET 1-25; e-mail format; adaptive password hash only; codes `^[A-Z][A-Z0-9_]+$`; statuses from fixed BA lists. | `CHECK` constraints; `CHECK`s are written so that `NULL` cannot pass. |',
    '| Uniqueness | One account per e-mail (case-insensitive), one profile per user, one plan and one constraint set per user and date, gap-free version numbers, one decision per proposal, one active consent per document, no duplicate open deviation per item. | `PRIMARY KEY`, `UNIQUE`, partial unique indexes. |',
    '| Referential integrity | Every reference points to an existing row; personal data is erased with the account; catalogue rows in use cannot be deleted; actual records survive plan erasure (unlinked). | `FOREIGN KEY` with explicit `CASCADE` / `RESTRICT` / `SET NULL`. |',
    '| Consistency | Diary entries refer only to active-version items of the same user and date; plan versions are immutable and sequential; completed items are frozen; `recoveryRequired` always reflects significant deviations (US-15). | Triggers and routines (V7), see "Functions and procedures" and "Triggers". |',
    '| Accuracy / provenance | Only user-confirmed values are stored as actual records (US-06); catalogue and MET values are estimates (LEG-6) with their `source` (LOCAL/PROVIDER) and `fetched_at`; planned values are snapshots of their version. | `input_source`, `source`, `external_ref`, snapshot columns. |',
    '| Timeliness / audit | Creation and last-change instants are recorded; decisions, versions and deviations keep their timestamps. | `created_at`, `updated_at` (trigger), `decided_at`, `detected_at`/`resolved_at`. |',
    '| Privacy | Direct identifiers (`app_user.email`, `password_hash`) are visible only to the application and admin roles; reporting uses masked/pseudonymised views. | Privileges (V9), `v_app_user_masked`, `v_analytics_daily_nutrition`. |',
    '',
    '## Tables',
    '',
    (SELECT string_agg(format('- [`%s`](#%s) — %s', relname, relname, coalesce(descr, '')), E'\n' ORDER BY relname) FROM tbl),
    '',
    (SELECT string_agg(block, E'\n' ORDER BY relname) FROM table_blocks),
    '## Views',
    '',
    (SELECT string_agg(block, E'\n' ORDER BY relname) FROM view_blocks),
    '## Functions and procedures',
    '',
    '| Routine | Kind | Security | Description |',
    '|---|---|---|---|',
    (SELECT string_agg(format('| `%s(%s)` | %s | %s | %s |', proname, args,
                              CASE prokind WHEN 'p' THEN 'procedure' ELSE 'function' END,
                              CASE WHEN prosecdef THEN 'DEFINER' ELSE 'INVOKER' END,
                              replace(coalesce(descr, CASE WHEN EXISTS (SELECT 1 FROM trg WHERE trg.proname = fn.proname)
                                                           THEN 'Trigger function (see triggers below).' ELSE '' END), '|', '\|')),
                       E'\n' ORDER BY proname) FROM fn),
    '',
    '## Triggers',
    '',
    '| Trigger | Table | Timing | Events | Function |',
    '|---|---|---|---|---|',
    (SELECT string_agg(format('| `%s` | `%s` | %s | %s | `%s` |', tgname, relname, timing, events, proname),
                       E'\n' ORDER BY relname, tgname) FROM trg),
    ''
);
