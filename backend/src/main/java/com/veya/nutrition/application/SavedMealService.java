package com.veya.nutrition.application;

import com.veya.nutrition.domain.FoodEntry;
import com.veya.nutrition.domain.FoodEntry.InputSource;
import com.veya.nutrition.domain.SavedMeal;
import com.veya.nutrition.domain.SavedMealItem;
import com.veya.nutrition.infrastructure.CatalogRepository;
import com.veya.nutrition.infrastructure.SavedMealRepository;
import com.veya.planning.api.PlanReferences;
import com.veya.planning.api.PlanReferences.ItemType;
import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import com.veya.shared.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** US-08: saved meal templates and re-logging them without changing the template. */
@Service
public class SavedMealService {

    private static final Logger log = LoggerFactory.getLogger(SavedMealService.class);

    public record Line(Long foodId, String foodName, BigDecimal portionG, BigDecimal kcal, BigDecimal proteinG,
                       BigDecimal fatG, BigDecimal carbsG) {
    }

    private final SavedMealRepository savedMeals;
    private final FoodDiaryService diary;
    private final CatalogRepository catalog;
    private final ReferenceCodes codes;
    private final PlanReferences plans;

    public SavedMealService(SavedMealRepository savedMeals, FoodDiaryService diary, CatalogRepository catalog,
                            ReferenceCodes codes, PlanReferences plans) {
        this.savedMeals = savedMeals;
        this.diary = diary;
        this.catalog = catalog;
        this.codes = codes;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<SavedMeal> list(UUID userId) {
        return savedMeals.findAllOfUser(userId);
    }

    @Transactional
    public SavedMeal create(UUID userId, String name, List<Line> lines) {
        String trimmed = name.trim();
        List<FieldViolation> errors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Long foodId = lines.get(i).foodId();
            if (foodId != null && !catalog.foodExists(foodId)) {
                errors.add(new FieldViolation("items[" + i + "].foodId", "is not a known food", foodId));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (savedMeals.existsByUserIdAndName(userId, trimmed)) {
            throw nameTaken(trimmed);
        }
        SavedMeal meal = new SavedMeal(userId, trimmed);
        lines.forEach(l -> meal.addLine(l.foodId(), l.foodName().trim(), l.portionG(), l.kcal(), l.proteinG(), l.fatG(), l.carbsG()));
        try {
            SavedMeal saved = savedMeals.saveAndFlush(meal);
            log.info("Saved meal created userId={} savedMealId={} lines={}", userId, saved.getId(), lines.size());
            return savedMeals.findById(saved.getId()).orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            if ("saved_meal_user_name_uq".equals(DatabaseErrors.constraintName(ex))) {
                throw nameTaken(trimmed);
            }
            throw ex;
        }
    }

    /** One diary entry (input source SAVED_MEAL) per template line; the template is not changed (US-08). */
    @Transactional
    public List<FoodEntry> log(UUID userId, UUID savedMealId, LocalDate date, String mealSlot, UUID planItemKey) {
        SavedMeal meal = savedMeals.findById(savedMealId).orElseThrow(ApiException::notFound);
        if (!meal.isOwnedBy(userId)) {
            throw ApiException.accessDenied();
        }
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.MEAL_SLOT, "mealSlot", mealSlot, errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        UUID planItemId = planItemKey == null ? null
                : plans.resolveActiveItem(userId, planItemKey, date, ItemType.MEAL, "planItemKey").planItemId();
        List<FoodEntry> created = new ArrayList<>();
        for (SavedMealItem line : meal.getItems()) {
            created.add(diary.persist(new FoodEntry(userId, date, mealSlot, InputSource.SAVED_MEAL, line.getFoodId(),
                    savedMealId, planItemId, line.getFoodName(), line.getPortionG(), line.getKcal(), line.getProteinG(),
                    line.getFatG(), line.getCarbsG())));
        }
        log.info("Saved meal logged userId={} savedMealId={} entries={}", userId, savedMealId, created.size());
        return created;
    }

    private static ApiException nameTaken(String name) {
        return new ApiException(ErrorCode.SAVED_MEAL_NAME_TAKEN, "You already have a saved meal called \"" + name + "\".");
    }
}
