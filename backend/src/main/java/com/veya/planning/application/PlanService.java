package com.veya.planning.application;

import com.veya.planning.api.Alternatives;
import com.veya.planning.api.Alternatives.ExerciseOption;
import com.veya.planning.api.Alternatives.MealOption;
import com.veya.planning.api.Alternatives.Ranked;
import com.veya.planning.api.Deviations;
import com.veya.planning.api.Deviations.Deviation;
import com.veya.planning.api.HardConstraints;
import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.HardConstraints.Violation;
import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.planning.api.PlanQueries.PlanView;
import com.veya.planning.domain.MealPlanner;
import com.veya.planning.domain.MealPlanner.Suggestion;
import com.veya.planning.infrastructure.JdbcPlanStore;
import com.veya.profile.api.ProfileLookup;
import com.veya.profile.api.ProfileLookup.Targets;
import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.BlockingConstraint;
import com.veya.shared.web.ErrorCode;
import com.veya.shared.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Planning use cases: meal plan suggestion (US-09), saving a plan as version 1 (US-09, US-12), reading the active
 * version, replacement options and confirmed replacements (US-10, US-14, UC-02) and item status (US-13, US-15).
 * Versions are created only through {@code create_plan_version} (immutable versions, optimistic locking by version).
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    public record ItemInput(String itemType, String mealSlot, LocalTime plannedTime, Long mealId, Long exerciseId,
                            Integer durationMin) {
    }

    public record SuggestionResult(LocalDate date, Suggestion suggestion, Targets target) {
    }

    /** Options for one item of the active version ({@code baseVersion} = the plan's active version). */
    public record Options<T>(PlanView plan, UUID itemKey, List<T> options) {
    }

    public record StatusResult(PlanView plan, PlanItemView item, List<Deviation> newDeviations) {
    }

    private final JdbcPlanStore store;
    private final Deviations deviations;
    private final ProfileLookup profiles;
    private final ReferenceCodes codes;
    private final JsonMapper json;
    private final int tolerancePct;

    public PlanService(JdbcPlanStore store, Deviations deviations, ProfileLookup profiles, ReferenceCodes codes,
                       JsonMapper json, @Value("${veya.planning.target-tolerance-pct:20}") int tolerancePct) {
        this.codes = codes;
        this.store = store;
        this.deviations = deviations;
        this.profiles = profiles;
        this.json = json;
        this.tolerancePct = tolerancePct;
    }

    /** US-09: nothing is stored (AQ-12: suggestion and explicit save are two steps). */
    @Transactional(readOnly = true)
    public SuggestionResult suggest(UUID userId, LocalDate date, List<String> slots) {
        Targets target = profiles.targets(userId).orElse(null);
        Suggestion suggestion = MealPlanner.suggest(slots, store.meals(), store.conditions(userId, date),
                target == null ? null : target.kcal(), tolerancePct);
        if (!suggestion.feasible()) {
            throw blocked(ErrorCode.NO_FEASIBLE_MEAL_PLAN, "No combination of candidate meals satisfies your constraints and target.",
                    suggestion.blocking(), null);
        }
        return new SuggestionResult(date, suggestion, target);
    }

    /** US-09 / US-12: saves version 1 with a nutrition snapshot from the catalogue; hard constraints are enforced. */
    @Transactional
    public PlanView create(UUID userId, LocalDate date, List<ItemInput> items) {
        List<CatalogMeal> meals = store.meals();
        List<CatalogExercise> exercises = store.exercises();
        DayConditions day = store.conditions(userId, date);
        List<FieldViolation> errors = new ArrayList<>();
        List<BlockingConstraint> blocking = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> row = itemRow(items.get(i), "items[" + i + "]", meals, exercises, day, errors, blocking);
            if (row != null) {
                rows.add(row);
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (!blocking.isEmpty()) {
            throw new ApiException(ErrorCode.HARD_CONSTRAINT_VIOLATION, blocking.get(0).message(), List.of(),
                    Map.of("blockingConstraints", blocking));
        }
        if (store.planExists(userId, date)) {
            throw planExists(date);
        }
        try {
            store.createVersion(userId, date, 0, "INITIAL_PLAN", json.writeValueAsString(rows));
        } catch (DataAccessException ex) { // concurrent creation for the same date
            if ("VY409".equals(DatabaseErrors.sqlState(ex))) {
                throw planExists(date);
            }
            throw ex;
        }
        PlanView plan = store.planByDate(userId, date).orElseThrow();
        log.info("Plan created userId={} planId={} date={} items={}", userId, plan.planId(), date, rows.size());
        return plan;
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID userId, UUID planId) {
        return store.ownedPlan(userId, planId);
    }

    @Transactional(readOnly = true)
    public PlanView byDate(UUID userId, LocalDate date) {
        return store.planByDate(userId, date).orElseThrow(() ->
                new ApiException(ErrorCode.PLAN_NOT_FOUND, "There is no plan for " + date + "."));
    }

    /** US-10: hard-constraint-compliant alternatives for a PLANNED meal; nothing is saved. */
    @Transactional(readOnly = true)
    public Options<MealOption> mealOptions(UUID userId, UUID planId, UUID itemKey) {
        PlanView plan = store.ownedPlan(userId, planId);
        PlanItemView item = plannedItem(plan, itemKey, "MEAL");
        Ranked<MealOption> ranked = Alternatives.meals(item.mealSlot(), item.mealId(), item.plannedKcal(), store.meals(),
                store.conditions(userId, plan.date()));
        if (ranked.options().isEmpty()) {
            throw blocked(ErrorCode.NO_FEASIBLE_ALTERNATIVE, "No meal fits the remaining time and exclusions.", ranked.blocking(),
                    itemKey);
        }
        return new Options<>(plan, itemKey, ranked.options());
    }

    /** US-14: compliant exercises for a PLANNED workout; nothing is saved. */
    @Transactional(readOnly = true)
    public Options<ExerciseOption> exerciseOptions(UUID userId, UUID planId, UUID itemKey) {
        PlanView plan = store.ownedPlan(userId, planId);
        PlanItemView item = plannedItem(plan, itemKey, "WORKOUT");
        CatalogExercise current = Alternatives.byId(store.exercises(), CatalogExercise::id, item.exerciseId()).orElse(null);
        Ranked<ExerciseOption> ranked = Alternatives.exercises(current, item.durationMin(), store.exercises(),
                store.conditions(userId, plan.date()));
        if (ranked.options().isEmpty()) {
            throw blocked(ErrorCode.NO_FEASIBLE_ALTERNATIVE, "No exercise fits the available time and equipment.",
                    ranked.blocking(), itemKey);
        }
        return new Options<>(plan, itemKey, ranked.options());
    }

    /**
     * US-10 / US-14 / UC-02: confirmed replacement creates the next immutable version (MEAL_REPLACEMENT or
     * EXERCISE_REPLACEMENT) in one transaction; stale baseVersion -> 409 and nothing changes (NFR-10).
     */
    @Transactional
    public PlanView replace(UUID userId, UUID planId, UUID itemKey, int baseVersion, Long mealId, Long exerciseId,
                            Integer durationMin) {
        PlanView plan = store.ownedPlan(userId, planId);
        if (plan.activeVersion() != baseVersion) {
            throw stale(baseVersion, plan.activeVersion());
        }
        PlanItemView item = plannedItem(plan, itemKey, null);
        ItemInput input = item.isMeal()
                ? new ItemInput("MEAL", item.mealSlot(), item.plannedTime(), mealId, null, null)
                : new ItemInput("WORKOUT", null, item.plannedTime(), null, exerciseId, durationMin);
        List<FieldViolation> errors = new ArrayList<>();
        if (item.isMeal() && (mealId == null || exerciseId != null || durationMin != null)) {
            errors.add(FieldViolation.of("mealId", "is required (and only mealId) to replace a MEAL"));
        }
        if (!item.isMeal() && (exerciseId == null || durationMin == null || mealId != null)) {
            errors.add(FieldViolation.of("exerciseId", "exerciseId and durationMin are required to replace a WORKOUT"));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        List<BlockingConstraint> blocking = new ArrayList<>();
        Map<String, Object> replacement = itemRow(input, "body", store.meals(), store.exercises(),
                store.conditions(userId, plan.date()), errors, blocking);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (!blocking.isEmpty()) {
            throw new ApiException(ErrorCode.HARD_CONSTRAINT_VIOLATION, blocking.get(0).message(), List.of(),
                    Map.of("blockingConstraints", blocking.stream()
                            .map(b -> new BlockingConstraint(b.type(), b.message(), itemKey)).toList()));
        }
        replacement.put("itemKey", itemKey);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlanItemView other : plan.items()) {
            if (other.isPlanned()) {
                rows.add(other.itemKey().equals(itemKey) ? replacement : copy(other));
            }
        }
        String reason = item.isMeal() ? "MEAL_REPLACEMENT" : "EXERCISE_REPLACEMENT";
        try {
            store.createVersion(userId, plan.date(), baseVersion, reason, json.writeValueAsString(rows));
        } catch (DataAccessException ex) { // a concurrent change won the race
            if ("VY409".equals(DatabaseErrors.sqlState(ex))) {
                throw stale(baseVersion, baseVersion + 1);
            }
            throw ex;
        }
        PlanView updated = store.ownedPlan(userId, planId);
        log.info("Plan item replaced userId={} planId={} itemKey={} version={}", userId, planId, itemKey, updated.activeVersion());
        return updated;
    }

    /**
     * US-13 / US-15: records COMPLETED, SKIPPED or MISSED for a planned MEAL, or MISSED for a planned WORKOUT. The
     * database records the resulting deviations (>= 200 kcal for a confirmed meal; skipped/missed) atomically.
     */
    @Transactional
    public StatusResult recordStatus(UUID userId, UUID planId, UUID itemKey, String status) {
        PlanView plan = store.ownedPlan(userId, planId);
        PlanItemView item = plan.items().stream().filter(i -> i.itemKey().equals(itemKey)).findFirst()
                .orElseThrow(ApiException::notFound);
        if (!item.isMeal() && !"MISSED".equals(status)) {
            throw ApiException.validation("status", "a WORKOUT can only be marked MISSED here; record completed, skipped "
                    + "or changed workouts with POST /api/v1/activity-entries");
        }
        if (!item.isPlanned()) {
            throw new ApiException(ErrorCode.STATUS_ALREADY_RECORDED, "The status of this item is already " + item.status() + ".");
        }
        Set<UUID> before = new HashSet<>(deviations.open(planId).stream().map(Deviation::deviationId).toList());
        store.recordStatus(item.planItemId(), status);
        List<Deviation> created = deviations.open(planId).stream().filter(d -> !before.contains(d.deviationId())).toList();
        PlanView updated = store.ownedPlan(userId, planId);
        PlanItemView updatedItem = updated.items().stream().filter(i -> i.itemKey().equals(itemKey)).findFirst().orElseThrow();
        log.info("Plan item status recorded userId={} planId={} itemKey={} status={} newDeviations={}", userId, planId,
                itemKey, status, created.size());
        return new StatusResult(updated, updatedItem, created);
    }

    @Transactional(readOnly = true)
    public List<Deviation> openDeviations(UUID userId, UUID planId) {
        store.ownedPlan(userId, planId);
        return deviations.open(planId);
    }

    /** Validates one item and builds its create_plan_version row with the catalogue snapshot; null if invalid. */
    private Map<String, Object> itemRow(ItemInput in, String field, List<CatalogMeal> meals, List<CatalogExercise> exercises,
                                        DayConditions day, List<FieldViolation> errors, List<BlockingConstraint> blocking) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemType", in.itemType());
        row.put("plannedTime", in.plannedTime() == null ? null : in.plannedTime().toString());
        if ("MEAL".equals(in.itemType())) {
            if (in.mealSlot() == null || in.mealId() == null || in.exerciseId() != null || in.durationMin() != null) {
                errors.add(FieldViolation.of(field, "a MEAL needs mealSlot and mealId (and no exerciseId/durationMin)"));
                return null;
            }
            CatalogMeal meal = Alternatives.byId(meals, CatalogMeal::id, in.mealId()).orElse(null);
            if (meal == null) {
                errors.add(new FieldViolation(field + ".mealId", "is not a known meal", in.mealId()));
                return null;
            }
            int before = errors.size();
            codes.check(Vocabulary.MEAL_SLOT, field + ".mealSlot", in.mealSlot(), errors);
            if (errors.size() > before) {
                return null;
            }
            HardConstraints.check(meal, day).forEach(v -> blocking.add(constraint(v)));
            row.put("mealSlot", in.mealSlot());
            row.put("mealId", meal.id());
            row.put("kcal", meal.kcal());
            row.put("proteinG", meal.proteinG());
            row.put("fatG", meal.fatG());
            row.put("carbsG", meal.carbsG());
        } else if ("WORKOUT".equals(in.itemType())) {
            if (in.exerciseId() == null || in.durationMin() == null || in.mealSlot() != null || in.mealId() != null) {
                errors.add(FieldViolation.of(field, "a WORKOUT needs exerciseId and durationMin (and no mealSlot/mealId)"));
                return null;
            }
            CatalogExercise exercise = Alternatives.byId(exercises, CatalogExercise::id, in.exerciseId()).orElse(null);
            if (exercise == null) {
                errors.add(new FieldViolation(field + ".exerciseId", "is not a known exercise", in.exerciseId()));
                return null;
            }
            HardConstraints.check(exercise, in.durationMin(), day).forEach(v -> blocking.add(constraint(v)));
            row.put("exerciseId", exercise.id());
            row.put("durationMin", in.durationMin());
        } else {
            errors.add(new FieldViolation(field + ".itemType", "must be MEAL or WORKOUT", in.itemType()));
            return null;
        }
        return row;
    }

    /** create_plan_version row of an unchanged PLANNED item (planned values are kept as they are). */
    private static Map<String, Object> copy(PlanItemView i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemKey", i.itemKey());
        row.put("itemType", i.itemType());
        row.put("mealSlot", i.mealSlot());
        row.put("plannedTime", i.plannedTime() == null ? null : i.plannedTime().toString());
        row.put("mealId", i.mealId());
        row.put("exerciseId", i.exerciseId());
        row.put("kcal", i.plannedKcal());
        row.put("proteinG", i.plannedProteinG());
        row.put("fatG", i.plannedFatG());
        row.put("carbsG", i.plannedCarbsG());
        row.put("durationMin", i.durationMin());
        return row;
    }

    /** A PLANNED item of the active version (unknown -> 404, recorded -> 409 ITEM_FROZEN, wrong type -> 400). */
    private static PlanItemView plannedItem(PlanView plan, UUID itemKey, String type) {
        PlanItemView item = plan.items().stream().filter(i -> i.itemKey().equals(itemKey)).findFirst()
                .orElseThrow(ApiException::notFound);
        if (type != null && !type.equals(item.itemType())) {
            throw ApiException.validation("itemKey", "must be a planned " + type);
        }
        if (!item.isPlanned()) {
            throw new ApiException(ErrorCode.ITEM_FROZEN, "Completed or recorded items are frozen and cannot be replaced.");
        }
        return item;
    }

    private static BlockingConstraint constraint(Violation v) {
        return new BlockingConstraint(v.type().name(), v.message(), null);
    }

    private static ApiException blocked(ErrorCode code, String detail, List<Violation> violations, UUID itemKey) {
        return new ApiException(code, detail, List.of(), Map.of("blockingConstraints",
                violations.stream().map(v -> new BlockingConstraint(v.type().name(), v.message(), itemKey)).toList()));
    }

    static ApiException stale(int baseVersion, int currentVersion) {
        return new ApiException(ErrorCode.STALE_PLAN_VERSION, "Stale plan version: expected " + baseVersion + ", current "
                + currentVersion + ".", List.of(), Map.of("currentVersion", currentVersion));
    }

    private static ApiException planExists(LocalDate date) {
        return new ApiException(ErrorCode.PLAN_ALREADY_EXISTS, "A plan for " + date + " already exists.");
    }
}
