package com.veya.planning.api;

import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.planning.api.PlanQueries.PlanView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Fixed, in-memory test data for the pure planning/recovery logic (no Spring, no database). */
public final class Fixtures {

    public static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    public static final CatalogMeal OATMEAL = meal(1, "Oatmeal with banana", "BREAKFAST", 10, "426.4", "OAT", "MILK", "BANANA");
    public static final CatalogMeal EGGS = meal(2, "Scrambled eggs on toast", "BREAKFAST", 10, "390.0", "EGG", "WHEAT", "GLUTEN");
    public static final CatalogMeal CHICKEN_BOWL = meal(4, "Chicken rice bowl", "LUNCH", 20, "629.9", "CHICKEN", "RICE");
    public static final CatalogMeal PASTA = meal(5, "Pasta bolognese", "LUNCH", 30, "567.8", "WHEAT", "BEEF");
    public static final CatalogMeal LENTIL_SOUP = meal(6, "Lentil soup", null, 35, "475.9", "LENTIL", "VEGETABLES");
    public static final CatalogMeal SALMON = meal(7, "Salmon with potatoes", "DINNER", 30, "560.5", "FISH", "POTATO");
    public static final CatalogMeal TOFU = meal(8, "Tofu vegetable stir-fry", "DINNER", 20, "565.4", "SOY", "VEGETABLES");
    public static final CatalogMeal BUCKWHEAT = meal(9, "Buckwheat with chicken", "DINNER", 25, "447.0", "BUCKWHEAT", "CHICKEN");
    public static final CatalogMeal PEANUT_TOAST = meal(12, "Peanut butter toast", "SNACK", 3, "380.0", "PEANUT", "WHEAT");

    public static final List<CatalogMeal> MEALS = List.of(OATMEAL, EGGS, CHICKEN_BOWL, PASTA, LENTIL_SOUP, SALMON, TOFU,
            BUCKWHEAT, PEANUT_TOAST);

    public static final CatalogExercise RUN = exercise(1, "Running, 10 km/h", "9.8");
    public static final CatalogExercise WALK = exercise(2, "Brisk walking", "4.3");
    public static final CatalogExercise CIRCUIT = exercise(3, "Bodyweight circuit", "8.0");
    public static final CatalogExercise DUMBBELLS = exercise(5, "Dumbbell strength training", "5.0", "DUMBBELLS");
    public static final List<CatalogExercise> EXERCISES = List.of(RUN, WALK, CIRCUIT, DUMBBELLS);

    private Fixtures() {
    }

    public static CatalogMeal meal(long id, String name, String slot, int prep, String kcal, String... ingredients) {
        return new CatalogMeal(id, name, slot, prep, new BigDecimal(kcal), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                Set.of(ingredients));
    }

    public static CatalogExercise exercise(long id, String name, String met, String... equipment) {
        return new CatalogExercise(id, name, new BigDecimal(met), Set.of(equipment));
    }

    public static DayConditions day(Integer minutes, Set<String> equipment, Set<String> dayExcluded, Set<String> permanent) {
        return new DayConditions(minutes, equipment, dayExcluded, permanent);
    }

    public static DayConditions unrestricted() {
        return DayConditions.unrestricted(Set.of());
    }

    public static PlanItemView mealItem(CatalogMeal meal, String slot, String time, String status) {
        return new PlanItemView(UUID.randomUUID(), UUID.randomUUID(), "MEAL", slot, LocalTime.parse(time), meal.id(), meal.name(),
                null, null, meal.kcal(), meal.proteinG(), meal.fatG(), meal.carbsG(), null, status);
    }

    public static PlanItemView workoutItem(CatalogExercise exercise, String time, int minutes, String status) {
        return new PlanItemView(UUID.randomUUID(), UUID.randomUUID(), "WORKOUT", null, LocalTime.parse(time), null, null,
                exercise.id(), exercise.name(), null, null, null, null, minutes, status);
    }

    public static PlanView plan(boolean recoveryRequired, PlanItemView... items) {
        return new PlanView(UUID.randomUUID(), UUID.randomUUID(), DAY, 1, "INITIAL_PLAN", Instant.parse("2026-11-15T20:00:00Z"),
                recoveryRequired, List.of(items));
    }
}
