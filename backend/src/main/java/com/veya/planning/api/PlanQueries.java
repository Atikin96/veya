package com.veya.planning.api;

import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Published read model of the planning module (active plan versions, catalogue, day conditions). */
public interface PlanQueries {

    record PlanItemView(UUID planItemId, UUID itemKey, String itemType, String mealSlot, LocalTime plannedTime, Long mealId,
                        String mealName, Long exerciseId, String exerciseName, BigDecimal plannedKcal,
                        BigDecimal plannedProteinG, BigDecimal plannedFatG, BigDecimal plannedCarbsG, Integer durationMin,
                        String status) {

        public boolean isMeal() {
            return "MEAL".equals(itemType);
        }

        public boolean isPlanned() {
            return "PLANNED".equals(status);
        }
    }

    /** The active version of a plan; items sorted by planned time (items without time last). */
    record PlanView(UUID planId, UUID userId, LocalDate date, int activeVersion, String changeReason, Instant versionCreatedAt,
                    boolean recoveryRequired, List<PlanItemView> items) {
    }

    /** Owned plan by id: unknown -> 404 RESOURCE_NOT_FOUND, another user's -> 403 ACCESS_DENIED (AQ-2). */
    PlanView ownedPlan(UUID userId, UUID planId);

    Optional<PlanView> planByDate(UUID userId, LocalDate date);

    /** Catalogue meals (bounded, NFR-02). */
    List<CatalogMeal> meals();

    List<CatalogExercise> exercises();

    /** Hard constraints of the user's day: saved daily constraints plus permanent exclusions. */
    DayConditions conditions(UUID userId, LocalDate date);

    /** Sum of confirmed diary kcal of the day (US-16: remaining targets from confirmed facts only). */
    BigDecimal consumedKcal(UUID userId, LocalDate date);
}
