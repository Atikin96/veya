package com.veya.recovery.application;

import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.recovery.api.ConstraintFeasibility.InfeasibleItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.veya.planning.api.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("ConstraintFeasibilityChecker: US-15 rule 3 on remaining items (AQ-13)")
class ConstraintFeasibilityCheckerTest {

    private static final Map<Long, CatalogMeal> MEAL_MAP = MEALS.stream().collect(Collectors.toMap(CatalogMeal::id, Function.identity()));
    private static final Map<Long, CatalogExercise> EXERCISE_MAP = EXERCISES.stream()
            .collect(Collectors.toMap(CatalogExercise::id, Function.identity()));

    @Test
    @DisplayName("Only PLANNED items are checked; each infeasible item gets a reason naming the constraint")
    void infeasibleItems() {
        PlanItemView eatenSalmon = mealItem(SALMON, "LUNCH", "13:00", "COMPLETED");
        PlanItemView salmonDinner = mealItem(SALMON, "DINNER", "19:30", "PLANNED");
        PlanItemView tofu = mealItem(TOFU, "DINNER", "20:00", "PLANNED");
        PlanItemView lifting = workoutItem(DUMBBELLS, "17:00", 45, "PLANNED");
        PlanItemView walk = workoutItem(WALK, "18:00", 20, "PLANNED");

        List<InfeasibleItem> result = ConstraintFeasibilityChecker.infeasibleItems(
                plan(false, eatenSalmon, salmonDinner, tofu, lifting, walk), day(40, Set.of(), Set.of("FISH"), Set.of()),
                MEAL_MAP, EXERCISE_MAP);

        assertThat(result).extracting(InfeasibleItem::itemKey).containsExactly(salmonDinner.itemKey(), lifting.itemKey());
        assertThat(result.get(0).reason()).startsWith("Planned dinner:").contains("FISH", "excluded today", "2026-11-16");
        assertThat(result.get(1).reason()).startsWith("Planned workout:").contains("DUMBBELLS");
    }

    @Test
    @DisplayName("Unrestricted day (no saved constraints) -> nothing is infeasible")
    void nothingInfeasible() {
        assertThat(ConstraintFeasibilityChecker.infeasibleItems(plan(false, mealItem(SALMON, "DINNER", "19:30", "PLANNED"),
                workoutItem(DUMBBELLS, "17:00", 45, "PLANNED")), unrestricted(), MEAL_MAP, EXERCISE_MAP)).isEmpty();
    }
}
