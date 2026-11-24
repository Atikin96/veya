package com.veya.api;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates plans for API tests through the database routine {@code create_plan_version} (the plan endpoints are not
 * implemented yet). Meal nutrition is taken from the catalogue view {@code v_meal_nutrition}.
 */
final class PlanFixtures {

    record Item(UUID key, String type, String slot, String time, String name, Integer durationMin) {
    }

    private PlanFixtures() {
    }

    static Item meal(UUID key, String slot, String time, String mealName) {
        return new Item(key, "MEAL", slot, time, mealName, null);
    }

    static Item workout(UUID key, String time, String exerciseName, int durationMin) {
        return new Item(key, "WORKOUT", null, time, exerciseName, durationMin);
    }

    /** Creates version 1 (INITIAL_PLAN) of the user's plan for the date. */
    static void createPlan(JdbcTemplate jdbc, UUID userId, LocalDate date, List<Item> items) {
        List<String> json = new ArrayList<>();
        for (Item item : items) {
            if (item.type().equals("MEAL")) {
                Map<String, Object> m = jdbc.queryForMap(
                        "SELECT meal_id, kcal, protein_g, fat_g, carbs_g FROM veya.v_meal_nutrition WHERE meal_name = ?", item.name());
                json.add(String.format(java.util.Locale.ROOT,
                        "{\"itemKey\":\"%s\",\"itemType\":\"MEAL\",\"mealSlot\":\"%s\",\"plannedTime\":\"%s\",\"mealId\":%s,"
                                + "\"kcal\":%s,\"proteinG\":%s,\"fatG\":%s,\"carbsG\":%s}",
                        item.key(), item.slot(), item.time(), m.get("meal_id"), m.get("kcal"), m.get("protein_g"),
                        m.get("fat_g"), m.get("carbs_g")));
            } else {
                Long exerciseId = jdbc.queryForObject("SELECT id FROM veya.exercise WHERE name = ?", Long.class, item.name());
                json.add(String.format("{\"itemKey\":\"%s\",\"itemType\":\"WORKOUT\",\"plannedTime\":\"%s\",\"exerciseId\":%d,"
                        + "\"durationMin\":%d}", item.key(), item.time(), exerciseId, item.durationMin()));
            }
        }
        jdbc.queryForObject("SELECT veya.create_plan_version(?, ?, 0, 'INITIAL_PLAN', ?::jsonb)", Integer.class,
                userId, date, "[" + String.join(",", json) + "]");
    }

    /** Creates the next version replacing all remaining items with {@code items} (simulates a replacement). */
    static void createNextVersion(JdbcTemplate jdbc, UUID userId, LocalDate date, int expectedVersion, String itemsJson) {
        jdbc.queryForObject("SELECT veya.create_plan_version(?, ?, ?, 'MEAL_REPLACEMENT', ?::jsonb)", Integer.class,
                userId, date, expectedVersion, itemsJson);
    }
}
