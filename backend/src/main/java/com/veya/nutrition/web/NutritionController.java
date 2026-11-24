package com.veya.nutrition.web;

import com.veya.identity.api.CurrentUser;
import com.veya.nutrition.application.FoodDiaryService;
import com.veya.nutrition.application.FoodSearchService;
import com.veya.nutrition.application.FoodDiaryService.Correction;
import com.veya.nutrition.application.FoodDiaryService.EntryResult;
import com.veya.nutrition.application.FoodDiaryService.NewEntry;
import com.veya.nutrition.application.FoodDiaryService.Summary;
import com.veya.nutrition.application.SavedMealService;
import com.veya.nutrition.application.SavedMealService.Line;
import com.veya.nutrition.infrastructure.CatalogRepository;
import com.veya.nutrition.domain.FoodEntry;
import com.veya.nutrition.domain.SavedMeal;
import com.veya.nutrition.web.NutritionDtos.FoodEntryPatch;
import com.veya.nutrition.web.NutritionDtos.FoodEntryRequest;
import com.veya.nutrition.web.NutritionDtos.FoodEntryResult;
import com.veya.nutrition.web.NutritionDtos.FoodItem;
import com.veya.nutrition.web.NutritionDtos.FoodSearchResult;
import com.veya.nutrition.web.NutritionDtos.LogSavedMealRequest;
import com.veya.nutrition.web.NutritionDtos.Macros;
import com.veya.nutrition.web.NutritionDtos.MealCandidate;
import com.veya.nutrition.web.NutritionDtos.NutritionSummary;
import com.veya.nutrition.web.NutritionDtos.SavedMealItem;
import com.veya.nutrition.web.NutritionDtos.SavedMealRequest;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.FieldViolation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Nutrition operations of docs/api/openapi.yaml (tag nutrition): US-05 to US-09, US-31. */
@RestController
@RequestMapping("/api/v1")
public class NutritionController {

    private final CatalogRepository catalog;
    private final FoodSearchService foodSearch;
    private final FoodDiaryService diary;
    private final SavedMealService savedMeals;
    private final ReferenceCodes codes;

    public NutritionController(CatalogRepository catalog, FoodSearchService foodSearch, FoodDiaryService diary,
                               SavedMealService savedMeals, ReferenceCodes codes) {
        this.catalog = catalog;
        this.foodSearch = foodSearch;
        this.diary = diary;
        this.savedMeals = savedMeals;
        this.codes = codes;
    }

    /** operationId searchFoods (US-05, US-31): local catalogue plus the optional provider, with fallback. */
    @GetMapping("/foods")
    public FoodSearchResult searchFoods(CurrentUser user,
                                        @RequestParam(name = "q") @Size(min = 2, max = 100) String q,
                                        @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(50) int limit) {
        FoodSearchService.Result result = foodSearch.search(q.trim(), limit);
        List<FoodItem> items = result.items().stream()
                .map(f -> new FoodItem(f.foodId(), f.name(), f.source(), f.kcalPer100g(), f.proteinPer100g(),
                        f.fatPer100g(), f.carbsPer100g(), "PROVIDER".equals(f.source()) ? result.attribution() : null))
                .toList();
        return new FoodSearchResult(result.providerStatus(), items);
    }

    /** operationId listMeals (US-09): candidate meals without the caller's permanent exclusions. */
    @GetMapping("/meals")
    @Transactional(readOnly = true)
    public List<MealCandidate> listMeals(CurrentUser user,
                                         @RequestParam(name = "mealSlot", required = false) String mealSlot,
                                         @RequestParam(name = "q", required = false) @Size(min = 2, max = 100) String q,
                                         @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(100) int limit) {
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.MEAL_SLOT, "mealSlot", mealSlot, errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return catalog.candidateMeals(user.id(), mealSlot, q == null ? null : q.trim(), limit).stream()
                .map(m -> new MealCandidate(m.mealId(), m.name(), m.mealSlot(), m.prepTimeMin(), m.kcal(), m.proteinG(),
                        m.fatG(), m.carbsG(), m.ingredientCodes()))
                .toList();
    }

    /** operationId listFoodEntries (US-06, US-07). */
    @GetMapping("/food-entries")
    public List<NutritionDtos.FoodEntry> listFoodEntries(CurrentUser user,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return diary.day(user.id(), date).stream().map(NutritionController::entry).toList();
    }

    /** operationId createFoodEntry (US-06, US-15). */
    @PostMapping("/food-entries")
    public ResponseEntity<FoodEntryResult> createFoodEntry(CurrentUser user, @Valid @RequestBody FoodEntryRequest r) {
        EntryResult result = diary.create(user.id(), new NewEntry(r.date(), r.mealSlot(), r.inputSource(), r.foodId(),
                r.planItemKey(), r.foodName(), r.portionG(), r.kcal(), r.proteinG(), r.fatG(), r.carbsG()));
        return ResponseEntity.created(URI.create("/api/v1/food-entries/" + result.entry().getId())).body(result(result));
    }

    /** operationId updateFoodEntry (US-06, US-15). */
    @PatchMapping("/food-entries/{entryId}")
    public FoodEntryResult updateFoodEntry(CurrentUser user, @PathVariable("entryId") UUID entryId,
                                           @Valid @RequestBody FoodEntryPatch p) {
        if (p.isEmpty()) {
            throw ApiException.validation("body", "must contain at least one field to change");
        }
        return result(diary.correct(user.id(), entryId,
                new Correction(p.mealSlot(), p.foodName(), p.portionG(), p.kcal(), p.proteinG(), p.fatG(), p.carbsG())));
    }

    /** operationId deleteFoodEntry (US-06, AQ-10). */
    @DeleteMapping("/food-entries/{entryId}")
    public ResponseEntity<Void> deleteFoodEntry(CurrentUser user, @PathVariable("entryId") UUID entryId) {
        diary.delete(user.id(), entryId);
        return ResponseEntity.noContent().build();
    }

    /** operationId listRecentFoodEntries (US-08). */
    @GetMapping("/food-entries/recent")
    public List<NutritionDtos.FoodEntry> listRecentFoodEntries(CurrentUser user,
            @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(20) int limit) {
        return diary.recent(user.id(), limit).stream().map(NutritionController::entry).toList();
    }

    /** operationId listSavedMeals (US-08). */
    @GetMapping("/saved-meals")
    public List<NutritionDtos.SavedMeal> listSavedMeals(CurrentUser user) {
        return savedMeals.list(user.id()).stream().map(NutritionController::savedMeal).toList();
    }

    /** operationId createSavedMeal (US-08). */
    @PostMapping("/saved-meals")
    public ResponseEntity<NutritionDtos.SavedMeal> createSavedMeal(CurrentUser user, @Valid @RequestBody SavedMealRequest r) {
        SavedMeal template = savedMeals.create(user.id(), r.name(), r.items().stream()
                .map(l -> new Line(l.foodId(), l.foodName(), l.portionG(), l.kcal(), l.proteinG(), l.fatG(), l.carbsG()))
                .toList());
        return ResponseEntity.created(URI.create("/api/v1/saved-meals/" + template.getId()))
                .body(savedMeal(template));
    }

    /** operationId logSavedMeal (US-08). */
    @PostMapping("/saved-meals/{savedMealId}/entries")
    public ResponseEntity<List<NutritionDtos.FoodEntry>> logSavedMeal(CurrentUser user, @PathVariable("savedMealId") UUID savedMealId,
                                                        @Valid @RequestBody LogSavedMealRequest r) {
        List<NutritionDtos.FoodEntry> created = savedMeals.log(user.id(), savedMealId, r.date(), r.mealSlot(), r.planItemKey())
                .stream().map(NutritionController::entry).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** operationId getNutritionSummary (US-07). Without a profile, target and remaining values are null. */
    @GetMapping("/days/{date}/nutrition")
    public NutritionSummary getNutritionSummary(CurrentUser user,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Summary s = diary.summary(user.id(), date);
        Macros consumed = new Macros(s.consumed().kcal(), s.consumed().proteinG(), s.consumed().fatG(), s.consumed().carbsG());
        Macros target = s.target().map(t -> new Macros(t.kcal(), t.proteinG(), t.fatG(), t.carbsG()))
                .orElse(new Macros(null, null, null, null));
        Macros remaining = s.target().map(t -> new Macros(t.kcal().subtract(consumed.kcal()),
                        t.proteinG().subtract(consumed.proteinG()), t.fatG().subtract(consumed.fatG()),
                        t.carbsG().subtract(consumed.carbsG())))
                .orElse(new Macros(null, null, null, null));
        return new NutritionSummary(date, consumed, target, remaining, s.consumed().entryCount());
    }

    static NutritionDtos.FoodEntry entry(FoodEntry e) {
        return new NutritionDtos.FoodEntry(e.getId(), e.getDate(), e.getMealSlot(), e.getInputSource(), e.getFoodId(),
                e.getSavedMealId(), e.getPlanItemKey(), e.getFoodName(), e.getPortionG(), e.getKcal(), e.getProteinG(),
                e.getFatG(), e.getCarbsG(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static FoodEntryResult result(EntryResult r) {
        return new FoodEntryResult(entry(r.entry()), r.recoveryRequired());
    }

    private static NutritionDtos.SavedMeal savedMeal(SavedMeal m) {
        List<SavedMealItem> items = m.getItems().stream()
                .map(l -> new SavedMealItem(l.getLineNo(), l.getFoodId(), l.getFoodName(), l.getPortionG(), l.getKcal(),
                        l.getProteinG(), l.getFatG(), l.getCarbsG()))
                .toList();
        return new NutritionDtos.SavedMeal(m.getId(), m.getName(), m.getCreatedAt(), items,
                new Macros(m.totalKcal(), m.totalProteinG(), m.totalFatG(), m.totalCarbsG()));
    }
}
