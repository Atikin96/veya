package com.veya.recovery.domain;

import com.veya.planning.api.Alternatives;
import com.veya.planning.api.Alternatives.ExerciseOption;
import com.veya.planning.api.Alternatives.Ranked;
import com.veya.planning.api.Alternatives.Reason;
import com.veya.planning.api.Alternatives.ReasonCategory;
import com.veya.planning.api.HardConstraints;
import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.HardConstraints.Violation;
import com.veya.planning.api.HardConstraints.ViolationType;
import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.planning.api.PlanQueries.PlanView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dynamic Plan Recovery engine (EPIC 4, UC-01 steps 4-5, Fig. B.4). Deterministic and constraint-based, no LLM:
 * <ol>
 *     <li>items that are not PLANNED are frozen and never changed (US-16);</li>
 *     <li>the remaining energy target is derived from confirmed facts only: profile target minus confirmed diary kcal;</li>
 *     <li>candidates are filtered by the hard constraints (exclusions, time, equipment) of {@link HardConstraints};</li>
 *     <li>feasible meal combinations for the remaining meals are ranked by
 *     {@code |remaining target - planned kcal| + CHANGE_PENALTY_KCAL x changed meals} (ties: fewer changes, lower ids),
 *     so a meal changes only when the change brings the day clearly closer to the target or the meal became
 *     infeasible;</li>
 *     <li>an infeasible remaining workout is replaced by the best feasible exercise, or removed if none exists; a
 *     skipped/missed workout without a remaining one gets one feasible replacement workout added;</li>
 *     <li>every change carries a human-readable reason with a constraint category (US-17).</li>
 * </ol>
 * If a remaining meal has no feasible candidate, or no change can be proposed, the result is a no-solution with
 * blocking constraints (US-19). Bounded search: at most 100 candidates per type (NFR-02), at most
 * {@link #CANDIDATES_PER_MEAL} per meal in the combination search.
 */
public final class RecoveryEngine {

    public static final BigDecimal CHANGE_PENALTY_KCAL = BigDecimal.valueOf(50);
    static final int CANDIDATES_PER_MEAL = 8;
    static final int MAX_COMBINATIONS = 50_000;

    public enum ChangeType { REPLACE, REMOVE, ADD }

    /** {@code target} is null for ADD; {@code newMeal}/{@code newExercise} are null for REMOVE. */
    public record Change(ChangeType type, PlanItemView target, String itemType, String mealSlot, java.time.LocalTime plannedTime,
                         CatalogMeal newMeal, CatalogExercise newExercise, Integer durationMin, Reason reason) {
    }

    public record Totals(BigDecimal targetKcal, BigDecimal consumedKcal, BigDecimal remainingPlannedKcalBefore,
                         BigDecimal remainingPlannedKcalAfter, BigDecimal forecastKcal) {
    }

    /** A constraint that blocks recovery; {@code itemKey} is the affected item, if any. */
    public record Blocked(ViolationType type, String message, UUID itemKey) {
    }

    /** Either {@code changes} (a proposal) or {@code blocking} (no solution) is non-empty. */
    public record Result(List<Change> changes, Totals totals, List<Blocked> blocking) {

        public boolean feasible() {
            return blocking.isEmpty();
        }
    }

    public record Input(PlanView plan, DayConditions day, List<CatalogMeal> meals, List<CatalogExercise> exercises,
                        BigDecimal targetKcal, BigDecimal consumedKcal) {
    }

    private RecoveryEngine() {
    }

    public static Result propose(Input in) {
        Map<Long, CatalogMeal> mealsById = in.meals().stream().collect(Collectors.toMap(CatalogMeal::id, Function.identity()));
        Map<Long, CatalogExercise> exercisesById = in.exercises().stream()
                .collect(Collectors.toMap(CatalogExercise::id, Function.identity()));
        List<PlanItemView> remainingMeals = in.plan().items().stream().filter(i -> i.isPlanned() && i.isMeal()).toList();
        List<PlanItemView> remainingWorkouts = in.plan().items().stream().filter(i -> i.isPlanned() && !i.isMeal()).toList();
        BigDecimal before = sumKcal(remainingMeals);
        BigDecimal remainingTarget = in.targetKcal() == null ? before : in.targetKcal().subtract(in.consumedKcal());

        List<Change> changes = new ArrayList<>();
        List<Blocked> blocking = new ArrayList<>();

        // Meals: bounded combination search over the feasible candidates of every remaining meal.
        List<List<CatalogMeal>> candidates = new ArrayList<>();
        for (PlanItemView item : remainingMeals) {
            CatalogMeal current = mealsById.get(item.mealId());
            BigDecimal share = remainingMeals.isEmpty() ? BigDecimal.ZERO
                    : remainingTarget.divide(BigDecimal.valueOf(remainingMeals.size()), 1, java.math.RoundingMode.HALF_UP);
            List<CatalogMeal> feasible = in.meals().stream()
                    .filter(m -> m.fitsSlot(item.mealSlot()) && HardConstraints.check(m, in.day()).isEmpty())
                    .sorted(Comparator.comparing((CatalogMeal m) -> m.kcal().subtract(share).abs()).thenComparingLong(CatalogMeal::id))
                    .limit(CANDIDATES_PER_MEAL).collect(Collectors.toCollection(ArrayList::new));
            if (current != null && HardConstraints.check(current, in.day()).isEmpty() && !feasible.contains(current)) {
                feasible.add(0, current); // keeping the current meal is always a candidate when it is still feasible
            }
            if (feasible.isEmpty()) {
                List<CatalogMeal> fitting = in.meals().stream().filter(m -> m.fitsSlot(item.mealSlot())).toList();
                Violation summary = HardConstraints.summarize(item.mealSlot(), fitting, in.day());
                blocking.add(new Blocked(summary.type(), summary.message(), item.itemKey()));
            }
            candidates.add(feasible);
        }
        if (!blocking.isEmpty()) {
            return noSolution(blocking, in, before);
        }
        List<CatalogMeal> chosen = bestCombination(remainingMeals, candidates, remainingTarget);
        BigDecimal after = chosen.stream().map(CatalogMeal::kcal).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int i = 0; i < remainingMeals.size(); i++) {
            PlanItemView item = remainingMeals.get(i);
            CatalogMeal meal = chosen.get(i);
            if (!Objects.equals(item.mealId(), meal.id())) {
                changes.add(new Change(ChangeType.REPLACE, item, "MEAL", item.mealSlot(), item.plannedTime(), meal, null, null,
                        mealReason(item, mealsById.get(item.mealId()), meal, in, before, after)));
            }
        }

        // Workouts: replace or remove infeasible remaining workouts.
        for (PlanItemView item : remainingWorkouts) {
            CatalogExercise current = exercisesById.get(item.exerciseId());
            List<Violation> violations = HardConstraints.check(current, item.durationMin(), in.day());
            if (violations.isEmpty()) {
                continue;
            }
            Ranked<ExerciseOption> options = Alternatives.exercises(current, item.durationMin(), in.exercises(), in.day());
            ReasonCategory category = category(violations.get(0).type());
            if (options.options().isEmpty()) {
                changes.add(new Change(ChangeType.REMOVE, item, null, null, null, null, null, null, new Reason(category,
                        violations.get(0).message() + " No feasible alternative exists, so the workout is removed.")));
            } else {
                ExerciseOption best = options.options().get(0);
                changes.add(new Change(ChangeType.REPLACE, item, "WORKOUT", null, item.plannedTime(), null, best.exercise(),
                        best.durationMin(), new Reason(category, violations.get(0).message() + " '" + best.exercise().name()
                        + "' (" + best.durationMin() + " min) fits the available time and equipment.")));
            }
        }

        // A skipped or missed workout without another remaining one: add one feasible replacement workout.
        boolean workoutRemains = remainingWorkouts.stream().anyMatch(w -> changes.stream()
                .noneMatch(c -> c.type() == ChangeType.REMOVE && c.target() == w));
        List<PlanItemView> skipped = in.plan().items().stream()
                .filter(i -> !i.isMeal() && ("SKIPPED".equals(i.status()) || "MISSED".equals(i.status()))).toList();
        if (!skipped.isEmpty() && !workoutRemains) {
            PlanItemView missed = skipped.get(skipped.size() - 1);
            Ranked<ExerciseOption> options = Alternatives.exercises(exercisesById.get(missed.exerciseId()),
                    missed.durationMin(), in.exercises(), in.day());
            if (!options.options().isEmpty()) {
                ExerciseOption best = options.options().get(0);
                changes.add(new Change(ChangeType.ADD, null, "WORKOUT", null, null, null, best.exercise(), best.durationMin(),
                        new Reason(ReasonCategory.TIME, "The planned '" + missed.exerciseName() + "' was "
                                + missed.status().toLowerCase() + "; '" + best.exercise().name() + "' (" + best.durationMin()
                                + " min) fits the available time and equipment.")));
            }
        }

        Totals totals = totals(in, before, after);
        if (changes.isEmpty()) {
            return new Result(List.of(), totals, List.of(new Blocked(ViolationType.NUTRITION_TARGET,
                    "No feasible change improves the remaining plan: it is already the closest feasible option to your "
                            + "target and no compensating workout fits your constraints.", null)));
        }
        return new Result(changes, totals, List.of());
    }

    /** Exhaustive search (bounded by {@link #MAX_COMBINATIONS}); greedy per meal beyond that bound. */
    static List<CatalogMeal> bestCombination(List<PlanItemView> items, List<List<CatalogMeal>> candidates, BigDecimal target) {
        long combinations = 1;
        for (List<CatalogMeal> c : candidates) {
            combinations = Math.min(combinations * c.size(), Long.MAX_VALUE / 100);
        }
        if (items.isEmpty()) {
            return List.of();
        }
        if (combinations > MAX_COMBINATIONS) {
            return candidates.stream().map(c -> c.get(0)).toList();
        }
        List<CatalogMeal> best = null;
        BigDecimal bestScore = null;
        int bestChanges = Integer.MAX_VALUE;
        int[] index = new int[candidates.size()];
        while (true) {
            List<CatalogMeal> combo = new ArrayList<>();
            int changed = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < index.length; i++) {
                CatalogMeal meal = candidates.get(i).get(index[i]);
                combo.add(meal);
                total = total.add(meal.kcal());
                if (!Objects.equals(items.get(i).mealId(), meal.id())) {
                    changed++;
                }
            }
            BigDecimal score = total.subtract(target).abs().add(CHANGE_PENALTY_KCAL.multiply(BigDecimal.valueOf(changed)));
            if (best == null || score.compareTo(bestScore) < 0 || (score.compareTo(bestScore) == 0 && changed < bestChanges)) {
                best = combo;
                bestScore = score;
                bestChanges = changed;
            }
            int pos = index.length - 1;
            while (pos >= 0 && ++index[pos] == candidates.get(pos).size()) {
                index[pos] = 0;
                pos--;
            }
            if (pos < 0) {
                return best;
            }
        }
    }

    private static Reason mealReason(PlanItemView item, CatalogMeal current, CatalogMeal replacement, Input in,
                                     BigDecimal before, BigDecimal after) {
        String slot = HardConstraints.slotName(item.mealSlot());
        if (current != null) {
            List<Violation> violations = HardConstraints.check(current, in.day());
            if (!violations.isEmpty()) {
                return new Reason(category(violations.get(0).type()), violations.get(0).message() + " '" + replacement.name()
                        + "' fits your constraints (" + replacement.prepTimeMin() + " min preparation, "
                        + replacement.kcal() + " kcal).");
            }
        }
        BigDecimal forecastBefore = in.consumedKcal().add(before);
        BigDecimal forecastAfter = in.consumedKcal().add(after);
        String target = in.targetKcal() == null ? "" : " (target " + in.targetKcal() + " kcal)";
        return new Reason(ReasonCategory.NUTRITION, "Replacing the " + slot + " with '" + replacement.name()
                + "' brings the day's forecast from " + forecastBefore + " to " + forecastAfter + " kcal" + target
                + ", based on the meals you have confirmed.");
    }

    static ReasonCategory category(ViolationType type) {
        return switch (type) {
            case TIME -> ReasonCategory.TIME;
            case EQUIPMENT -> ReasonCategory.EQUIPMENT;
            case INGREDIENT, FOOD_EXCLUSION -> ReasonCategory.INGREDIENT;
            case NUTRITION_TARGET -> ReasonCategory.NUTRITION;
        };
    }

    private static Result noSolution(List<Blocked> blocking, Input in, BigDecimal before) {
        return new Result(List.of(), totals(in, before, before), blocking);
    }

    private static Totals totals(Input in, BigDecimal before, BigDecimal after) {
        return new Totals(in.targetKcal(), in.consumedKcal(), before, after, in.consumedKcal().add(after));
    }

    static BigDecimal sumKcal(List<PlanItemView> meals) {
        return meals.stream().map(PlanItemView::plannedKcal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Item key of the item a change targets (null for ADD). */
    public static UUID targetKey(Change change) {
        return change.target() == null ? null : change.target().itemKey();
    }
}
