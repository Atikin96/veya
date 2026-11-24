package com.veya.planning.domain;

import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.ViolationType;
import com.veya.planning.domain.MealPlanner.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.veya.planning.api.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("MealPlanner: deterministic, constraint-based menu suggestion (US-09)")
class MealPlannerTest {

    private static final List<String> THREE = List.of("BREAKFAST", "LUNCH", "DINNER");

    @Test
    @DisplayName("Chooses the distinct combination closest to the target, without excluded meals")
    void closestCombination() {
        Suggestion s = MealPlanner.suggest(THREE, MEALS, day(null, null, Set.of(), Set.of("FISH")), new BigDecimal("1621"), 20);

        assertThat(s.feasible()).isTrue();
        assertThat(s.choices()).extracting(c -> c.meal().name())
                .containsExactly("Oatmeal with banana", "Chicken rice bowl", "Tofu vegetable stir-fry");
        assertThat(s.totalKcal()).isEqualByComparingTo("1621.7");
        assertThat(s.choices()).extracting(MealPlanner.Choice::slot).containsExactlyElementsOf(THREE);
    }

    @Test
    @DisplayName("The same input always gives the same menu (determinism, KPI-1)")
    void deterministic() {
        Suggestion a = MealPlanner.suggest(THREE, MEALS, unrestricted(), new BigDecimal("1500"), 20);
        Suggestion b = MealPlanner.suggest(THREE, List.copyOf(MEALS.reversed()), unrestricted(), new BigDecimal("1500"), 20);
        assertThat(a).isEqualTo(b);
    }

    @ParameterizedTest(name = "target {0} with tolerance {1}% -> feasible {2}")
    @CsvSource({"1621.7, 0, true", "1800, 10, true", "1802, 10, false", "2500, 20, false"})
    @DisplayName("Boundary: the total must be within +/- tolerance of the target")
    void tolerance(String target, int tolerance, boolean feasible) {
        List<CatalogMeal> fixed = List.of(OATMEAL, CHICKEN_BOWL, TOFU); // only one combination: 1621.7 kcal
        Suggestion s = MealPlanner.suggest(THREE, fixed, unrestricted(), new BigDecimal(target), tolerance);
        assertThat(s.feasible()).isEqualTo(feasible);
        if (!feasible) {
            assertThat(s.blocking()).singleElement().satisfies(b -> {
                assertThat(b.type()).isEqualTo(ViolationType.NUTRITION_TARGET);
                assertThat(b.message()).contains("1621.7 kcal", "acceptable range");
            });
        }
    }

    @Test
    @DisplayName("Without a profile target no menu can be suggested")
    void noTarget() {
        Suggestion s = MealPlanner.suggest(THREE, MEALS, unrestricted(), null, 20);
        assertThat(s.feasible()).isFalse();
        assertThat(s.blocking().get(0).message()).contains("profile");
    }

    @Test
    @DisplayName("A slot without any compliant meal blocks the suggestion with the reason")
    void slotWithoutCandidates() {
        Suggestion s = MealPlanner.suggest(List.of("DINNER"), MEALS, day(10, null, Set.of(), Set.of()), new BigDecimal("500"), 20);
        assertThat(s.feasible()).isFalse();
        assertThat(s.blocking()).singleElement().extracting(b -> b.type()).isEqualTo(ViolationType.TIME);
    }

    @Test
    @DisplayName("The same meal is never suggested twice; too few distinct meals -> not feasible")
    void distinctMeals() {
        Suggestion s = MealPlanner.suggest(List.of("LUNCH", "DINNER"), List.of(LENTIL_SOUP), unrestricted(), new BigDecimal("950"), 50);
        assertThat(s.feasible()).isFalse();
        assertThat(s.blocking().get(0).message()).contains("Not enough different meals");
    }
}
