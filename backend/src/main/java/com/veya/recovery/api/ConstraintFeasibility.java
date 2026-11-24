package com.veya.recovery.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * US-15 rule 3: after the daily constraints changed, finds remaining (PLANNED) items of the day's active plan version
 * that the constraints make infeasible, records a CONSTRAINT_INFEASIBLE deviation per item (which sets
 * recoveryRequired) and reports the impact. The plan itself is never changed here.
 */
public interface ConstraintFeasibility {

    record InfeasibleItem(UUID itemKey, String reason) {
    }

    record PlanImpact(UUID planId, boolean recoveryRequired, List<InfeasibleItem> infeasibleItems) {
    }

    /** Evaluates the saved constraints of the day; empty when the user has no plan for the date. */
    Optional<PlanImpact> evaluate(UUID userId, LocalDate date);
}
