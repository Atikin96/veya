package com.veya.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Q2 immutable plan versions, US-10/US-14 replacement, US-16 frozen items, NFR-10 expected version. */
@DisplayName("Plan versioning (create_plan_version and immutability triggers)")
class PlanVersioningTests extends DatabaseTestSupport {

    private static final String REPLACE_DINNER = """
            SELECT create_plan_version(?, ?, ?, 'MEAL_REPLACEMENT', (
                SELECT jsonb_agg(item) FROM (
                    SELECT jsonb_build_object('itemKey', a.item_key, 'itemType', a.item_type, 'mealSlot', a.meal_slot_code,
                                              'plannedTime', a.planned_time, 'mealId', a.meal_id, 'exerciseId', a.exercise_id,
                                              'kcal', a.planned_kcal, 'proteinG', a.planned_protein_g, 'fatG', a.planned_fat_g,
                                              'carbsG', a.planned_carbs_g, 'durationMin', a.duration_min) AS item
                    FROM v_active_plan_item a
                    WHERE a.user_id = ? AND a.plan_date = ? AND a.status = 'PLANNED' AND a.item_key <> ?
                    UNION ALL
                    SELECT jsonb_build_object('itemKey', ?::uuid, 'itemType', 'MEAL', 'mealSlot', 'DINNER', 'plannedTime', '19:30',
                                              'mealId', n.meal_id, 'kcal', n.kcal, 'proteinG', n.protein_g, 'fatG', n.fat_g, 'carbsG', n.carbs_g)
                    FROM v_meal_nutrition n WHERE n.meal_name = 'Lentil soup'
                ) s))""";

    private Integer replaceDinner(Plan plan, int expectedVersion) {
        return app.queryForObject(REPLACE_DINNER, Integer.class, plan.userId(), plan.date(), expectedVersion,
                plan.userId(), plan.date(), plan.dinner(), plan.dinner());
    }

    @Test
    @DisplayName("Initial plan: version 1 with four PLANNED items and the active-version pointer set")
    void initialPlan() {
        Plan plan = createPlan();

        assertThat(currentVersion(plan)).isEqualTo(1);
        assertThat(app.queryForList("SELECT status FROM v_active_plan_item WHERE daily_plan_id = ?", String.class, planId(plan)))
                .hasSize(4).containsOnly("PLANNED");
        assertThat(recoveryRequired(plan)).isFalse();
    }

    @Test
    @DisplayName("US-10: replacing a meal creates version 2; recorded items are frozen and carried over unchanged")
    void replacementCreatesNextVersionWithFrozenItems() {
        Plan plan = createPlan();
        logMeal(plan, plan.breakfast(), 0);
        setStatus(plan.breakfast(), "COMPLETED");
        UUID breakfastV1 = activeItem(plan.breakfast());

        Integer version = replaceDinner(plan, 1);

        assertThat(version).isEqualTo(2);
        assertThat(currentVersion(plan)).isEqualTo(2);
        List<Map<String, Object>> active = app.queryForList(
                "SELECT item_key, status, meal_name FROM v_active_plan_item WHERE daily_plan_id = ? ORDER BY planned_time", planId(plan));
        assertThat(active).hasSize(4);
        assertThat(activeStatus(plan.breakfast())).isEqualTo("COMPLETED");
        assertThat(activeItem(plan.breakfast())).isNotEqualTo(breakfastV1);   // a copy inside version 2
        assertThat(app.queryForObject("SELECT meal_name FROM v_active_plan_item WHERE item_key = ?", String.class, plan.dinner()))
                .isEqualTo("Lentil soup");
        // Version 1 still contains the original dinner: history is not rewritten.
        assertThat(app.queryForObject("""
                SELECT m.name FROM plan_item pi JOIN plan_version pv ON pv.id = pi.plan_version_id JOIN meal m ON m.id = pi.meal_id
                WHERE pv.daily_plan_id = ? AND pv.version_no = 1 AND pi.item_key = ?""", String.class, planId(plan), plan.dinner()))
                .isEqualTo("Salmon with potatoes");
    }

    @Test
    @DisplayName("NFR-10: a stale expected version is rejected with VY409 and nothing changes")
    void staleExpectedVersion() {
        Plan plan = createPlan();
        replaceDinner(plan, 1);

        assertSqlState("VY409", () -> replaceDinner(plan, 1));
        assertThat(currentVersion(plan)).isEqualTo(2);
        assertThat(versionCount(plan)).isEqualTo(2);
    }

    @Test
    @DisplayName("INITIAL_PLAN only for version 1; a second initial plan is rejected")
    void initialPlanOnlyOnce() {
        Plan plan = createPlan();

        assertSqlState("VY409", () -> createPlan(plan.userId(), plan.date()));
        assertSqlState("VY400", () -> app.queryForObject(
                "SELECT create_plan_version(?, ?, 1, 'INITIAL_PLAN', '[]')", Integer.class, plan.userId(), plan.date()));
        assertThat(versionCount(plan)).isEqualTo(1);
    }

    @Test
    @DisplayName("US-16: an item that is already recorded cannot be replaced (frozen key)")
    void frozenItemCannotBeReplaced() {
        Plan plan = createPlan();
        logMeal(plan, plan.lunch(), 0);
        setStatus(plan.lunch(), "COMPLETED");

        assertSqlState("VY400", () -> app.queryForObject("""
                SELECT create_plan_version(?, ?, 1, 'MEAL_REPLACEMENT', jsonb_build_array(jsonb_build_object(
                    'itemKey', ?::uuid, 'itemType', 'MEAL', 'mealSlot', 'LUNCH', 'mealId', (SELECT id FROM meal WHERE name = 'Lentil soup'),
                    'kcal', 500, 'proteinG', 20, 'fatG', 10, 'carbsG', 60)))""", Integer.class, plan.userId(), plan.date(), plan.lunch()));
        assertThat(versionCount(plan)).isEqualTo(1);
    }

    @Test
    @DisplayName("Invalid item data is rejected by CHECK constraints and the whole version is rolled back")
    void invalidItemRollsBackVersion() {
        Plan plan = createPlan();

        assertSqlState("23514", () -> app.queryForObject("""
                SELECT create_plan_version(?, ?, 1, 'EXERCISE_REPLACEMENT', jsonb_build_array(jsonb_build_object(
                    'itemType', 'WORKOUT', 'exerciseId', (SELECT min(id) FROM exercise), 'durationMin', 0)))""",
                Integer.class, plan.userId(), plan.date()));
        assertThat(currentVersion(plan)).isEqualTo(1);
        assertThat(versionCount(plan)).isEqualTo(1);
    }

    @Test
    @DisplayName("Superseded versions are frozen: no status recording, no new links from diary entries")
    void supersededVersionIsFrozen() {
        Plan plan = createPlan();
        UUID lunchV1 = activeItem(plan.lunch());
        replaceDinner(plan, 1);

        assertSqlState("VY001", () -> app.update("UPDATE plan_item SET status = 'SKIPPED' WHERE id = ?", lunchV1));
        assertSqlState("VY001", () -> app.update("""
                INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                VALUES (?, ?, 'LUNCH', 'MANUAL', ?, 'Soup', 300, 300, 10, 10, 30)""", plan.userId(), plan.date(), lunchV1));
    }

    @Test
    @DisplayName("US-13: status is recorded once (PLANNED -> other) and never reset")
    void statusRecordedOnce() {
        Plan plan = createPlan();
        setStatus(plan.breakfast(), "COMPLETED");

        assertSqlState("VY400", () -> setStatus(plan.breakfast(), "SKIPPED"));
        assertSqlState("VY400", () -> setStatus(plan.breakfast(), "PLANNED"));
        assertSqlState("23514", () -> setStatus(plan.lunch(), "CHANGED"));   // CHANGED is for workouts only
    }

    @Test
    @DisplayName("Ownership: entries cannot be linked to another user's plan items (VY404)")
    void crossUserLinkRejected() {
        Plan plan = createPlan();
        UUID otherUser = createUser();

        assertSqlState("VY404", () -> app.update("""
                INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                VALUES (?, ?, 'LUNCH', 'MANUAL', ?, 'Soup', 300, 300, 10, 10, 30)""", otherUser, plan.date(), activeItem(plan.lunch())));
        assertSqlState("VY404", () -> app.update("""
                INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status, duration_min)
                VALUES (?, ?, (SELECT min(id) FROM exercise), ?, 'COMPLETED', 30)""", otherUser, plan.date(), activeItem(plan.workout())));
    }

    @Test
    @DisplayName("Entry/plan consistency: meal entries need a MEAL on the same date; workouts need a WORKOUT")
    void entryMustMatchItemTypeAndDate() {
        Plan plan = createPlan();

        assertSqlState("VY400", () -> app.update("""
                INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                VALUES (?, ?, 'LUNCH', 'MANUAL', ?, 'Soup', 300, 300, 10, 10, 30)""", plan.userId(), plan.date(), activeItem(plan.workout())));
        assertSqlState("VY400", () -> app.update("""
                INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                VALUES (?, ?, 'LUNCH', 'MANUAL', ?, 'Soup', 300, 300, 10, 10, 30)""", plan.userId(), plan.date().plusDays(1), activeItem(plan.lunch())));
        assertSqlState("VY400", () -> app.update("""
                INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status, duration_min)
                VALUES (?, ?, (SELECT min(id) FROM exercise), ?, 'COMPLETED', 30)""", plan.userId(), plan.date(), activeItem(plan.lunch())));
    }

    @Test
    @DisplayName("A workout event sets the planned item's status; a second event for it is rejected")
    void activityEntryDrivesStatus() {
        Plan plan = createPlan();
        UUID workout = activeItem(plan.workout());

        app.update("""
                INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status, duration_min)
                VALUES (?, ?, (SELECT exercise_id FROM plan_item WHERE id = ?), ?, 'CHANGED', 20)""", plan.userId(), plan.date(), workout, workout);

        assertThat(activeStatus(plan.workout())).isEqualTo("CHANGED");
        assertThat(recoveryRequired(plan)).isFalse();   // CHANGED is not a significant deviation
        assertSqlState("VY400", () -> app.update("""
                INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status)
                VALUES (?, ?, (SELECT exercise_id FROM plan_item WHERE id = ?), ?, 'SKIPPED')""", plan.userId(), plan.date(), workout, workout));
    }
}
