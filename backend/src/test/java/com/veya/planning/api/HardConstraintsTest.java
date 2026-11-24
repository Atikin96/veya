package com.veya.planning.api;

import com.veya.planning.api.HardConstraints.Violation;
import com.veya.planning.api.HardConstraints.ViolationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static com.veya.planning.api.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("HardConstraints: exclusions, time and equipment rules (US-03, US-10, US-14, US-15 rule 3)")
class HardConstraintsTest {

    @Test
    @DisplayName("A meal with a permanently excluded ingredient is a FOOD_EXCLUSION, a day exclusion an INGREDIENT violation")
    void exclusions() {
        assertThat(types(HardConstraints.check(SALMON, day(null, null, Set.of(), Set.of("FISH")))))
                .containsExactly(ViolationType.FOOD_EXCLUSION);
        assertThat(types(HardConstraints.check(SALMON, day(null, null, Set.of("FISH"), Set.of()))))
                .containsExactly(ViolationType.INGREDIENT);
        // excluded both permanently and today: reported once, as the permanent exclusion
        assertThat(types(HardConstraints.check(SALMON, day(null, null, Set.of("FISH"), Set.of("FISH")))))
                .containsExactly(ViolationType.FOOD_EXCLUSION);
        assertThat(HardConstraints.check(TOFU, day(null, null, Set.of("FISH"), Set.of("PEANUT")))).isEmpty();
    }

    @ParameterizedTest(name = "prep {0} min, available {1} -> violation {2}")
    @CsvSource({"30, 30, false", "30, 29, true", "30, 0, true", "30, , false"})
    @DisplayName("Boundary: a meal fits when its preparation time is <= the available minutes; null = unrestricted")
    void mealTime(int prep, Integer available, boolean violated) {
        var meal = meal(99, "Test", "DINNER", prep, "500.0", "RICE");
        List<Violation> v = HardConstraints.check(meal, day(available, null, Set.of(), Set.of()));
        assertThat(v.isEmpty()).isNotEqualTo(violated);
        if (violated) {
            assertThat(v.get(0).type()).isEqualTo(ViolationType.TIME);
            assertThat(v.get(0).message()).contains(prep + " minutes");
        }
    }

    @Test
    @DisplayName("Equipment: unknown (no saved constraints) is unrestricted; an empty set means no equipment")
    void equipment() {
        assertThat(HardConstraints.check(DUMBBELLS, 45, day(null, null, Set.of(), Set.of()))).isEmpty();
        assertThat(types(HardConstraints.check(DUMBBELLS, 45, day(null, Set.of(), Set.of(), Set.of()))))
                .containsExactly(ViolationType.EQUIPMENT);
        assertThat(HardConstraints.check(DUMBBELLS, 45, day(null, Set.of("DUMBBELLS"), Set.of(), Set.of()))).isEmpty();
        assertThat(types(HardConstraints.check(DUMBBELLS, 45, day(30, Set.of(), Set.of(), Set.of()))))
                .containsExactly(ViolationType.EQUIPMENT, ViolationType.TIME);
        assertThat(HardConstraints.check(RUN, 30, day(30, Set.of(), Set.of(), Set.of()))).isEmpty();
    }

    @Test
    @DisplayName("summarize explains why no meal fits a slot, by the most frequent violation type")
    void summarize() {
        Violation none = HardConstraints.summarize("DINNER", List.of(), unrestricted());
        assertThat(none.type()).isEqualTo(ViolationType.NUTRITION_TARGET);
        assertThat(none.message()).contains("no meal for dinner");

        Violation time = HardConstraints.summarize("DINNER", List.of(SALMON, TOFU, BUCKWHEAT), day(10, null, Set.of("FISH"), Set.of()));
        assertThat(time.type()).isEqualTo(ViolationType.TIME);
        assertThat(time.message()).contains("All 3 dinner candidates", "need more time than available",
                "contain ingredients excluded today");
    }

    @Test
    @DisplayName("Slot names are human-readable; meals without a preferred slot fit every slot")
    void slots() {
        assertThat(HardConstraints.slotName("DINNER")).isEqualTo("dinner");
        assertThat(HardConstraints.slotName(null)).isEqualTo("meal");
        assertThat(LENTIL_SOUP.fitsSlot("BREAKFAST")).isTrue();
        assertThat(SALMON.fitsSlot("LUNCH")).isFalse();
    }

    private static List<ViolationType> types(List<Violation> violations) {
        return violations.stream().map(Violation::type).toList();
    }
}
