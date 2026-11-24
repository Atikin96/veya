package com.veya.planning.api;

import com.veya.planning.api.Alternatives.ExerciseOption;
import com.veya.planning.api.Alternatives.MealOption;
import com.veya.planning.api.Alternatives.Ranked;
import com.veya.planning.api.Alternatives.ReasonCategory;
import com.veya.planning.api.HardConstraints.ViolationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.veya.planning.api.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Alternatives: ranked, compliant replacement options with reasons (US-10, US-14, US-17)")
class AlternativesTest {

    @Test
    @DisplayName("Meal options exclude the current and infeasible meals, are closest-energy first and carry reasons")
    void mealOptions() {
        Ranked<MealOption> ranked = Alternatives.meals("DINNER", SALMON.id(), SALMON.kcal(), MEALS,
                day(40, Set.of(), Set.of("FISH"), Set.of()));

        List<String> names = ranked.options().stream().map(o -> o.meal().name()).toList();
        assertThat(names).containsExactly("Tofu vegetable stir-fry", "Lentil soup", "Buckwheat with chicken");
        assertThat(ranked.blocking()).isEmpty();
        MealOption best = ranked.options().get(0);
        assertThat(best.reasons()).extracting(Alternatives.Reason::category)
                .containsExactly(ReasonCategory.INGREDIENT, ReasonCategory.TIME, ReasonCategory.NUTRITION);
        assertThat(best.reasons().get(2).text()).isEqualTo("4.9 kcal heavier than the planned dinner.");
    }

    @Test
    @DisplayName("No feasible meal -> no options and a blocking explanation")
    void noMealOption() {
        Ranked<MealOption> ranked = Alternatives.meals("DINNER", SALMON.id(), SALMON.kcal(), MEALS, day(5, null, Set.of(), Set.of()));
        assertThat(ranked.options()).isEmpty();
        assertThat(ranked.blocking()).singleElement().extracting(HardConstraints.Violation::type).isEqualTo(ViolationType.TIME);
    }

    @Test
    @DisplayName("At most MAX_OPTIONS options are returned even with a large catalogue")
    void optionLimit() {
        List<HardConstraints.CatalogMeal> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(meal(100 + i, "Meal " + i, "LUNCH", 5, String.valueOf(400 + i), "RICE"));
        }
        assertThat(Alternatives.meals("LUNCH", null, new BigDecimal("410"), many, unrestricted()).options())
                .hasSize(Alternatives.MAX_OPTIONS)
                .first().extracting(o -> o.meal().name()).isEqualTo("Meal 10");
    }

    @Test
    @DisplayName("Exercise options fit the equipment, are shortened to the available time and ranked by MET x minutes")
    void exerciseOptions() {
        Ranked<ExerciseOption> ranked = Alternatives.exercises(RUN, 30, EXERCISES, day(20, Set.of(), Set.of(), Set.of()));
        assertThat(ranked.options()).extracting(o -> o.exercise().name()).containsExactly("Bodyweight circuit", "Brisk walking");
        ExerciseOption best = ranked.options().get(0);
        assertThat(best.durationMin()).isEqualTo(20);
        assertThat(best.reasons().get(0).text()).isEqualTo("Needs no equipment.");
        assertThat(best.reasons().get(1).text()).contains("Shortened to 20 minutes");

        assertThat(Alternatives.exercises(RUN, 30, EXERCISES, day(null, Set.of("DUMBBELLS"), Set.of(), Set.of())).options())
                .extracting(o -> o.durationMin()).containsOnly(30);
    }

    @Test
    @DisplayName("Regression (smoke test): with unknown equipment only equipment-free exercises are suggested")
    void unknownEquipmentIsNotAssumed() {
        var kettlebell = exercise(7, "Kettlebell swings", "9.8", "KETTLEBELL");
        Ranked<ExerciseOption> ranked = Alternatives.exercises(RUN, 30, List.of(RUN, kettlebell, CIRCUIT), unrestricted());
        assertThat(ranked.options()).extracting(o -> o.exercise().name()).containsExactly("Bodyweight circuit");
    }

    @Test
    @DisplayName("No time or no compliant exercise -> blocking with TIME / EQUIPMENT")
    void noExerciseOption() {
        assertThat(Alternatives.exercises(RUN, 30, EXERCISES, day(0, null, Set.of(), Set.of())).blocking())
                .singleElement().extracting(HardConstraints.Violation::type).isEqualTo(ViolationType.TIME);
        Ranked<ExerciseOption> equipment = Alternatives.exercises(RUN, 30, List.of(RUN, DUMBBELLS), day(60, Set.of(), Set.of(), Set.of()));
        assertThat(equipment.options()).isEmpty();
        assertThat(equipment.blocking().get(0).type()).isEqualTo(ViolationType.EQUIPMENT);
    }

    @Test
    @DisplayName("Energy texts: lighter / heavier / same")
    void energyText() {
        assertThat(Alternatives.energyText(new BigDecimal("475.9"), new BigDecimal("560.9"), "the planned dinner"))
                .isEqualTo("85 kcal lighter than the planned dinner.");
        assertThat(Alternatives.energyText(new BigDecimal("500"), new BigDecimal("500.0"), "x")).isEqualTo("Same energy as x.");
        assertThat(Alternatives.byId(EXERCISES, HardConstraints.CatalogExercise::id, 3)).contains(CIRCUIT);
        assertThat(Alternatives.byId(EXERCISES, HardConstraints.CatalogExercise::id, 42)).isEmpty();
    }
}
