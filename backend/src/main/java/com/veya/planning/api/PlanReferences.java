package com.veya.planning.api;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Published queries of the planning module used by the diary (nutrition) and workout records (activity): resolving
 * the logical {@code planItemKey} to the item of the active plan version, and reading a day's recoveryRequired flag.
 */
public interface PlanReferences {

    enum ItemType { MEAL, WORKOUT }

    /** An item of the active plan version that a diary/workout record may refer to. */
    record ActiveItem(UUID planItemId, UUID itemKey, String status) {
    }

    /**
     * Resolves {@code itemKey} for a record of {@code userId} on {@code date}. Errors: another user's item -> 403
     * ACCESS_DENIED (AQ-2); unknown key, wrong item type or another date -> 400 VALIDATION_FAILED on {@code field};
     * the key exists only in a superseded version -> 409 IMMUTABLE_HISTORY.
     */
    ActiveItem resolveActiveItem(UUID userId, UUID itemKey, LocalDate date, ItemType type, String field);

    /** @return the day's recoveryRequired flag, empty when the user has no plan for the date. */
    Optional<Boolean> recoveryRequired(UUID userId, LocalDate date);
}
