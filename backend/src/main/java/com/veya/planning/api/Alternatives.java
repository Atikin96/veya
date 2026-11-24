package com.veya.planning.api;

import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.HardConstraints.ViolationType;
import com.veya.planning.api.HardConstraints.Violation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * US-10 / US-14: hard-constraint-compliant alternatives for one planned item, ranked deterministically, each with
 * human-readable reasons (US-17 categories). Pure functions, used by the option endpoints and the recovery engine.
 */
public final class Alternatives {

    /** At most this many options are returned (all of the at most 100 candidates are evaluated, NFR-02). */
    public static final int MAX_OPTIONS = 10;

    public enum ReasonCategory { NUTRITION, TIME, INGREDIENT, EQUIPMENT, OTHER }

    public record Reason(ReasonCategory category, String text) {
    }

    public record MealOption(CatalogMeal meal, List<Reason> reasons) {
    }

    public record ExerciseOption(CatalogExercise exercise, int durationMin, List<Reason> reasons) {
    }

    /** Options (best first) or, if there are none, the constraints that block every candidate. */
    public record Ranked<T>(List<T> options, List<Violation> blocking) {
    }

    private Alternatives() {
    }

    /** Meals for the slot other than the current one, closest to the planned energy first (ties: shorter prep, id). */
    public static Ranked<MealOption> meals(String slot, Long currentMealId, BigDecimal plannedKcal, List<CatalogMeal> catalog,
                                           DayConditions day) {
        List<CatalogMeal> fitting = catalog.stream().filter(m -> m.fitsSlot(slot) && (currentMealId == null || m.id() != currentMealId)).toList();
        List<MealOption> options = fitting.stream()
                .filter(m -> HardConstraints.check(m, day).isEmpty())
                .sorted(Comparator.comparing((CatalogMeal m) -> m.kcal().subtract(plannedKcal).abs())
                        .thenComparingInt(CatalogMeal::prepTimeMin).thenComparingLong(CatalogMeal::id))
                .limit(MAX_OPTIONS)
                .map(m -> new MealOption(m, mealReasons(m, slot, plannedKcal, day)))
                .toList();
        return new Ranked<>(options, options.isEmpty() ? List.of(HardConstraints.summarize(slot, fitting, day)) : List.of());
    }

    /**
     * Exercises other than the current one that fit the equipment and time; the duration is the planned one, shortened
     * to the available time. Ranked by closeness of the energy estimate (MET x minutes, LEG-6) to the planned workout.
     */
    public static Ranked<ExerciseOption> exercises(CatalogExercise current, int plannedDurationMin,
                                                   List<CatalogExercise> catalog, DayConditions day) {
        int duration = day.availableTimeMin() == null ? plannedDurationMin : Math.min(plannedDurationMin, day.availableTimeMin());
        List<CatalogExercise> others = catalog.stream().filter(e -> current == null || e.id() != current.id()).toList();
        // Suggestions are conservative: if the user has not saved the day's equipment, only equipment-free exercises are
        // proposed (an unknown kettlebell must not be assumed). Existing planned workouts are still judged unrestricted.
        DayConditions suggestDay = day.availableEquipment() != null ? day
                : new DayConditions(day.availableTimeMin(), Set.of(), day.dayExcluded(), day.permanentExcluded());
        if (duration < 1) {
            return new Ranked<>(List.of(), List.of(new Violation(ViolationType.TIME,
                    "No time is available for a workout today.")));
        }
        BigDecimal plannedLoad = current == null ? BigDecimal.ZERO : current.metValue().multiply(BigDecimal.valueOf(plannedDurationMin));
        List<ExerciseOption> options = others.stream()
                .filter(e -> HardConstraints.check(e, duration, suggestDay).isEmpty())
                .sorted(Comparator.comparing((CatalogExercise e) -> e.metValue().multiply(BigDecimal.valueOf(duration))
                        .subtract(plannedLoad).abs()).thenComparingLong(CatalogExercise::id))
                .limit(MAX_OPTIONS)
                .map(e -> new ExerciseOption(e, duration, exerciseReasons(e, duration, plannedDurationMin, suggestDay)))
                .toList();
        if (!options.isEmpty()) {
            return new Ranked<>(options, List.of());
        }
        Map<ViolationType, Integer> counts = new EnumMap<>(ViolationType.class);
        others.forEach(e -> HardConstraints.check(e, duration, suggestDay).forEach(v -> counts.merge(v.type(), 1, Integer::sum)));
        ViolationType main = counts.isEmpty() ? ViolationType.EQUIPMENT
                : counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return new Ranked<>(List.of(), List.of(new Violation(main, "No exercise fits " + duration
                + " minutes with the available equipment (" + others.size() + " candidates checked).")));
    }

    static List<Reason> mealReasons(CatalogMeal meal, String slot, BigDecimal plannedKcal, DayConditions day) {
        List<Reason> reasons = new ArrayList<>();
        if (!day.dayExcluded().isEmpty()) {
            reasons.add(new Reason(ReasonCategory.INGREDIENT, "Contains none of today's excluded ingredients ("
                    + String.join(", ", day.dayExcluded()) + ")."));
        }
        if (day.availableTimeMin() != null) {
            reasons.add(new Reason(ReasonCategory.TIME, "Takes " + meal.prepTimeMin() + " minutes; "
                    + day.availableTimeMin() + " minutes are available."));
        }
        reasons.add(new Reason(ReasonCategory.NUTRITION, energyText(meal.kcal(), plannedKcal, "the planned "
                + HardConstraints.slotName(slot))));
        return reasons;
    }

    static List<Reason> exerciseReasons(CatalogExercise exercise, int duration, int plannedDuration, DayConditions day) {
        List<Reason> reasons = new ArrayList<>();
        reasons.add(new Reason(ReasonCategory.EQUIPMENT, exercise.requiredEquipment().isEmpty() ? "Needs no equipment."
                : "Needs " + String.join(", ", exercise.requiredEquipment().stream().sorted().toList()) + ", which is available."));
        reasons.add(new Reason(ReasonCategory.TIME, duration < plannedDuration
                ? "Shortened to " + duration + " minutes to fit the available time."
                : "Keeps the planned " + duration + " minutes" + (day.availableTimeMin() == null ? "." : " within the "
                + day.availableTimeMin() + " available minutes.")));
        return reasons;
    }

    /** "85 kcal lighter than the planned dinner." / "Same energy as ..." */
    static String energyText(BigDecimal kcal, BigDecimal reference, String what) {
        BigDecimal diff = kcal.subtract(reference);
        int sign = diff.signum();
        if (sign == 0) {
            return "Same energy as " + what + ".";
        }
        return diff.abs().stripTrailingZeros().toPlainString() + " kcal " + (sign < 0 ? "lighter" : "heavier") + " than "
                + what + ".";
    }

    /** Finds a catalogue entry by id. */
    public static <T> Optional<T> byId(List<T> list, java.util.function.ToLongFunction<T> id, long value) {
        return list.stream().filter(t -> id.applyAsLong(t) == value).findFirst();
    }
}
