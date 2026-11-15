package com.veya.db;

import com.veya.TestcontainersConfiguration;
import com.veya.VeyaTestDatabase;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base class for database integration tests. The Spring context migrates the shared PostgreSQL 17 container
 * with Flyway (as veya_owner); {@link #app} is the application's own datasource (veya_app_user).
 * Every test creates its own users with random ids, so tests do not depend on each other.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class DatabaseTestSupport {

    protected static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    /** Real bcrypt hashes of random, discarded secrets: valid format, no known password. */
    protected static final String DUMMY_HASH = "$2a$10$pL9XePYcs/Q.XU6lli4r5./ytd69..f4EML0PicWkAc8GONRasGVS";
    protected static final String OTHER_HASH = "$2a$10$UpgecL5vsIFUfSNZZIBdZO6AqF5jQfw34dMFxdLo1e2HIYgAsNuOm";

    /** Application role (veya_app_user) - the Spring datasource. */
    @Autowired
    protected JdbcTemplate app;

    protected final JdbcTemplate owner = template(VeyaTestDatabase.OWNER_USER, VeyaTestDatabase.OWNER_PASSWORD);
    protected final JdbcTemplate admin = template(VeyaTestDatabase.ADMIN_USER, VeyaTestDatabase.ADMIN_PASSWORD);
    protected final JdbcTemplate readonly = template(VeyaTestDatabase.READONLY_USER, VeyaTestDatabase.READONLY_PASSWORD);

    private static JdbcTemplate template(String user, String password) {
        return new JdbcTemplate(VeyaTestDatabase.dataSource(VeyaTestDatabase.shared(), user, password));
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures (created through the application role and the real database functions)
    // ------------------------------------------------------------------------------------------

    /** A plan with breakfast, lunch, dinner (salmon) and a 30-minute run; keys identify items across versions. */
    protected record Plan(UUID userId, LocalDate date, UUID breakfast, UUID lunch, UUID dinner, UUID workout) {
    }

    protected UUID createUser() {
        UUID id = UUID.randomUUID();
        app.update("INSERT INTO app_user (id, email, password_hash, adult_confirmed_at) VALUES (?, ?, ?, now())",
                id, "user-" + id + "@example.org", DUMMY_HASH);
        app.update("""
                INSERT INTO user_profile (user_id, goal_code, energy_target_kcal, protein_target_g, fat_target_g, carbs_target_g)
                VALUES (?, 'MAINTAIN_WEIGHT', 2000, 100, 70, 250)""", id);
        return id;
    }

    protected Plan createPlan() {
        return createPlan(createUser(), DAY);
    }

    protected Plan createPlan(UUID userId, LocalDate date) {
        Plan plan = new Plan(userId, date, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Integer version = app.queryForObject("""
                SELECT create_plan_version(?, ?, 0, 'INITIAL_PLAN', (
                    SELECT jsonb_agg(item) FROM (
                        SELECT jsonb_build_object('itemKey', k.item_key, 'itemType', 'MEAL', 'mealSlot', k.slot,
                                                  'plannedTime', k.t, 'mealId', n.meal_id, 'kcal', n.kcal,
                                                  'proteinG', n.protein_g, 'fatG', n.fat_g, 'carbsG', n.carbs_g) AS item
                        FROM (VALUES (?::uuid, 'BREAKFAST', '08:00', 'Oatmeal with banana'),
                                     (?::uuid, 'LUNCH',     '13:00', 'Chicken rice bowl'),
                                     (?::uuid, 'DINNER',    '19:30', 'Salmon with potatoes')) AS k (item_key, slot, t, meal_name)
                        JOIN v_meal_nutrition n ON n.meal_name = k.meal_name
                        UNION ALL
                        SELECT jsonb_build_object('itemKey', ?::uuid, 'itemType', 'WORKOUT', 'plannedTime', '18:00',
                                                  'exerciseId', (SELECT id FROM exercise WHERE name = 'Running, 10 km/h'),
                                                  'durationMin', 30)
                    ) s))""", Integer.class,
                userId, date, plan.breakfast(), plan.lunch(), plan.dinner(), plan.workout());
        assertThat(version).isEqualTo(1);
        return plan;
    }

    protected UUID planId(Plan plan) {
        return app.queryForObject("SELECT id FROM daily_plan WHERE user_id = ? AND plan_date = ?",
                UUID.class, plan.userId(), plan.date());
    }

    protected int currentVersion(Plan plan) {
        return app.queryForObject("SELECT current_version_no FROM daily_plan WHERE user_id = ? AND plan_date = ?",
                Integer.class, plan.userId(), plan.date());
    }

    protected int versionCount(Plan plan) {
        return app.queryForObject("SELECT count(*) FROM plan_version WHERE daily_plan_id = ?", Integer.class, planId(plan));
    }

    protected boolean recoveryRequired(Plan plan) {
        return app.queryForObject("SELECT recovery_required FROM daily_plan WHERE user_id = ? AND plan_date = ?",
                Boolean.class, plan.userId(), plan.date());
    }

    protected int openDeviations(Plan plan) {
        return app.queryForObject("SELECT count(*) FROM deviation_event WHERE daily_plan_id = ? AND resolved_at IS NULL",
                Integer.class, planId(plan));
    }

    /** Id of the item with this key in the active version. */
    protected UUID activeItem(UUID itemKey) {
        return app.queryForObject("SELECT plan_item_id FROM v_active_plan_item WHERE item_key = ?", UUID.class, itemKey);
    }

    protected BigDecimal plannedKcal(UUID itemKey) {
        return app.queryForObject("SELECT planned_kcal FROM v_active_plan_item WHERE item_key = ?", BigDecimal.class, itemKey);
    }

    protected String activeStatus(UUID itemKey) {
        return app.queryForObject("SELECT status FROM v_active_plan_item WHERE item_key = ?", String.class, itemKey);
    }

    /** Logs one manual food entry for a planned meal with planned kcal + delta. */
    protected UUID logMeal(Plan plan, UUID itemKey, int kcalDelta) {
        UUID entryId = UUID.randomUUID();
        app.update("""
                INSERT INTO food_entry (id, user_id, entry_date, meal_slot_code, input_source, plan_item_id,
                                        food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                SELECT ?, ?, ?, a.meal_slot_code, 'MANUAL', a.plan_item_id, 'Test meal', 300, a.planned_kcal + ?, 20, 10, 40
                FROM v_active_plan_item a WHERE a.item_key = ?""",
                entryId, plan.userId(), plan.date(), kcalDelta, itemKey);
        return entryId;
    }

    protected void setStatus(UUID itemKey, String status) {
        app.update("UPDATE plan_item SET status = ? WHERE id = ?", status, activeItem(itemKey));
    }

    /** Lunch confirmed with +250 kcal: a significant deviation that sets recoveryRequired. */
    protected void causeDeviation(Plan plan) {
        logMeal(plan, plan.breakfast(), 0);
        setStatus(plan.breakfast(), "COMPLETED");
        logMeal(plan, plan.lunch(), 250);
        setStatus(plan.lunch(), "COMPLETED");
    }

    protected UUID createProposal(Plan plan, int baseVersion) {
        UUID id = UUID.randomUUID();
        app.update("INSERT INTO recovery_proposal (id, daily_plan_id, base_version_no) VALUES (?, ?, ?)",
                id, planId(plan), baseVersion);
        return id;
    }

    protected UUID addReplaceMeal(UUID proposalId, int changeNo, UUID targetKey, String mealName) {
        UUID id = UUID.randomUUID();
        app.update("""
                INSERT INTO recovery_change (id, proposal_id, change_no, change_type, target_item_id, item_type, meal_slot_code,
                                             planned_time, meal_id, planned_kcal, planned_protein_g, planned_fat_g, planned_carbs_g,
                                             reason_category, reason_text)
                SELECT ?, ?, ?, 'REPLACE', a.plan_item_id, 'MEAL', a.meal_slot_code, a.planned_time,
                       n.meal_id, n.kcal, n.protein_g, n.fat_g, n.carbs_g, 'INGREDIENT', 'Test replacement reason'
                FROM v_active_plan_item a, v_meal_nutrition n
                WHERE a.item_key = ? AND n.meal_name = ?""",
                id, proposalId, changeNo, targetKey, mealName);
        return id;
    }

    protected UUID addWorkout(UUID proposalId, int changeNo, String exerciseName, int minutes) {
        UUID id = UUID.randomUUID();
        app.update("""
                INSERT INTO recovery_change (id, proposal_id, change_no, change_type, item_type, planned_time, exercise_id,
                                             duration_min, reason_category, reason_text)
                SELECT ?, ?, ?, 'ADD', 'WORKOUT', '20:30', e.id, ?, 'TIME', 'Fits the remaining available time'
                FROM exercise e WHERE e.name = ?""",
                id, proposalId, changeNo, minutes, exerciseName);
        return id;
    }

    /** Calls the decision procedure as the application; returns the new version number or null. */
    protected Integer decide(UUID userId, UUID proposalId, String decision, int baseVersion, String selectedChangeIds) {
        return decide(app, userId, proposalId, decision, baseVersion, selectedChangeIds);
    }

    protected static Integer decide(JdbcTemplate jdbc, UUID userId, UUID proposalId, String decision, int baseVersion,
                                    String selectedChangeIds) {
        return jdbc.queryForObject("CALL apply_recovery_decision(?, ?, ?, ?, ?::uuid[], NULL)", Integer.class,
                userId, proposalId, decision, baseVersion, new SqlParameterValue(Types.VARCHAR, selectedChangeIds));
    }

    // ------------------------------------------------------------------------------------------
    // Error assertions
    // ------------------------------------------------------------------------------------------

    protected static String sqlState(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    protected static void assertSqlState(String expected, ThrowingCallable call) {
        assertThatThrownBy(call).satisfies(e -> assertThat(sqlState(e)).as("SQLSTATE of %s", e.getMessage()).isEqualTo(expected));
    }
}
