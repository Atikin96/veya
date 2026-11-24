package com.veya.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bodies of the nutrition operations (schemas of docs/api/openapi.yaml with the same names). Nutrition values are
 * stored with one decimal place; more decimals are rejected so that confirmed values are persisted unchanged (US-06).
 */
public final class NutritionDtos {

    private NutritionDtos() {
    }

    public record FoodItem(long foodId, String name, String source, BigDecimal kcalPer100g, BigDecimal proteinPer100g,
                           BigDecimal fatPer100g, BigDecimal carbsPer100g, String attribution) {
    }

    public record FoodSearchResult(String providerStatus, List<FoodItem> items) {
    }

    public record MealCandidate(long mealId, String name, String mealSlot, int prepTimeMin, BigDecimal kcal,
                                BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG, List<String> ingredientCodes) {
    }

    public record FoodEntryRequest(
            @NotNull LocalDate date,
            @NotNull String mealSlot,
            @NotNull @Pattern(regexp = "CATALOG|MANUAL", message = "must be CATALOG or MANUAL") String inputSource,
            @Positive Long foodId,
            UUID planItemKey,
            @NotBlank @Size(max = 200) String foodName,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") @Digits(integer = 4, fraction = 1) BigDecimal portionG,
            @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 1) BigDecimal kcal,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal proteinG,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal fatG,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal carbsG) {
    }

    /** JSON merge-patch: only the sent fields change; at least one field is required. */
    public record FoodEntryPatch(
            String mealSlot,
            @Size(min = 1, max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String foodName,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") @Digits(integer = 4, fraction = 1) BigDecimal portionG,
            @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 1) BigDecimal kcal,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal proteinG,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal fatG,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal carbsG) {

        public boolean isEmpty() {
            return mealSlot == null && foodName == null && portionG == null && kcal == null && proteinG == null
                    && fatG == null && carbsG == null;
        }
    }

    public record FoodEntry(UUID entryId, LocalDate date, String mealSlot, String inputSource, Long foodId,
                            UUID savedMealId, UUID planItemKey, String foodName, BigDecimal portionG, BigDecimal kcal,
                            BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG, Instant createdAt,
                            Instant updatedAt) {
    }

    public record FoodEntryResult(FoodEntry entry, Boolean recoveryRequired) {
    }

    public record SavedMealLine(
            @Positive Long foodId,
            @NotBlank @Size(max = 200) String foodName,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") @Digits(integer = 4, fraction = 1) BigDecimal portionG,
            @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 1) BigDecimal kcal,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal proteinG,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal fatG,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal carbsG) {
    }

    public record SavedMealRequest(@NotBlank @Size(max = 100) String name,
                                   @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid SavedMealLine> items) {
    }

    public record SavedMealItem(int lineNo, Long foodId, String foodName, BigDecimal portionG, BigDecimal kcal,
                                BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
    }

    public record Macros(BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
    }

    public record SavedMeal(UUID savedMealId, String name, Instant createdAt, List<SavedMealItem> items, Macros totals) {
    }

    public record LogSavedMealRequest(@NotNull LocalDate date, @NotNull String mealSlot, UUID planItemKey) {
    }

    public record NutritionSummary(LocalDate date, Macros consumed, Macros target, Macros remaining, int entryCount) {
    }
}
