package com.veya.planning.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.veya.identity.api.CurrentUser;
import com.veya.planning.api.Alternatives.ExerciseOption;
import com.veya.planning.api.Alternatives.MealOption;
import com.veya.planning.api.Alternatives.Reason;
import com.veya.planning.api.Deviations.Deviation;
import com.veya.planning.api.PlanQueries.PlanView;
import com.veya.planning.api.PlanResponse;
import com.veya.planning.api.PlanResponse.Plan;
import com.veya.planning.application.PlanService;
import com.veya.planning.application.PlanService.ItemInput;
import com.veya.planning.application.PlanService.Options;
import com.veya.planning.application.PlanService.StatusResult;
import com.veya.planning.application.PlanService.SuggestionResult;
import com.veya.profile.api.ProfileLookup.Targets;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.FieldViolation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Planning operations of docs/api/openapi.yaml (tag planning; exercise-options is tagged activity). */
@RestController
@RequestMapping("/api/v1")
public class PlanController {

    static final String HH_MM = "^([01][0-9]|2[0-3]):[0-5][0-9]$";
    static final List<String> DEFAULT_SLOTS = List.of("BREAKFAST", "LUNCH", "DINNER");

    public record MealPlanSuggestionRequest(@NotNull LocalDate date, @Size(min = 1) List<@NotNull String> mealSlots) {
    }

    public record PlanItemInput(
            @NotNull @Pattern(regexp = "MEAL|WORKOUT", message = "must be MEAL or WORKOUT") String itemType,
            String mealSlot,
            @Pattern(regexp = HH_MM, message = "must be HH:mm") String plannedTime,
            @Positive Long mealId,
            @Positive Long exerciseId,
            @Min(1) @Max(600) Integer durationMin) {
    }

    public record CreatePlanRequest(@NotNull LocalDate date, @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid PlanItemInput> items) {
    }

    public record ReplacementOptionsRequest(@NotNull UUID planId, @NotNull UUID itemKey) {
    }

    public record ReplacementRequest(@NotNull @Min(1) Integer baseVersion, @Positive Long mealId, @Positive Long exerciseId,
                                     @Min(1) @Max(600) Integer durationMin) {
    }

    public record ItemStatusRequest(
            @NotNull @Pattern(regexp = "COMPLETED|SKIPPED|MISSED", message = "must be COMPLETED, SKIPPED or MISSED") String status) {
    }

    public record Macros(BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SuggestedItem(String itemType, String mealSlot, String plannedTime, long mealId, String mealName,
                                BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
    }

    public record MealPlanSuggestion(LocalDate date, List<SuggestedItem> items, Macros totals, Macros target) {
    }

    public record MealOptionView(long mealId, String name, BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG,
                                 BigDecimal carbsG, int prepTimeMin, List<Reason> reasons) {
    }

    public record MealOptions(UUID planId, UUID itemKey, int baseVersion, List<MealOptionView> options) {
    }

    public record ExerciseOptionView(long exerciseId, String name, int durationMin, List<Reason> reasons) {
    }

    public record ExerciseOptions(UUID planId, UUID itemKey, int baseVersion, List<ExerciseOptionView> options) {
    }

    public record ItemStatusResult(UUID planId, PlanResponse.Item item, boolean recoveryRequired, List<Deviation> newDeviations) {
    }

    private final PlanService plans;
    private final ReferenceCodes codes;

    public PlanController(PlanService plans, ReferenceCodes codes) {
        this.plans = plans;
        this.codes = codes;
    }

    /** operationId suggestMealPlan (US-09) - nothing is saved. */
    @PostMapping("/meal-plan-suggestions")
    public MealPlanSuggestion suggestMealPlan(CurrentUser user, @Valid @RequestBody MealPlanSuggestionRequest r) {
        List<String> slots = r.mealSlots() == null ? DEFAULT_SLOTS : r.mealSlots();
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.MEAL_SLOT, "mealSlots", slots, errors);
        if (new HashSet<>(slots).size() != slots.size() && errors.isEmpty()) {
            errors.add(FieldViolation.of("mealSlots", "must not contain duplicates"));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        SuggestionResult result = plans.suggest(user.id(), r.date(), slots);
        List<SuggestedItem> items = result.suggestion().choices().stream()
                .map(c -> new SuggestedItem("MEAL", c.slot(), null, c.meal().id(), c.meal().name(), c.meal().kcal(),
                        c.meal().proteinG(), c.meal().fatG(), c.meal().carbsG()))
                .toList();
        Macros totals = new Macros(sum(items, SuggestedItem::kcal), sum(items, SuggestedItem::proteinG),
                sum(items, SuggestedItem::fatG), sum(items, SuggestedItem::carbsG));
        Targets t = result.target();
        return new MealPlanSuggestion(r.date(), items, totals, new Macros(t.kcal(), t.proteinG(), t.fatG(), t.carbsG()));
    }

    /** operationId getPlanByDate (US-09, US-12, US-20). */
    @GetMapping("/plans")
    public Plan getPlanByDate(CurrentUser user,
                              @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return PlanResponse.of(plans.byDate(user.id(), date));
    }

    /** operationId createPlan (US-09, US-12): version 1 (INITIAL_PLAN). */
    @PostMapping("/plans")
    public ResponseEntity<Plan> createPlan(CurrentUser user, @Valid @RequestBody CreatePlanRequest r) {
        List<ItemInput> items = r.items().stream()
                .map(i -> new ItemInput(i.itemType(), i.mealSlot(), i.plannedTime() == null ? null : LocalTime.parse(i.plannedTime()),
                        i.mealId(), i.exerciseId(), i.durationMin()))
                .toList();
        PlanView plan = plans.create(user.id(), r.date(), items);
        return ResponseEntity.created(URI.create("/api/v1/plans/" + plan.planId())).body(PlanResponse.of(plan));
    }

    /** operationId getPlan. */
    @GetMapping("/plans/{planId}")
    public Plan getPlan(CurrentUser user, @PathVariable("planId") UUID planId) {
        return PlanResponse.of(plans.get(user.id(), planId));
    }

    /** operationId getMealOptions (US-10) - nothing is saved. */
    @PostMapping("/meal-options")
    public MealOptions getMealOptions(CurrentUser user, @Valid @RequestBody ReplacementOptionsRequest r) {
        Options<MealOption> o = plans.mealOptions(user.id(), r.planId(), r.itemKey());
        return new MealOptions(o.plan().planId(), o.itemKey(), o.plan().activeVersion(), o.options().stream()
                .map(m -> new MealOptionView(m.meal().id(), m.meal().name(), m.meal().kcal(), m.meal().proteinG(),
                        m.meal().fatG(), m.meal().carbsG(), m.meal().prepTimeMin(), m.reasons()))
                .toList());
    }

    /** operationId getExerciseOptions (US-14) - nothing is saved. */
    @PostMapping("/exercise-options")
    public ExerciseOptions getExerciseOptions(CurrentUser user, @Valid @RequestBody ReplacementOptionsRequest r) {
        Options<ExerciseOption> o = plans.exerciseOptions(user.id(), r.planId(), r.itemKey());
        return new ExerciseOptions(o.plan().planId(), o.itemKey(), o.plan().activeVersion(), o.options().stream()
                .map(e -> new ExerciseOptionView(e.exercise().id(), e.exercise().name(), e.durationMin(), e.reasons()))
                .toList());
    }

    /** operationId replacePlanItem (US-10, US-14): creates the next version. */
    @PostMapping("/plans/{planId}/items/{itemKey}/replacement")
    public Plan replacePlanItem(CurrentUser user, @PathVariable("planId") UUID planId, @PathVariable("itemKey") UUID itemKey,
                                @Valid @RequestBody ReplacementRequest r) {
        return PlanResponse.of(plans.replace(user.id(), planId, itemKey, r.baseVersion(), r.mealId(), r.exerciseId(),
                r.durationMin()));
    }

    /** operationId recordPlanItemStatus (US-13, US-15). */
    @PostMapping("/plans/{planId}/items/{itemKey}/status")
    public ItemStatusResult recordPlanItemStatus(CurrentUser user, @PathVariable("planId") UUID planId,
                                                 @PathVariable("itemKey") UUID itemKey, @Valid @RequestBody ItemStatusRequest r) {
        StatusResult result = plans.recordStatus(user.id(), planId, itemKey, r.status());
        return new ItemStatusResult(planId, PlanResponse.item(result.item()), result.plan().recoveryRequired(),
                result.newDeviations());
    }

    /** operationId listOpenDeviations (US-15). */
    @GetMapping("/plans/{planId}/deviations")
    public List<Deviation> listOpenDeviations(CurrentUser user, @PathVariable("planId") UUID planId) {
        return plans.openDeviations(user.id(), planId);
    }

    private static BigDecimal sum(List<SuggestedItem> items, java.util.function.Function<SuggestedItem, BigDecimal> f) {
        return items.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
