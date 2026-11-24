package com.veya.planning.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deviation events of a plan (US-15). Rules 1-2 are recorded by database triggers atomically with the write that
 * confirms a meal or records a skip; rule 3 (CONSTRAINT_INFEASIBLE) is recorded by the application.
 */
public interface Deviations {

    record Deviation(UUID deviationId, String type, UUID itemKey, BigDecimal kcalDifference, String details,
                     Instant detectedAt) {
    }

    /** Records an open CONSTRAINT_INFEASIBLE event for the item once (repeated detection does not duplicate it). */
    void recordConstraintInfeasible(UUID planId, UUID planItemId, String details);

    /** Open (unresolved) deviations of a plan, oldest first. */
    List<Deviation> open(UUID planId);
}
