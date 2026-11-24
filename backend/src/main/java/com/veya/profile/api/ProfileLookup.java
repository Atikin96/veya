package com.veya.profile.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Published query of the profile module for other modules. */
public interface ProfileLookup {

    /** Daily targets entered by the user (US-02; not calculated). */
    record Targets(BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
    }

    /** Saved planning constraints of a day (US-03). */
    record DayConstraintsView(Integer availableTimeMin, List<String> availableEquipment, List<String> availableIngredients,
                              List<String> excludedIngredients) {
    }

    boolean hasProfile(UUID userId);

    /** @return the day's saved constraints, empty when none were saved. */
    Optional<DayConstraintsView> dayConstraints(UUID userId, LocalDate date);

    /** @return the user's daily targets, empty when no profile exists yet. */
    Optional<Targets> targets(UUID userId);

    /** @return the user's permanent food exclusions (ingredient codes, Q5), empty without a profile. */
    List<String> permanentExclusions(UUID userId);
}
