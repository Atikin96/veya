package com.veya.planning.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic hard-constraint rules (BA §2.4, US-03, US-10, US-14, US-15 rule 3, US-16). Pure functions without
 * Spring or database access, shared by plan creation, replacement options, the constraint-change check and the
 * recovery engine, so every feature applies exactly the same rules.
 * <ul>
 *     <li>a meal must not contain a permanently excluded ingredient (FOOD_EXCLUSION) or one excluded for the day
 *     (INGREDIENT);</li>
 *     <li>a meal's preparation time and a workout's duration must fit {@code availableTimeMin} (TIME), if set;</li>
 *     <li>a workout's required equipment must be available (EQUIPMENT), if the day's equipment is known.</li>
 * </ul>
 * {@code availableIngredients} is not a hard constraint (open question AQ-13).
 */
public final class HardConstraints {

    public enum ViolationType { TIME, EQUIPMENT, INGREDIENT, FOOD_EXCLUSION, NUTRITION_TARGET }

    public record Violation(ViolationType type, String message) {
    }

    /** Catalogue meal with nutrition derived from its components (view v_meal_nutrition). */
    public record CatalogMeal(long id, String name, String mealSlot, int prepTimeMin, BigDecimal kcal, BigDecimal proteinG,
                              BigDecimal fatG, BigDecimal carbsG, Set<String> ingredients) {

        /** Meals without a preferred slot fit every slot. */
        public boolean fitsSlot(String slot) {
            return mealSlot == null || mealSlot.equals(slot);
        }
    }

    public record CatalogExercise(long id, String name, BigDecimal metValue, Set<String> requiredEquipment) {
    }

    /**
     * Conditions of a day. {@code availableEquipment == null} means the user has not saved constraints for the day
     * (equipment unrestricted); an empty set means no equipment.
     */
    public record DayConditions(Integer availableTimeMin, Set<String> availableEquipment, Set<String> dayExcluded,
                                Set<String> permanentExcluded) {

        public static DayConditions unrestricted(Set<String> permanentExcluded) {
            return new DayConditions(null, null, Set.of(), permanentExcluded);
        }
    }

    private HardConstraints() {
    }

    public static List<Violation> check(CatalogMeal meal, DayConditions day) {
        List<Violation> violations = new ArrayList<>();
        Set<String> permanent = intersection(meal.ingredients(), day.permanentExcluded());
        if (!permanent.isEmpty()) {
            violations.add(new Violation(ViolationType.FOOD_EXCLUSION,
                    "'" + meal.name() + "' contains " + String.join(", ", permanent) + ", which is excluded in your profile."));
        }
        Set<String> today = intersection(meal.ingredients(), day.dayExcluded());
        today.removeAll(permanent);
        if (!today.isEmpty()) {
            violations.add(new Violation(ViolationType.INGREDIENT,
                    "'" + meal.name() + "' contains " + String.join(", ", today) + ", which is excluded today."));
        }
        if (day.availableTimeMin() != null && meal.prepTimeMin() > day.availableTimeMin()) {
            violations.add(new Violation(ViolationType.TIME, "'" + meal.name() + "' needs " + meal.prepTimeMin()
                    + " minutes of preparation; only " + day.availableTimeMin() + " minutes are available."));
        }
        return violations;
    }

    public static List<Violation> check(CatalogExercise exercise, int durationMin, DayConditions day) {
        List<Violation> violations = new ArrayList<>();
        if (day.availableEquipment() != null) {
            Set<String> missing = new TreeSet<>(exercise.requiredEquipment());
            missing.removeAll(day.availableEquipment());
            if (!missing.isEmpty()) {
                violations.add(new Violation(ViolationType.EQUIPMENT, "'" + exercise.name() + "' needs "
                        + String.join(", ", missing) + ", which is not available."));
            }
        }
        if (day.availableTimeMin() != null && durationMin > day.availableTimeMin()) {
            violations.add(new Violation(ViolationType.TIME, "'" + exercise.name() + "' takes " + durationMin
                    + " minutes; only " + day.availableTimeMin() + " minutes are available."));
        }
        return violations;
    }

    /** Why no meal fits a slot: the violation types found among the slot's catalogue meals. */
    public static Violation summarize(String slot, List<CatalogMeal> fitting, DayConditions day) {
        String name = slotName(slot);
        if (fitting.isEmpty()) {
            return new Violation(ViolationType.NUTRITION_TARGET, "The catalogue has no meal for " + name + ".");
        }
        Map<ViolationType, Integer> counts = new EnumMap<>(ViolationType.class);
        for (CatalogMeal meal : fitting) {
            for (Violation v : check(meal, day)) {
                counts.merge(v.type(), 1, Integer::sum);
            }
        }
        ViolationType main = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(ViolationType.NUTRITION_TARGET);
        StringBuilder sb = new StringBuilder("All " + fitting.size() + " " + name + " candidates break a constraint:");
        counts.forEach((type, n) -> sb.append(' ').append(n).append(' ').append(describe(type)).append(';'));
        sb.setLength(sb.length() - 1);
        return new Violation(main, sb.append('.').toString());
    }

    static String describe(ViolationType type) {
        return switch (type) {
            case FOOD_EXCLUSION -> "contain ingredients excluded in your profile";
            case INGREDIENT -> "contain ingredients excluded today";
            case TIME -> "need more time than available";
            case EQUIPMENT -> "need unavailable equipment";
            case NUTRITION_TARGET -> "miss the nutrition target";
        };
    }

    /** Human-readable name of a meal slot code ("DINNER" -> "dinner"). */
    public static String slotName(String slot) {
        return slot == null ? "meal" : slot.toLowerCase(Locale.ROOT);
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.retainAll(b);
        return result;
    }
}
