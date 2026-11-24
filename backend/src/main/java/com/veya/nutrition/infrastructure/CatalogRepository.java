package com.veya.nutrition.infrastructure;

import com.veya.shared.persistence.SqlLike;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Local food catalogue ({@code food_item}) and catalogue meals ({@code v_meal_nutrition}) - US-05, US-09, US-31. */
@Repository
public class CatalogRepository {

    public record FoodRow(long foodId, String name, String source, BigDecimal kcalPer100g, BigDecimal proteinPer100g,
                          BigDecimal fatPer100g, BigDecimal carbsPer100g) {
    }

    public record MealRow(long mealId, String name, String mealSlot, int prepTimeMin, BigDecimal kcal, BigDecimal proteinG,
                          BigDecimal fatG, BigDecimal carbsG, List<String> ingredientCodes) {
    }

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Case-insensitive substring search (trigram index {@code food_item_name_trgm_idx}); names starting with the text
     * first, then by name. LIKE wildcards in the text are escaped.
     */
    public List<FoodRow> searchFoods(String text, int limit) {
        String escaped = SqlLike.escape(text);
        return jdbc.sql("""
                        SELECT id AS food_id, name, source, kcal_per_100g AS kcal_per100g, protein_per_100g AS protein_per100g,
                               fat_per_100g AS fat_per100g, carbs_per_100g AS carbs_per100g
                        FROM food_item
                        WHERE lower(name) LIKE '%' || :text || '%'
                        ORDER BY (lower(name) LIKE :text || '%') DESC, lower(name), id
                        LIMIT :limit""")
                .param("text", escaped).param("limit", limit).query(FoodRow.class).list();
    }

    /**
     * Caches a normalized provider product as a PROVIDER row (unique on source + external_ref), so it gets a stable
     * foodId for diary entries and stays searchable when the provider is down (R1 fallback).
     */
    public FoodRow cacheProviderFood(com.veya.integration.food.FoodProvider.ProviderFood f) {
        return jdbc.sql("""
                        INSERT INTO food_item (name, source, external_ref, kcal_per_100g, protein_per_100g, fat_per_100g,
                                               carbs_per_100g, fetched_at)
                        VALUES (:name, 'PROVIDER', :ref, :kcal, :protein, :fat, :carbs, now())
                        ON CONFLICT (source, external_ref) DO UPDATE SET name = EXCLUDED.name,
                            kcal_per_100g = EXCLUDED.kcal_per_100g, protein_per_100g = EXCLUDED.protein_per_100g,
                            fat_per_100g = EXCLUDED.fat_per_100g, carbs_per_100g = EXCLUDED.carbs_per_100g, fetched_at = now()
                        RETURNING id AS food_id, name, source, kcal_per_100g AS kcal_per100g, protein_per_100g AS protein_per100g,
                                  fat_per_100g AS fat_per100g, carbs_per_100g AS carbs_per100g""")
                .param("name", f.name()).param("ref", f.externalRef()).param("kcal", f.kcalPer100g())
                .param("protein", f.proteinPer100g()).param("fat", f.fatPer100g()).param("carbs", f.carbsPer100g())
                .query(FoodRow.class).single();
    }

    public boolean foodExists(long foodId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM food_item WHERE id = :id)").param("id", foodId).query(Boolean.class).single();
    }

    /** Catalogue meals without the user's permanent exclusions (US-09, US-03); meals without a slot fit every slot. */
    public List<MealRow> candidateMeals(UUID userId, String mealSlot, String text, int limit) {
        String escaped = SqlLike.escape(text);
        return jdbc.sql("""
                        SELECT meal_id, meal_name, meal_slot_code, prep_time_min, kcal, protein_g, fat_g, carbs_g, ingredient_codes
                        FROM v_meal_nutrition m
                        WHERE (CAST(:slot AS varchar) IS NULL OR m.meal_slot_code IS NULL OR m.meal_slot_code = :slot)
                          AND (CAST(:text AS varchar) IS NULL OR lower(m.meal_name) LIKE '%' || :text || '%')
                          AND NOT (m.ingredient_codes::text[] && ARRAY(SELECT e.ingredient_code::text FROM profile_food_exclusion e
                                                              WHERE e.user_id = :user))
                        ORDER BY lower(m.meal_name), m.meal_id
                        LIMIT :limit""")
                .param("slot", mealSlot).param("text", escaped).param("user", userId).param("limit", limit)
                .query((rs, n) -> new MealRow(rs.getLong("meal_id"), rs.getString("meal_name"), rs.getString("meal_slot_code"),
                        rs.getInt("prep_time_min"), rs.getBigDecimal("kcal"), rs.getBigDecimal("protein_g"),
                        rs.getBigDecimal("fat_g"), rs.getBigDecimal("carbs_g"), strings(rs.getArray("ingredient_codes"))))
                .list();
    }

    public Optional<String> foodName(long foodId) {
        return jdbc.sql("SELECT name FROM food_item WHERE id = :id").param("id", foodId).query(String.class).optional();
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
