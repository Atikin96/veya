package com.veya.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US-15 significant deviation rules with the 200 kcal default threshold (KPI-1 scenario data). */
@DisplayName("US-15 deviation detection")
class DeviationDetectionTests extends DatabaseTestSupport {

    @ParameterizedTest(name = "confirmed meal {0} kcal from plan -> recoveryRequired = {1}")
    @CsvSource({
            "0, false",
            "199, false",
            "-199, false",
            "200, true",
            "-200, true",
            "250, true",
            "-600, true"
    })
    @DisplayName("US-15 rule 1: |actual - planned| >= 200 kcal on a confirmed meal")
    void kcalThresholdBoundary(int delta, boolean expectedDeviation) {
        Plan plan = createPlan();
        logMeal(plan, plan.lunch(), delta);

        // Logging alone does not trigger: the meal must be confirmed (COMPLETED).
        assertThat(recoveryRequired(plan)).isFalse();

        setStatus(plan.lunch(), "COMPLETED");

        assertThat(recoveryRequired(plan)).isEqualTo(expectedDeviation);
        assertThat(openDeviations(plan)).isEqualTo(expectedDeviation ? 1 : 0);
        if (expectedDeviation) {
            assertThat(app.queryForObject("SELECT kcal_difference FROM deviation_event WHERE daily_plan_id = ?",
                    BigDecimal.class, planId(plan))).isEqualByComparingTo(BigDecimal.valueOf(delta));
        }
    }

    @Test
    @DisplayName("Several entries of one meal are summed before comparing with the plan")
    void entriesOfOneMealAreSummed() {
        Plan plan = createPlan();
        BigDecimal planned = plannedKcal(plan.lunch());
        UUID lunch = activeItem(plan.lunch());
        for (int i = 0; i < 2; i++) {
            app.update("""
                    INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, plan_item_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                    VALUES (?, ?, 'LUNCH', 'MANUAL', ?, 'Half portion', 150, ?, 10, 5, 20)""",
                    plan.userId(), plan.date(), lunch, planned.divide(BigDecimal.TWO).add(BigDecimal.valueOf(60)));
        }

        setStatus(plan.lunch(), "COMPLETED");   // total = planned + 120 -> below threshold

        assertThat(recoveryRequired(plan)).isFalse();
    }

    @Test
    @DisplayName("Correcting an entry of a completed meal re-evaluates the deviation (US-06 + US-15)")
    void correctionReevaluates() {
        Plan plan = createPlan();
        UUID entry = logMeal(plan, plan.lunch(), 150);
        setStatus(plan.lunch(), "COMPLETED");
        assertThat(recoveryRequired(plan)).isFalse();

        app.update("UPDATE food_entry SET kcal = kcal + 50 WHERE id = ?", entry);

        assertThat(recoveryRequired(plan)).isTrue();
        // Repeated detection does not create duplicate open events.
        app.update("UPDATE food_entry SET kcal = kcal + 10 WHERE id = ?", entry);
        assertThat(openDeviations(plan)).isEqualTo(1);
    }

    @Test
    @DisplayName("US-15 rule 2: a skipped workout sets recoveryRequired")
    void skippedWorkout() {
        Plan plan = createPlan();
        UUID workout = activeItem(plan.workout());

        app.update("""
                INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status)
                VALUES (?, ?, (SELECT exercise_id FROM plan_item WHERE id = ?), ?, 'SKIPPED')""", plan.userId(), plan.date(), workout, workout);

        assertThat(activeStatus(plan.workout())).isEqualTo("SKIPPED");
        assertThat(recoveryRequired(plan)).isTrue();
        assertThat(app.queryForObject("SELECT deviation_type FROM deviation_event WHERE daily_plan_id = ?", String.class, planId(plan)))
                .isEqualTo("ITEM_SKIPPED");
    }

    @Test
    @DisplayName("US-15 rule 2: a missed meal sets recoveryRequired")
    void missedMeal() {
        Plan plan = createPlan();

        setStatus(plan.breakfast(), "MISSED");

        assertThat(recoveryRequired(plan)).isTrue();
        assertThat(app.queryForObject("SELECT deviation_type FROM deviation_event WHERE daily_plan_id = ?", String.class, planId(plan)))
                .isEqualTo("ITEM_MISSED");
    }

    @Test
    @DisplayName("US-15 rule 3: the application records an infeasible future item; the flag follows")
    void constraintInfeasibility() {
        Plan plan = createPlan();

        app.update("""
                INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type, details)
                VALUES (?, ?, 'CONSTRAINT_INFEASIBLE', 'Fish is excluded today')""", planId(plan), activeItem(plan.dinner()));

        assertThat(recoveryRequired(plan)).isTrue();
    }

    @Test
    @DisplayName("A deviation event must reference an item of its own plan")
    void deviationItemMustBelongToPlan() {
        Plan plan = createPlan();
        Plan other = createPlan();

        assertSqlState("VY400", () -> app.update("""
                INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type, details)
                VALUES (?, ?, 'CONSTRAINT_INFEASIBLE', 'x')""", planId(plan), activeItem(other.dinner())));
    }

    @ParameterizedTest(name = "planned {0}, actual {1} -> {2}")
    @CsvSource({"600, 799, false", "600, 800, true", "600, 400, true", "600, 401, false", "0, 200, true"})
    @DisplayName("is_significant_kcal_deviation() boundary values")
    void thresholdFunction(int planned, int actual, boolean expected) {
        assertThat(app.queryForObject("SELECT is_significant_kcal_deviation(?, ?)", Boolean.class, planned, actual))
                .isEqualTo(expected);
    }
}
