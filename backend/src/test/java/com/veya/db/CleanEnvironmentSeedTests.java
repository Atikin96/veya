package com.veya.db;

import com.veya.VeyaTestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproducibility from a clean environment (US-32, NFR-17): a brand-new PostgreSQL 17 container is provisioned
 * by the init script, migrated by Flyway as veya_owner and loaded with infra/postgres/seed/demo_data.sql through
 * psql as veya_admin_user - the same commands as in README. The demo scenarios are then verified.
 */
@DisplayName("Clean environment: roles + migrations + demo seed")
class CleanEnvironmentSeedTests {

    private static PostgreSQLContainer container;
    private static JdbcTemplate admin;
    private static ExecResult seedResult;

    @BeforeAll
    static void provisionFreshDatabase() throws Exception {
        container = VeyaTestDatabase.newContainer();
        container.start();
        VeyaTestDatabase.migrate(container);
        seedResult = container.execInContainer("sh", "-c",
                "PGPASSWORD='" + VeyaTestDatabase.ADMIN_PASSWORD + "' psql -v ON_ERROR_STOP=1 -h localhost "
                        + "-U " + VeyaTestDatabase.ADMIN_USER + " -d " + VeyaTestDatabase.DATABASE + " -f /veya/seed/demo_data.sql");
        admin = new JdbcTemplate(VeyaTestDatabase.dataSource(container, VeyaTestDatabase.ADMIN_USER, VeyaTestDatabase.ADMIN_PASSWORD));
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @DisplayName("The seed script loads without errors as a non-superuser")
    void seedLoads() {
        assertThat(seedResult.getExitCode()).as(seedResult.getStderr()).isZero();
        assertThat(seedResult.getStdout()).contains("COMMIT");
    }

    @Test
    @DisplayName("Seed row counts are deterministic")
    void rowCounts() {
        Map<String, Integer> expected = Map.of(
                "app_user", 4, "user_profile", 3, "user_consent", 7, "daily_plan", 4, "plan_version", 6,
                "food_entry", 16, "activity_entry", 3, "deviation_event", 5, "recovery_proposal", 4, "recovery_decision", 3);
        expected.forEach((table, rows) ->
                assertThat(admin.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isEqualTo(rows));
    }

    @Test
    @DisplayName("Demo scenarios: ACCEPTED, STALE, REJECTED and EDITED proposals")
    void proposalScenarios() {
        List<String> statuses = admin.queryForList(
                "SELECT status FROM v_recovery_proposal_overview ORDER BY proposal_id", String.class);

        assertThat(statuses).containsExactly("ACCEPTED", "STALE", "REJECTED", "EDITED");
    }

    @Test
    @DisplayName("Demo scenarios: +199 kcal no deviation, -200 kcal deviation, REJECT keeps recoveryRequired")
    void boundaryScenarios() {
        List<Map<String, Object>> deviations = admin.queryForList("""
                SELECT deviation_type, kcal_difference, resolved_at IS NULL AS open
                FROM deviation_event d JOIN daily_plan p ON p.id = d.daily_plan_id
                WHERE p.user_id = 'a0000000-0000-4000-8000-000000000002' AND p.plan_date = DATE '2026-11-16'""");

        assertThat(deviations).hasSize(1);
        assertThat(((Number) deviations.getFirst().get("kcal_difference")).intValue()).isEqualTo(-200);
        assertThat(deviations.getFirst().get("open")).isEqualTo(true);
        assertThat(admin.queryForObject("""
                SELECT recovery_required FROM daily_plan
                WHERE user_id = 'a0000000-0000-4000-8000-000000000002' AND plan_date = DATE '2026-11-16'""", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("Demo scenario UC-01: version 2 keeps completed and skipped items, replaces the dinner")
    void acceptedRecoveryScenario() {
        List<String> items = admin.queryForList("""
                SELECT coalesce(meal_name, exercise_name) || ':' || status FROM v_active_plan_item
                WHERE user_id = 'a0000000-0000-4000-8000-000000000001' AND plan_date = DATE '2026-11-16'
                ORDER BY planned_time""", String.class);

        assertThat(items).containsExactly("Oatmeal with banana:COMPLETED", "Chicken rice bowl:COMPLETED",
                "Running, 10 km/h:SKIPPED", "Tofu vegetable stir-fry:PLANNED", "Bodyweight circuit:PLANNED");
    }
}
