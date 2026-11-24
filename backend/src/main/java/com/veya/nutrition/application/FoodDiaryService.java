package com.veya.nutrition.application;

import com.veya.nutrition.domain.FoodEntry;
import com.veya.nutrition.domain.FoodEntry.InputSource;
import com.veya.nutrition.infrastructure.CatalogRepository;
import com.veya.nutrition.infrastructure.DailyNutritionView;
import com.veya.nutrition.infrastructure.DailyNutritionView.DayTotals;
import com.veya.nutrition.infrastructure.FoodEntryRepository;
import com.veya.planning.api.PlanReferences;
import com.veya.planning.api.PlanReferences.ItemType;
import com.veya.profile.api.ProfileLookup;
import com.veya.profile.api.ProfileLookup.Targets;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Food diary: log and correct confirmed entries (US-06), daily totals against the profile targets (US-07) and recent
 * foods (US-08). Invalid values are rejected before anything is written, so totals are not affected.
 */
@Service
public class FoodDiaryService {

    private static final Logger log = LoggerFactory.getLogger(FoodDiaryService.class);

    /** Entry plus the day's plan state after the change ({@code null} = no plan for the date). */
    public record EntryResult(FoodEntry entry, Boolean recoveryRequired) {
    }

    public record Summary(LocalDate date, DayTotals consumed, Optional<Targets> target) {
    }

    /** Values of a new entry as confirmed by the user. */
    public record NewEntry(LocalDate date, String mealSlot, String inputSource, Long foodId, UUID planItemKey,
                           String foodName, BigDecimal portionG, BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG,
                           BigDecimal carbsG) {
    }

    /** Fields of a correction; {@code null} = unchanged. */
    public record Correction(String mealSlot, String foodName, BigDecimal portionG, BigDecimal kcal, BigDecimal proteinG,
                             BigDecimal fatG, BigDecimal carbsG) {
    }

    private final FoodEntryRepository entries;
    private final DailyNutritionView totals;
    private final CatalogRepository catalog;
    private final ReferenceCodes codes;
    private final PlanReferences plans;
    private final ProfileLookup profiles;
    private final EntityManager entityManager;

    public FoodDiaryService(FoodEntryRepository entries, DailyNutritionView totals, CatalogRepository catalog,
                            ReferenceCodes codes, PlanReferences plans, ProfileLookup profiles, EntityManager entityManager) {
        this.entries = entries;
        this.totals = totals;
        this.catalog = catalog;
        this.codes = codes;
        this.plans = plans;
        this.profiles = profiles;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<FoodEntry> day(UUID userId, LocalDate date) {
        return entries.findDay(userId, date);
    }

    @Transactional(readOnly = true)
    public List<FoodEntry> recent(UUID userId, int limit) {
        List<UUID> ids = entries.findRecentIds(userId, limit);
        Map<UUID, FoodEntry> byId = entries.findAllById(ids).stream()
                .collect(Collectors.toMap(FoodEntry::getId, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    /** US-06: stores the confirmed values unchanged; optionally linked to a planned MEAL of the same date (US-15). */
    @Transactional
    public EntryResult create(UUID userId, NewEntry e) {
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.MEAL_SLOT, "mealSlot", e.mealSlot(), errors);
        if ("CATALOG".equals(e.inputSource()) && e.foodId() == null) {
            errors.add(FieldViolation.of("foodId", "is required when inputSource is CATALOG"));
        }
        if (e.foodId() != null && !catalog.foodExists(e.foodId())) {
            errors.add(new FieldViolation("foodId", "is not a known food", e.foodId()));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        UUID planItemId = e.planItemKey() == null ? null
                : plans.resolveActiveItem(userId, e.planItemKey(), e.date(), ItemType.MEAL, "planItemKey").planItemId();
        FoodEntry entry = persist(new FoodEntry(userId, e.date(), e.mealSlot(), InputSource.valueOf(e.inputSource()),
                e.foodId(), null, planItemId, e.foodName().trim(), e.portionG(), e.kcal(), e.proteinG(), e.fatG(), e.carbsG()));
        log.info("Food entry created userId={} entryId={} linkedToPlan={}", userId, entry.getId(), planItemId != null);
        return result(entry);
    }

    /** US-06 correction; a linked, already confirmed meal is re-evaluated by the database (200 kcal rule). */
    @Transactional
    public EntryResult correct(UUID userId, UUID entryId, Correction c) {
        FoodEntry entry = owned(userId, entryId);
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.MEAL_SLOT, "mealSlot", c.mealSlot(), errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        entry.correct(c.mealSlot(), c.foodName() == null ? null : c.foodName().trim(), c.portionG(), c.kcal(),
                c.proteinG(), c.fatG(), c.carbsG());
        entries.flush();
        entityManager.refresh(entry);
        log.info("Food entry corrected userId={} entryId={}", userId, entryId);
        return result(entry);
    }

    /** AQ-10 (PROPOSED in the contract): removing a wrongly logged entry is part of "log and correct". */
    @Transactional
    public void delete(UUID userId, UUID entryId) {
        entries.delete(owned(userId, entryId));
        entries.flush();
        log.info("Food entry deleted userId={} entryId={}", userId, entryId);
    }

    /** US-07: totals of persisted confirmed entries compared with the profile targets. */
    @Transactional(readOnly = true)
    public Summary summary(UUID userId, LocalDate date) {
        return new Summary(date, totals.dayTotals(userId, date).orElse(DayTotals.NONE), profiles.targets(userId));
    }

    /** Saves, flushes (database triggers run now) and re-reads generated values and formulas. */
    FoodEntry persist(FoodEntry entry) {
        FoodEntry saved = entries.saveAndFlush(entry);
        entityManager.refresh(saved);
        return saved;
    }

    /** AQ-2 (contract): unknown id -> 404, another user's entry -> 403. */
    private FoodEntry owned(UUID userId, UUID entryId) {
        FoodEntry entry = entries.findById(entryId).orElseThrow(ApiException::notFound);
        if (!entry.isOwnedBy(userId)) {
            throw ApiException.accessDenied();
        }
        return entry;
    }

    EntryResult result(FoodEntry entry) {
        return new EntryResult(entry, plans.recoveryRequired(entry.getUserId(), entry.getDate()).orElse(null));
    }
}
