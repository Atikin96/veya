package com.veya.recovery.application;

import com.veya.planning.api.HardConstraints;
import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.HardConstraints.Violation;
import com.veya.planning.api.PlanQueries;
import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.planning.api.PlanQueries.PlanView;
import com.veya.recovery.api.ConstraintFeasibility;
import com.veya.planning.api.Deviations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-15 rule 3 (interpretation AQ-13, needs approval): a PLANNED item of the active version is infeasible when it
 * breaks a {@link HardConstraints} rule under the day's conditions. Each infeasible item is recorded once as an open
 * CONSTRAINT_INFEASIBLE deviation; the database trigger then sets recoveryRequired. The plan itself is not changed.
 */
@Service
public class ConstraintFeasibilityChecker implements ConstraintFeasibility {

    private final PlanQueries plans;
    private final Deviations deviations;

    public ConstraintFeasibilityChecker(PlanQueries plans, Deviations deviations) {
        this.plans = plans;
        this.deviations = deviations;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PlanImpact> evaluate(UUID userId, LocalDate date) {
        Optional<PlanView> plan = plans.planByDate(userId, date);
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        List<InfeasibleItem> infeasible = infeasibleItems(plan.get(), plans.conditions(userId, date),
                plans.meals().stream().collect(Collectors.toMap(CatalogMeal::id, Function.identity())),
                plans.exercises().stream().collect(Collectors.toMap(CatalogExercise::id, Function.identity())));
        Map<UUID, UUID> itemIds = plan.get().items().stream()
                .collect(Collectors.toMap(PlanItemView::itemKey, PlanItemView::planItemId));
        for (InfeasibleItem item : infeasible) {
            deviations.recordConstraintInfeasible(plan.get().planId(), itemIds.get(item.itemKey()), item.reason());
        }
        boolean recoveryRequired = plans.planByDate(userId, date).map(PlanView::recoveryRequired).orElse(false);
        return Optional.of(new PlanImpact(plan.get().planId(), recoveryRequired, infeasible));
    }

    /** Pure rule evaluation (unit-tested): reasons of the PLANNED items that break a hard constraint. */
    static List<InfeasibleItem> infeasibleItems(PlanView plan, DayConditions day, Map<Long, CatalogMeal> meals,
                                                Map<Long, CatalogExercise> exercises) {
        List<InfeasibleItem> result = new ArrayList<>();
        for (PlanItemView item : plan.items()) {
            if (!item.isPlanned()) {
                continue; // completed/recorded items are frozen (US-16)
            }
            List<Violation> violations = item.isMeal()
                    ? HardConstraints.check(meals.get(item.mealId()), day)
                    : HardConstraints.check(exercises.get(item.exerciseId()), item.durationMin(), day);
            if (!violations.isEmpty()) {
                String what = item.isMeal() ? "Planned " + HardConstraints.slotName(item.mealSlot()) : "Planned workout";
                result.add(new InfeasibleItem(item.itemKey(), what + ": " + violations.stream().map(Violation::message)
                        .collect(Collectors.joining(" ")) + " (" + plan.date() + ")"));
            }
        }
        return result;
    }
}
