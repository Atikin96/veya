package com.veya.recovery.domain;

import com.veya.planning.api.Alternatives.ReasonCategory;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.ViolationType;
import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.planning.api.PlanQueries.PlanView;
import com.veya.recovery.domain.RecoveryEngine.Change;
import com.veya.recovery.domain.RecoveryEngine.ChangeType;
import com.veya.recovery.domain.RecoveryEngine.Input;
import com.veya.recovery.domain.RecoveryEngine.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.veya.planning.api.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario tests of the recovery engine with fixed data (KPI-1, KPI-3): every expected value was computed by hand
 * from the fixture catalogue. Scenario A reproduces the UC-01 example of the API contract (forecast 1871.7 kcal).
 */
@Tag("unit")
@DisplayName("RecoveryEngine: deterministic constraint-based recovery (US-16, US-17, US-19)")
class RecoveryEngineTest {

    private static final BigDecimal TARGET = new BigDecimal("1900");

    private final PlanItemView breakfast = mealItem(OATMEAL, "BREAKFAST", "08:00", "COMPLETED");
    private final PlanItemView lunch = mealItem(CHICKEN_BOWL, "LUNCH", "13:00", "COMPLETED");
    private final PlanItemView run = workoutItem(RUN, "18:00", 30, "SKIPPED");
    private final PlanItemView salmonDinner = mealItem(SALMON, "DINNER", "19:30", "PLANNED");

    @Test
    @DisplayName("A (UC-01): lunch +250 kcal, run skipped, fish unavailable -> dinner replaced (INGREDIENT), workout added")
    void ucOneScenario() {
        PlanView plan = plan(true, breakfast, lunch, run, salmonDinner);
        Result r = RecoveryEngine.propose(new Input(plan, day(40, Set.of(), Set.of("FISH"), Set.of()), MEALS, EXERCISES, TARGET,
                new BigDecimal("1306.3")));

        assertThat(r.feasible()).isTrue();
        assertThat(r.changes()).hasSize(2);
        Change dinner = r.changes().get(0);
        assertThat(dinner.type()).isEqualTo(ChangeType.REPLACE);
        assertThat(dinner.target()).isSameAs(salmonDinner);
        assertThat(dinner.newMeal()).isEqualTo(TOFU);
        assertThat(dinner.reason().category()).isEqualTo(ReasonCategory.INGREDIENT);
        assertThat(dinner.reason().text()).contains("FISH", "Tofu vegetable stir-fry");
        Change added = r.changes().get(1);
        assertThat(added.type()).isEqualTo(ChangeType.ADD);
        assertThat(added.target()).isNull();
        assertThat(added.newExercise()).isEqualTo(CIRCUIT);
        assertThat(added.durationMin()).isEqualTo(30);
        assertThat(added.reason().text()).contains("skipped");

        assertThat(r.totals().remainingPlannedKcalBefore()).isEqualByComparingTo("560.5");
        assertThat(r.totals().remainingPlannedKcalAfter()).isEqualByComparingTo("565.4");
        assertThat(r.totals().forecastKcal()).isEqualByComparingTo("1871.7");
    }

    @Test
    @DisplayName("Frozen items are never targeted, even if they now break a constraint (US-16)")
    void frozenItemsUntouched() {
        PlanItemView eatenSalmon = mealItem(SALMON, "LUNCH", "13:00", "COMPLETED");
        PlanItemView tofu = mealItem(TOFU, "DINNER", "19:30", "PLANNED");
        Result r = RecoveryEngine.propose(new Input(plan(true, breakfast, eatenSalmon, run, tofu),
                day(60, Set.of(), Set.of("FISH"), Set.of()), MEALS, EXERCISES, TARGET, new BigDecimal("1300")));
        assertThat(r.changes()).noneMatch(c -> c.target() != null && !c.target().isPlanned());
    }

    @Test
    @DisplayName("C: over-eating -> the dinner is replaced by a lighter one only if it beats the change penalty (NUTRITION)")
    void overEating() {
        PlanItemView tofu = mealItem(TOFU, "DINNER", "19:30", "PLANNED");
        Result r = RecoveryEngine.propose(new Input(plan(true, breakfast, lunch, tofu), unrestricted(), MEALS, EXERCISES, TARGET,
                new BigDecimal("1700")));
        assertThat(r.changes()).singleElement().satisfies(c -> {
            assertThat(c.newMeal()).isEqualTo(BUCKWHEAT);
            assertThat(c.reason().category()).isEqualTo(ReasonCategory.NUTRITION);
            assertThat(c.reason().text()).contains("from 2265.4 to 2147.0 kcal", "target 1900");
        });
    }

    @Test
    @DisplayName("B: if the current remaining plan is already the best feasible option -> no-solution (no empty proposal)")
    void nothingToImprove() {
        PlanItemView tofu = mealItem(TOFU, "DINNER", "19:30", "PLANNED");
        Result r = RecoveryEngine.propose(new Input(plan(true, breakfast, lunch, tofu), unrestricted(), MEALS, EXERCISES, TARGET,
                new BigDecimal("1300")));
        assertThat(r.feasible()).isFalse();
        assertThat(r.blocking()).singleElement().satisfies(b -> {
            assertThat(b.type()).isEqualTo(ViolationType.NUTRITION_TARGET);
            assertThat(b.itemKey()).isNull();
        });
    }

    @Test
    @DisplayName("D (US-19): a remaining meal without any compliant candidate -> blocking constraint for that item")
    void noCandidate() {
        Result r = RecoveryEngine.propose(new Input(plan(true, breakfast, lunch, salmonDinner), day(10, null, Set.of(), Set.of()),
                MEALS, EXERCISES, TARGET, new BigDecimal("1300")));
        assertThat(r.feasible()).isFalse();
        assertThat(r.changes()).isEmpty();
        assertThat(r.blocking()).singleElement().satisfies(b -> {
            assertThat(b.type()).isEqualTo(ViolationType.TIME);
            assertThat(b.itemKey()).isEqualTo(salmonDinner.itemKey());
        });
        assertThat(r.totals().remainingPlannedKcalAfter()).isEqualByComparingTo(r.totals().remainingPlannedKcalBefore());
    }

    @Test
    @DisplayName("E: an infeasible remaining workout is replaced (EQUIPMENT) or removed when no alternative exists")
    void infeasibleWorkout() {
        PlanItemView lifting = workoutItem(DUMBBELLS, "17:00", 45, "PLANNED");
        Result replaced = RecoveryEngine.propose(new Input(plan(true, breakfast, lifting), day(60, Set.of(), Set.of(), Set.of()),
                MEALS, EXERCISES, TARGET, new BigDecimal("426.4")));
        assertThat(replaced.changes()).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(ChangeType.REPLACE);
            assertThat(c.newExercise()).isEqualTo(WALK); // 4.3 x 45 = 193.5 is closest to 5.0 x 45 = 225
            assertThat(c.reason().category()).isEqualTo(ReasonCategory.EQUIPMENT);
        });

        Result removed = RecoveryEngine.propose(new Input(plan(true, breakfast, lifting), day(60, Set.of(), Set.of(), Set.of()),
                MEALS, List.of(DUMBBELLS), TARGET, new BigDecimal("426.4")));
        assertThat(removed.changes()).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(ChangeType.REMOVE);
            assertThat(c.newExercise()).isNull();
            assertThat(c.reason().text()).contains("removed");
        });
    }

    @Test
    @DisplayName("Without a profile target the remaining target is the remaining planned energy")
    void noTarget() {
        Result r = RecoveryEngine.propose(new Input(plan(true, breakfast, lunch, run, salmonDinner),
                day(40, Set.of(), Set.of("FISH"), Set.of()), MEALS, EXERCISES, null, new BigDecimal("1306.3")));
        assertThat(r.feasible()).isTrue();
        assertThat(r.changes().get(0).newMeal()).isEqualTo(TOFU); // 565.4 is closest to the planned 560.5
        assertThat(r.totals().targetKcal()).isNull();
    }

    @Test
    @DisplayName("Search bound: beyond MAX_COMBINATIONS the engine falls back to the best candidate per meal")
    void boundedSearch() {
        List<CatalogMeal> lunches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lunches.add(meal(200 + i, "Lunch " + i, "LUNCH", 5, String.valueOf(300 + 10 * i), "RICE"));
        }
        List<PlanItemView> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) { // 8^6 = 262144 combinations > 50000
            items.add(mealItem(lunches.get(9), "LUNCH", String.format("1%d:00", i), "PLANNED"));
        }
        List<List<CatalogMeal>> candidates = new ArrayList<>();
        items.forEach(i -> candidates.add(lunches.subList(0, 8)));
        assertThat(RecoveryEngine.bestCombination(items, candidates, new BigDecimal("1800")))
                .hasSize(6).allMatch(m -> m.equals(lunches.get(0)));
        assertThat(RecoveryEngine.bestCombination(List.of(), List.of(), BigDecimal.ONE)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ViolationType.class)
    @DisplayName("Every violation type maps to a US-17 reason category")
    void categories(ViolationType type) {
        assertThat(RecoveryEngine.category(type)).isNotNull();
    }

    @Test
    @DisplayName("Same input, same proposal (determinism)")
    void deterministic() {
        PlanView plan = plan(true, breakfast, lunch, run, salmonDinner);
        Input in = new Input(plan, day(40, Set.of(), Set.of("FISH"), Set.of()), MEALS, EXERCISES, TARGET, new BigDecimal("1306.3"));
        assertThat(RecoveryEngine.propose(in)).isEqualTo(RecoveryEngine.propose(in));
        assertThat(RecoveryEngine.targetKey(RecoveryEngine.propose(in).changes().get(0))).isEqualTo(salmonDinner.itemKey());
    }
}
