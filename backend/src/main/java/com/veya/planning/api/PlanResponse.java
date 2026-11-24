package com.veya.planning.api;

import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.planning.api.PlanQueries.PlanView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * JSON shape of a plan (schemas {@code Plan}, {@code PlanItem}, {@code PlanItemRef} of docs/api/openapi.yaml). Shared by
 * the planning, recovery and history endpoints so a plan is rendered identically everywhere.
 */
public final class PlanResponse {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public record MealRef(long mealId, String name) {
    }

    public record ExerciseRef(long exerciseId, String name) {
    }

    public record Item(UUID itemKey, String status, String itemType, String mealSlot, String plannedTime, MealRef meal,
                       ExerciseRef exercise, BigDecimal plannedKcal, BigDecimal plannedProteinG, BigDecimal plannedFatG,
                       BigDecimal plannedCarbsG, Integer durationMin) {
    }

    /** {@code PlanItemRef}: an item value without key and status (proposed values of a recovery change). */
    public record ItemRef(String itemType, String mealSlot, String plannedTime, MealRef meal, ExerciseRef exercise,
                          BigDecimal plannedKcal, BigDecimal plannedProteinG, BigDecimal plannedFatG,
                          BigDecimal plannedCarbsG, Integer durationMin) {
    }

    public record Plan(UUID planId, LocalDate date, int activeVersion, String changeReason, Instant versionCreatedAt,
                       boolean recoveryRequired, List<Item> items) {
    }

    private PlanResponse() {
    }

    public static Plan of(PlanView view) {
        return new Plan(view.planId(), view.date(), view.activeVersion(), view.changeReason(), view.versionCreatedAt(),
                view.recoveryRequired(), view.items().stream().map(PlanResponse::item).toList());
    }

    public static Item item(PlanItemView i) {
        return new Item(i.itemKey(), i.status(), i.itemType(), i.mealSlot(), time(i.plannedTime()),
                i.mealId() == null ? null : new MealRef(i.mealId(), i.mealName()),
                i.exerciseId() == null ? null : new ExerciseRef(i.exerciseId(), i.exerciseName()),
                i.plannedKcal(), i.plannedProteinG(), i.plannedFatG(), i.plannedCarbsG(), i.durationMin());
    }

    public static String time(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }
}
