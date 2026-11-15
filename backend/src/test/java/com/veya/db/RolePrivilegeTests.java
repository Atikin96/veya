package com.veya.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Least-privilege role model (V9 + infra/postgres/initdb). 42501 = insufficient_privilege. */
@DisplayName("Roles and privileges")
class RolePrivilegeTests extends DatabaseTestSupport {

    @Test
    @DisplayName("No VEYA role is a superuser or can create roles/databases; group roles cannot log in")
    void rolesAreNotPrivileged() {
        List<Map<String, Object>> roles = owner.queryForList("""
                SELECT rolname, rolsuper, rolcreaterole, rolcreatedb, rolcanlogin, rolbypassrls
                FROM pg_roles WHERE rolname LIKE 'veya%' ORDER BY rolname""");

        assertThat(roles).extracting(r -> r.get("rolname")).containsExactly(
                "veya_admin", "veya_admin_user", "veya_app", "veya_app_user", "veya_owner", "veya_readonly", "veya_readonly_user");
        assertThat(roles).allSatisfy(r -> {
            assertThat(r.get("rolsuper")).isEqualTo(false);
            assertThat(r.get("rolcreaterole")).isEqualTo(false);
            assertThat(r.get("rolcreatedb")).isEqualTo(false);
            assertThat(r.get("rolbypassrls")).isEqualTo(false);
            assertThat(r.get("rolcanlogin")).isEqualTo(((String) r.get("rolname")).endsWith("_user")
                    || r.get("rolname").equals("veya_owner"));
        });
    }

    @Test
    @DisplayName("veya_owner owns every table, view and VEYA routine in schema veya")
    void ownerOwnsAllObjects() {
        Integer foreign = owner.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'veya' AND c.relkind IN ('r', 'v') AND pg_get_userbyid(c.relowner) <> 'veya_owner'""",
                Integer.class);

        assertThat(foreign).isZero();
    }

    @Test
    @DisplayName("Every table/view has a privilege decision: app and admin can read it, read-only only the allow-list")
    void newObjectsCannotBeForgotten() {
        String objects = """
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'veya' AND c.relkind IN ('r', 'v') AND c.relname <> 'flyway_schema_history'
                  AND %s ORDER BY c.relname""";
        List<String> unreadableByApp = owner.queryForList(objects.formatted(
                "NOT has_table_privilege('veya_app', c.oid, 'SELECT') AND c.relname NOT IN ('v_app_user_masked', 'v_analytics_daily_nutrition')"),
                String.class);
        List<String> unreadableByAdmin = owner.queryForList(objects.formatted(
                "NOT has_table_privilege('veya_admin', c.oid, 'SELECT')"), String.class);
        List<String> readableByReadonly = owner.queryForList(objects.formatted(
                "has_table_privilege('veya_readonly', c.oid, 'SELECT')"), String.class);

        assertThat(unreadableByApp).isEmpty();
        assertThat(unreadableByAdmin).isEmpty();
        assertThat(readableByReadonly).containsExactly(
                "consent_document", "equipment", "exercise", "exercise_equipment", "food_item", "food_item_ingredient",
                "goal_type", "ingredient", "meal", "meal_component", "meal_slot", "recovery_setting",
                "v_analytics_daily_nutrition", "v_app_user_masked", "v_meal_nutrition");
    }

    @ParameterizedTest(name = "application role: {0}")
    @ValueSource(strings = {
            "CREATE TABLE veya.hacked (id int)",
            "DROP TABLE veya.food_entry",
            "ALTER TABLE veya.app_user ADD COLUMN note text",
            "TRUNCATE veya.food_entry",
            "CREATE TABLE public.hacked (id int)",
            "SELECT count(*) FROM veya.flyway_schema_history",
            "INSERT INTO veya.plan_version (daily_plan_id, version_no, change_reason) VALUES (gen_random_uuid(), 1, 'INITIAL_PLAN')",
            "INSERT INTO veya.plan_item (plan_version_id, item_key, item_type) VALUES (gen_random_uuid(), gen_random_uuid(), 'MEAL')",
            "UPDATE veya.plan_item SET planned_kcal = 1",
            "UPDATE veya.daily_plan SET recovery_required = false",
            "DELETE FROM veya.plan_version",
            "DELETE FROM veya.recovery_proposal",
            "INSERT INTO veya.recovery_decision (proposal_id, decision) VALUES (gen_random_uuid(), 'REJECT')",
            "UPDATE veya.deviation_event SET resolved_at = now()",
            "UPDATE veya.app_user SET created_at = now()",
            "DELETE FROM veya.food_item",
            "UPDATE veya.recovery_setting SET kcal_deviation_threshold = 1",
            "INSERT INTO veya.goal_type VALUES ('HACK', 'Hack')",
            "SELECT veya.evaluate_meal_deviation(gen_random_uuid())"
    })
    void applicationRoleIsDenied(String sql) {
        assertSqlState("42501", () -> app.execute(sql));
    }

    @Test
    @DisplayName("Application role performs its normal operations")
    void applicationRoleCanDoItsWork() {
        Plan plan = createPlan();

        logMeal(plan, plan.breakfast(), 0);
        setStatus(plan.breakfast(), "COMPLETED");
        app.update("UPDATE food_entry SET portion_g = 310 WHERE user_id = ?", plan.userId());
        app.update("UPDATE app_user SET password_hash = ? WHERE id = ?", OTHER_HASH, plan.userId());

        assertThat(app.queryForObject("SELECT count(*) FROM v_day_summary WHERE user_id = ?", Integer.class, plan.userId()))
                .isEqualTo(1);
        assertThat(app.queryForObject("SELECT export_user_data(?) IS NOT NULL", Boolean.class, plan.userId())).isTrue();
    }

    @ParameterizedTest(name = "read-only role: {0}")
    @ValueSource(strings = {
            "SELECT count(*) FROM veya.app_user",
            "SELECT count(*) FROM veya.food_entry",
            "SELECT count(*) FROM veya.user_profile",
            "SELECT count(*) FROM veya.daily_plan",
            "SELECT count(*) FROM veya.user_consent",
            "SELECT count(*) FROM veya.v_day_summary",
            "SELECT veya.export_user_data(gen_random_uuid())",
            "SELECT veya.create_plan_version(gen_random_uuid(), current_date, 0, 'INITIAL_PLAN', '[]')"
    })
    void readOnlyRoleCannotReadPersonalData(String sql) {
        assertSqlState("42501", () -> readonly.execute(sql));
    }

    @Test
    @DisplayName("Read-only role cannot write: read-only transaction and no write privileges")
    void readOnlyRoleCannotWrite() {
        assertThatThrownBy(() -> readonly.update("INSERT INTO goal_type VALUES ('HACK', 'Hack')"))
                .satisfies(e -> assertThat(sqlState(e)).isIn("25006", "42501"));
        assertThatThrownBy(() -> readonly.update("UPDATE food_item SET name = name"))
                .satisfies(e -> assertThat(sqlState(e)).isIn("25006", "42501"));
        assertThat(readonly.queryForObject("SHOW default_transaction_read_only", String.class)).isEqualTo("on");
    }

    @Test
    @DisplayName("Read-only role reads reference data and masked/anonymised views")
    void readOnlyRoleReadsReportingData() {
        UUID user = createUser();
        String email = app.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, user);

        assertThat(readonly.queryForObject("SELECT count(*) FROM meal_slot", Integer.class)).isEqualTo(4);
        assertThat(readonly.queryForObject("SELECT count(*) FROM v_meal_nutrition", Integer.class)).isEqualTo(12);
        List<String> masked = readonly.queryForList("SELECT email_masked FROM v_app_user_masked", String.class);
        assertThat(masked).isNotEmpty().doesNotContain(email).allSatisfy(m -> assertThat(m).contains("***@"));
        assertThat(readonly.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("Admin role has full DML but no DDL and cannot rewrite migration history")
    void adminRole() {
        UUID user = createUser();

        assertThat(admin.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Integer.class, user)).isEqualTo(1);
        assertThat(admin.update("UPDATE user_profile SET energy_target_kcal = 2100 WHERE user_id = ?", user)).isEqualTo(1);
        assertSqlState("42501", () -> admin.execute("CREATE TABLE veya.admin_table (id int)"));
        assertSqlState("42501", () -> admin.execute("DROP VIEW veya.v_day_summary"));
        assertSqlState("42501", () -> admin.update("DELETE FROM flyway_schema_history"));
    }

    @Test
    @DisplayName("Admin cannot bypass integrity triggers: plan history stays immutable")
    void adminCannotRewriteHistory() {
        Plan plan = createPlan();

        assertSqlState("VY001", () -> admin.update("UPDATE plan_version SET change_reason = 'MEAL_REPLACEMENT' WHERE daily_plan_id = ?", planId(plan)));
        assertSqlState("VY001", () -> admin.update("UPDATE plan_item SET planned_kcal = 1 WHERE id = ?", activeItem(plan.lunch())));
        assertSqlState("VY001", () -> admin.update("UPDATE daily_plan SET current_version_no = 5 WHERE id = ?", planId(plan)));
        assertSqlState("VY001", () -> admin.update("UPDATE consent_document SET summary = 'changed'"));
    }
}
