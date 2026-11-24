package com.veya.db;

import com.veya.VeyaTestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Schema: creation, keys, documentation and reference data")
class SchemaStructureTests extends DatabaseTestSupport {

    private static final List<String> EXPECTED_TABLES = List.of(
            "activity_entry", "app_user", "consent_document", "daily_constraint", "daily_constraint_equipment",
            "daily_constraint_ingredient", "daily_plan", "deviation_event", "equipment", "exercise",
            "exercise_equipment", "food_entry", "food_item", "food_item_ingredient", "goal_type", "ingredient",
            "meal", "meal_component", "meal_slot", "plan_item", "plan_version", "profile_food_exclusion",
            "recovery_change", "recovery_decision", "recovery_proposal", "recovery_setting", "saved_meal",
            "saved_meal_item", "user_consent", "user_profile");

    private static final List<String> EXPECTED_VIEWS = List.of(
            "v_active_plan_item", "v_analytics_daily_nutrition", "v_app_user_masked", "v_daily_nutrition_summary",
            "v_day_summary", "v_meal_nutrition", "v_recovery_proposal_overview");

    @Test
    @DisplayName("Flyway applied V1..V10 successfully; V1 baseline is preserved")
    void allMigrationsApplied() {
        List<String> versions = owner.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    }

    @Test
    @DisplayName("All expected tables and views exist in schema veya, nothing in public")
    void expectedTablesAndViewsExist() {
        List<String> tables = owner.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'veya' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
                ORDER BY table_name""", String.class);
        List<String> views = owner.queryForList(
                "SELECT table_name FROM information_schema.views WHERE table_schema = 'veya' ORDER BY table_name", String.class);
        Integer publicTables = owner.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);

        assertThat(tables).containsExactlyElementsOf(EXPECTED_TABLES);
        assertThat(views).containsExactlyElementsOf(EXPECTED_VIEWS);
        assertThat(publicTables).isZero();
    }

    @Test
    @DisplayName("Every table has a primary key")
    void everyTableHasPrimaryKey() {
        List<String> withoutPk = owner.queryForList("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'veya' AND c.relkind = 'r'
                  AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = c.oid AND k.contype = 'p')""", String.class);

        assertThat(withoutPk).isEmpty();
    }

    @Test
    @DisplayName("Every foreign key of a user-data table is supported by an index whose leading columns match")
    void foreignKeysOnUserDataAreIndexed() {
        List<String> unindexed = owner.queryForList("""
                SELECT con.conrelid::regclass || '.' || con.conname
                FROM pg_constraint con JOIN pg_namespace n ON n.oid = con.connamespace
                WHERE n.nspname = 'veya' AND con.contype = 'f'
                  AND con.confrelid::regclass::text IN ('app_user', 'user_profile', 'daily_plan', 'plan_version',
                                                        'recovery_proposal', 'saved_meal', 'daily_constraint', 'plan_item')
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_index i
                      WHERE i.indrelid = con.conrelid
                        AND (SELECT array_agg(k ORDER BY ord)
                             FROM unnest(i.indkey::int2[]) WITH ORDINALITY AS u (k, ord)
                             WHERE ord <= array_length(con.conkey, 1)) = con.conkey)""", String.class);

        assertThat(unindexed).isEmpty();
    }

    @Test
    @DisplayName("Every table and column has a description (source of the data dictionary)")
    void everyTableAndColumnIsCommented() {
        List<String> missing = owner.queryForList("""
                SELECT c.relname || coalesce('.' || a.attname, '')
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                WHERE n.nspname = 'veya' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
                  AND (obj_description(c.oid, 'pg_class') IS NULL OR col_description(c.oid, a.attnum) IS NULL)""", String.class);

        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("docs/database/data-dictionary.md documents every table and column")
    void dataDictionaryIsUpToDate() throws IOException {
        String dictionary = Files.readString(VeyaTestDatabase.repositoryFile("docs/database/data-dictionary.md"));
        List<Map<String, Object>> columns = owner.queryForList("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = 'veya' AND table_name IN (
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'veya' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history')""");

        for (String table : EXPECTED_TABLES) {
            assertThat(dictionary).as("section for table %s", table).contains("### `" + table + "`");
        }
        for (Map<String, Object> column : columns) {
            String table = (String) column.get("table_name");
            int start = dictionary.indexOf("### `" + table + "`");
            int end = dictionary.indexOf("\n### ", start + 1);
            String section = dictionary.substring(start, end < 0 ? dictionary.length() : end);
            assertThat(section).as("column %s.%s", table, column.get("column_name"))
                    .contains("| `" + column.get("column_name") + "` |");
        }
    }

    @Test
    @DisplayName("Reference data is loaded by migration V10")
    void referenceDataLoaded() {
        assertThat(app.queryForObject("SELECT kcal_deviation_threshold FROM recovery_setting", Integer.class)).isEqualTo(200);
        assertThat(app.queryForList("SELECT code FROM meal_slot ORDER BY sort_order", String.class))
                .containsExactly("BREAKFAST", "LUNCH", "DINNER", "SNACK");
        // V10 rows only: provider products cached at runtime (source PROVIDER) are not reference data
        assertThat(app.queryForObject("SELECT count(*) FROM food_item WHERE source = 'LOCAL'", Integer.class)).isEqualTo(24);
        assertThat(app.queryForObject("SELECT count(*) FROM meal", Integer.class)).isEqualTo(12);
        assertThat(app.queryForObject("SELECT count(*) FROM exercise", Integer.class)).isEqualTo(12);
        assertThat(app.queryForObject("SELECT count(*) FROM consent_document", Integer.class)).isEqualTo(2);
        // Every meal has components and therefore derived nutrition.
        assertThat(app.queryForObject("SELECT count(*) FROM v_meal_nutrition WHERE kcal > 0", Integer.class)).isEqualTo(12);
    }

    @Test
    @DisplayName("The trigram index serves case-insensitive food search (US-05)")
    void foodSearchUsesTrigramIndex() {
        List<String> plan = app.queryForList("""
                EXPLAIN (COSTS OFF)
                SELECT id, name FROM food_item WHERE lower(name) LIKE '%' || lower('CHICK') || '%'""", String.class);
        // Small catalogue: force index use to prove the index matches the query shape.
        List<String> forced = app.execute((java.sql.Connection c) -> {
            try (var st = c.createStatement()) {
                st.execute("SET enable_seqscan = off");
                try (var rs = st.executeQuery("EXPLAIN (COSTS OFF) SELECT id FROM food_item WHERE lower(name) LIKE '%chick%'")) {
                    List<String> lines = new java.util.ArrayList<>();
                    while (rs.next()) {
                        lines.add(rs.getString(1));
                    }
                    return lines;
                } finally {
                    st.execute("RESET enable_seqscan");
                }
            }
        });
        Integer found = app.queryForObject(
                "SELECT count(*) FROM food_item WHERE lower(name) LIKE '%' || lower('CHICK') || '%'", Integer.class);

        assertThat(plan).isNotEmpty();
        assertThat(String.join("\n", forced)).contains("food_item_name_trgm_idx");
        assertThat(found).isEqualTo(1);
    }
}
