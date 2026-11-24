package com.veya.planning.domain;

import com.veya.planning.api.HardConstraints;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.HardConstraints.Violation;
import com.veya.planning.api.HardConstraints.ViolationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * US-09 meal plan suggestion: deterministic and constraint-based (BA §2.4). For every requested slot only meals that
 * fit the slot and pass {@link HardConstraints} are candidates; the combination of distinct meals whose energy is
 * closest to the profile target is chosen (ties: lower meal ids). It is accepted only within the configured
 * tolerance ("configured acceptable target ranges", US-09); otherwise the result explains why no plan is feasible.
 * Nothing is stored.
 */
public final class MealPlanner {

    /** Candidates kept per slot (closest to the per-slot share) - bounds the search to at most 10^slots combinations. */
    static final int CANDIDATES_PER_SLOT = 10;

    public record Choice(String slot, CatalogMeal meal) {
    }

    /** Either {@code choices} (feasible) or {@code blocking} (no feasible plan) is non-empty. */
    public record Suggestion(List<Choice> choices, BigDecimal totalKcal, List<Violation> blocking) {

        public boolean feasible() {
            return blocking.isEmpty();
        }
    }

    private MealPlanner() {
    }

    public static Suggestion suggest(List<String> slots, List<CatalogMeal> meals, DayConditions day, BigDecimal targetKcal,
                                     int tolerancePct) {
        if (targetKcal == null) {
            return infeasible(List.of(new Violation(ViolationType.NUTRITION_TARGET,
                    "Save your profile with a daily energy target first (PUT /api/v1/me/profile).")));
        }
        BigDecimal share = targetKcal.divide(BigDecimal.valueOf(slots.size()), 1, RoundingMode.HALF_UP);
        List<List<CatalogMeal>> candidates = new ArrayList<>();
        List<Violation> blocking = new ArrayList<>();
        for (String slot : slots) {
            List<CatalogMeal> fitting = meals.stream().filter(m -> m.fitsSlot(slot)).toList();
            List<CatalogMeal> feasible = fitting.stream().filter(m -> HardConstraints.check(m, day).isEmpty())
                    .sorted(Comparator.comparing((CatalogMeal m) -> m.kcal().subtract(share).abs()).thenComparingLong(CatalogMeal::id))
                    .limit(CANDIDATES_PER_SLOT).toList();
            if (feasible.isEmpty()) {
                blocking.add(HardConstraints.summarize(slot, fitting, day));
            }
            candidates.add(feasible);
        }
        if (!blocking.isEmpty()) {
            return infeasible(blocking);
        }
        List<CatalogMeal> best = search(candidates, targetKcal);
        if (best == null) {
            return infeasible(List.of(new Violation(ViolationType.NUTRITION_TARGET,
                    "Not enough different meals fit your constraints for " + slots.size() + " slots.")));
        }
        BigDecimal total = best.stream().map(CatalogMeal::kcal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allowed = targetKcal.multiply(BigDecimal.valueOf(tolerancePct)).divide(BigDecimal.valueOf(100), 1,
                RoundingMode.HALF_UP);
        if (total.subtract(targetKcal).abs().compareTo(allowed) > 0) {
            return infeasible(List.of(new Violation(ViolationType.NUTRITION_TARGET, "The closest feasible combination has "
                    + total + " kcal; the acceptable range is " + targetKcal.subtract(allowed) + "-" + targetKcal.add(allowed)
                    + " kcal (target " + targetKcal + " kcal +/- " + tolerancePct + "%).")));
        }
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            choices.add(new Choice(slots.get(i), best.get(i)));
        }
        return new Suggestion(choices, total, List.of());
    }

    /** Exhaustive search over the bounded candidate lists for the distinct combination closest to the target. */
    private static List<CatalogMeal> search(List<List<CatalogMeal>> candidates, BigDecimal target) {
        List<CatalogMeal> best = null;
        BigDecimal bestDiff = null;
        int[] index = new int[candidates.size()];
        while (true) {
            List<CatalogMeal> combo = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            boolean distinct = true;
            for (int i = 0; i < index.length; i++) {
                CatalogMeal meal = candidates.get(i).get(index[i]);
                distinct &= ids.add(meal.id());
                combo.add(meal);
            }
            if (distinct) {
                BigDecimal diff = combo.stream().map(CatalogMeal::kcal).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .subtract(target).abs();
                if (bestDiff == null || diff.compareTo(bestDiff) < 0) {
                    best = combo;
                    bestDiff = diff;
                }
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

    private static Suggestion infeasible(List<Violation> blocking) {
        return new Suggestion(List.of(), BigDecimal.ZERO, blocking);
    }
}
