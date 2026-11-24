package com.veya.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Nutrition API over real HTTP: US-05 to US-09, US-31; ownership (US-30, AQ-2) and integrity rules. */
class NutritionApiTests extends ApiTestSupport {

    private static final String ENTRIES = "/api/v1/food-entries";
    private static final String ENTRY = "/api/v1/food-entries/{entryId}";

    static Map<String, Object> manual(String date, String slot, String name, double portion, double kcal) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("mealSlot", slot);
        body.put("inputSource", "MANUAL");
        body.put("foodName", name);
        body.put("portionG", portion);
        body.put("kcal", kcal);
        body.put("proteinG", 32);
        body.put("fatG", 38);
        body.put("carbsG", 110);
        return body;
    }

    @Test
    @DisplayName("US-05: food search is case-insensitive, normalized per 100 g with the source; invalid queries are rejected")
    void searchFoods() {
        TestUser user = newUser("search");
        JsonNode result = expect(200, "GET", "/api/v1/foods?q=CHICK", user.token(), null).json();
        assertThat(result.get("providerStatus").asString()).isEqualTo("UNAVAILABLE");
        assertThat(result.get("items").size()).isPositive();
        JsonNode first = result.get("items").get(0);
        assertThat(first.get("name").asString().toLowerCase()).startsWith("chick");
        assertThat(first.get("source").asString()).isEqualTo("LOCAL");
        assertThat(first.has("kcalPer100g")).isTrue();

        assertThat(expect(200, "GET", "/api/v1/foods?q=%25%25", user.token(), null).json().get("items").size()).isZero();
        assertThat(expect(200, "GET", "/api/v1/foods?q=a&limit=1".replace("q=a", "q=an"), user.token(), null)
                .json().get("items").size()).isLessThanOrEqualTo(1);
        assertProblem(expect(400, "GET", "/api/v1/foods?q=c", user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(400, "GET", "/api/v1/foods", user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(400, "GET", "/api/v1/foods?q=chick&limit=51", user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(401, "GET", "/api/v1/foods?q=chick", null, null), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("US-09/US-03: candidate meals exclude the user's permanent exclusions and respect the meal slot")
    void listMealsRespectsExclusions() {
        TestUser user = newUser("meals");
        String all = expect(200, "GET", "/api/v1/meals?mealSlot=DINNER", user.token(), null).body();
        assertThat(all).contains("Salmon with potatoes");

        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of("FISH")));
        JsonNode meals = expect(200, "GET", "/api/v1/meals?mealSlot=DINNER", user.token(), null).json();
        assertThat(meals.toString()).doesNotContain("Salmon with potatoes");
        for (JsonNode meal : meals) {
            assertThat(meal.get("ingredientCodes").toString()).doesNotContain("FISH");
            assertThat(meal.get("mealSlot").isNull() || meal.get("mealSlot").asString().equals("DINNER")).isTrue();
        }
        assertProblem(expect(400, "GET", "/api/v1/meals?mealSlot=BRUNCH", user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(400, "GET", "/api/v1/meals?limit=101", user.token(), null), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("US-06: confirmed entries are persisted unchanged; invalid values are rejected without affecting totals")
    void logAndValidateEntries() {
        TestUser user = newUser("diary");
        Response created = expect(201, "POST", ENTRIES, user.token(), manual("2026-11-16", "LUNCH", "Office pizza (3 slices)", 330, 879.9));
        JsonNode entry = created.json().get("entry");
        assertThat(entry.get("kcal").asDouble()).isEqualTo(879.9);
        assertThat(entry.get("planItemKey").isNull()).isTrue();
        assertThat(created.json().get("recoveryRequired").isNull()).isTrue();
        assertThat(created.header("Location")).isEqualTo("/api/v1/food-entries/" + entry.get("entryId").asString());
        assertThat(jdbc.queryForObject("SELECT kcal FROM veya.food_entry WHERE id = ?::uuid", Double.class,
                entry.get("entryId").asString())).isEqualTo(879.9);

        Map<String, Object> catalog = manual("2026-11-16", "SNACK", "Banana", 120, 106.8);
        catalog.put("inputSource", "CATALOG");
        assertThat(expect(400, "POST", ENTRIES, user.token(), catalog).body()).contains("foodId");
        catalog.put("foodId", 999999);
        assertThat(expect(400, "POST", ENTRIES, user.token(), catalog).body()).contains("foodId");
        Long banana = jdbc.queryForObject("SELECT id FROM veya.food_item WHERE name = 'Banana'", Long.class);
        catalog.put("foodId", banana);
        assertThat(expect(201, "POST", ENTRIES, user.token(), catalog).json().get("entry").get("foodId").asLong()).isEqualTo(banana);

        assertThat(expect(400, "POST", ENTRIES, user.token(), manual("2026-11-16", "SNACK", "Apple", 0, 50)).body()).contains("portionG");
        assertThat(expect(400, "POST", ENTRIES, user.token(), manual("2026-11-16", "SNACK", "Apple", 100, 52.25)).body()).contains("kcal");
        assertThat(expect(400, "POST", ENTRIES, user.token(), manual("2026-11-16", "BRUNCH", "Apple", 100, 52)).body()).contains("mealSlot");
        assertThat(expect(400, "POST", ENTRIES, user.token(), manual("2026-11-16", "SNACK", " ", 100, 52)).body()).contains("foodName");
        assertThat(expect(400, "POST", ENTRIES, user.token(), manual("2026-13-45", "SNACK", "Apple", 100, 52)).body()).contains("date");
        Map<String, Object> saved = manual("2026-11-16", "SNACK", "Apple", 100, 52);
        saved.put("inputSource", "SAVED_MEAL");
        assertThat(expect(400, "POST", ENTRIES, user.token(), saved).body()).contains("inputSource");
        assertProblem(expect(401, "POST", ENTRIES, null, manual("2026-11-16", "SNACK", "Apple", 100, 52)), 401, "AUTHENTICATION_REQUIRED");

        JsonNode day = expect(200, "GET", ENTRIES, ENTRIES + "?date=2026-11-16", user.token(), null).json();
        assertThat(day.size()).isEqualTo(2);
        assertThat(day.get(0).get("mealSlot").asString()).isEqualTo("LUNCH"); // sorted by meal slot order
        assertProblem(expect(400, "GET", ENTRIES, ENTRIES, user.token(), null), 400, "VALIDATION_FAILED");

        TestUser other = newUser("other");
        assertThat(expect(200, "GET", ENTRIES, ENTRIES + "?date=2026-11-16", other.token(), null).json().size()).isZero();
    }

    @Test
    @DisplayName("US-06: an entry can be corrected and removed only by its owner (403 for another user, 404 for unknown ids)")
    void correctAndDeleteEntries() {
        TestUser user = newUser("correct");
        TestUser other = newUser("intruder");
        String id = expect(201, "POST", ENTRIES, user.token(), manual("2026-11-16", "LUNCH", "Pizza", 330, 879.9))
                .json().get("entry").get("entryId").asString();
        String path = ENTRY.replace("{entryId}", id);

        JsonNode corrected = expect(200, "PATCH", ENTRY, path, user.token(), Map.of("portionG", 300, "kcal", 799.9)).json();
        assertThat(corrected.get("entry").get("kcal").asDouble()).isEqualTo(799.9);
        assertThat(corrected.get("entry").get("foodName").asString()).isEqualTo("Pizza");

        assertProblem(expect(403, "PATCH", ENTRY, path, other.token(), Map.of("kcal", 1)), 403, "ACCESS_DENIED");
        assertProblem(expect(403, "DELETE", ENTRY, path, other.token(), null), 403, "ACCESS_DENIED");
        assertProblem(expect(400, "PATCH", ENTRY, path, user.token(), Map.of()), 400, "VALIDATION_FAILED");
        assertProblem(expect(400, "PATCH", ENTRY, path, user.token(), Map.of("date", "2026-11-17")), 400, "VALIDATION_FAILED");
        String unknown = ENTRY.replace("{entryId}", UUID.randomUUID().toString());
        assertProblem(expect(404, "PATCH", ENTRY, unknown, user.token(), Map.of("kcal", 1)), 404, "RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT kcal FROM veya.food_entry WHERE id = ?::uuid", Double.class, id)).isEqualTo(799.9);

        expect(204, "DELETE", ENTRY, path, user.token(), null);
        assertProblem(expect(404, "DELETE", ENTRY, path, user.token(), null), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("US-06/US-15: an entry can be linked to a planned MEAL of the caller's active plan version on the same date")
    void linkEntryToPlannedMeal() {
        TestUser user = newUser("linked");
        TestUser other = newUser("other-plan");
        LocalDate date = LocalDate.of(2026, 11, 16);
        UUID lunch = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        UUID dinner = UUID.randomUUID();
        PlanFixtures.createPlan(jdbc, user.id(), date, List.of(
                PlanFixtures.meal(lunch, "LUNCH", "13:00", "Chicken rice bowl"),
                PlanFixtures.workout(run, "18:00", "Running, 10 km/h", 30),
                PlanFixtures.meal(dinner, "DINNER", "19:30", "Salmon with potatoes")));

        Map<String, Object> body = manual("2026-11-16", "LUNCH", "Office pizza (3 slices)", 330, 879.9);
        body.put("planItemKey", lunch.toString());
        JsonNode created = expect(201, "POST", ENTRIES, user.token(), body).json();
        assertThat(created.get("entry").get("planItemKey").asString()).isEqualTo(lunch.toString());
        assertThat(created.get("recoveryRequired").asBoolean()).isFalse();

        assertProblem(expect(403, "POST", ENTRIES, other.token(), body), 403, "ACCESS_DENIED");

        Map<String, Object> workout = new HashMap<>(body);
        workout.put("planItemKey", run.toString());
        assertThat(expect(400, "POST", ENTRIES, user.token(), workout).body()).contains("planItemKey");

        Map<String, Object> otherDate = new HashMap<>(body);
        otherDate.put("date", "2026-11-17");
        assertThat(expect(400, "POST", ENTRIES, user.token(), otherDate).body()).contains("planItemKey");

        Map<String, Object> unknown = new HashMap<>(body);
        unknown.put("planItemKey", UUID.randomUUID().toString());
        assertThat(expect(400, "POST", ENTRIES, user.token(), unknown).body()).contains("planItemKey");

        // version 2 replaces the dinner with a new item: the old dinner key is history now (immutable)
        PlanFixtures.createNextVersion(jdbc, user.id(), date, 1, "[{\"itemKey\":\"" + lunch + "\",\"itemType\":\"MEAL\","
                + "\"mealSlot\":\"LUNCH\",\"mealId\":4,\"kcal\":629.9,\"proteinG\":54.7,\"fatG\":16.4,\"carbsG\":63.0}]");
        Map<String, Object> superseded = new HashMap<>(body);
        superseded.put("mealSlot", "DINNER");
        superseded.put("planItemKey", dinner.toString());
        assertProblem(expect(409, "POST", ENTRIES, user.token(), superseded), 409, "IMMUTABLE_HISTORY");
    }

    @Test
    @DisplayName("US-08: recent foods are distinct by name, newest first, and limited")
    void recentFoods() {
        TestUser user = newUser("recent");
        expect(201, "POST", ENTRIES, user.token(), manual("2026-11-15", "BREAKFAST", "Eggs", 120, 171.6));
        expect(201, "POST", ENTRIES, user.token(), manual("2026-11-16", "LUNCH", "Pizza", 330, 879.9));
        expect(201, "POST", ENTRIES, user.token(), manual("2026-11-17", "BREAKFAST", "eggs", 100, 143));

        JsonNode recent = expect(200, "GET", "/api/v1/food-entries/recent", user.token(), null).json();
        assertThat(recent.size()).isEqualTo(2);
        assertThat(recent.get(0).get("foodName").asString()).isEqualTo("eggs");
        assertThat(recent.get(1).get("foodName").asString()).isEqualTo("Pizza");
        assertThat(expect(200, "GET", "/api/v1/food-entries/recent?limit=1", user.token(), null).json().size()).isEqualTo(1);
        assertProblem(expect(400, "GET", "/api/v1/food-entries/recent?limit=21", user.token(), null), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("US-08: a saved meal is reused as new diary entries without altering the template; names are unique per user")
    void savedMeals() {
        TestUser user = newUser("saved");
        TestUser other = newUser("saved-other");
        Long eggs = jdbc.queryForObject("SELECT id FROM veya.food_item WHERE name = 'Eggs'", Long.class);
        Map<String, Object> template = Map.of("name", "My usual breakfast", "items", List.of(
                Map.of("foodId", eggs, "foodName", "Eggs", "portionG", 120, "kcal", 171.6, "proteinG", 15.1, "fatG", 11.4, "carbsG", 0.8),
                Map.of("foodName", "Whole wheat bread", "portionG", 60, "kcal", 148.2, "proteinG", 7.8, "fatG", 2.0, "carbsG", 24.6)));

        Response created = expect(201, "POST", "/api/v1/saved-meals", user.token(), template);
        JsonNode meal = created.json();
        assertThat(meal.get("totals").get("kcal").asDouble()).isEqualTo(319.8);
        assertThat(meal.get("items").get(1).get("lineNo").asInt()).isEqualTo(2);
        String id = meal.get("savedMealId").asString();

        assertProblem(expect(409, "POST", "/api/v1/saved-meals", user.token(), template), 409, "SAVED_MEAL_NAME_TAKEN");
        expect(201, "POST", "/api/v1/saved-meals", other.token(), template); // same name for another user is fine
        assertProblem(expect(400, "POST", "/api/v1/saved-meals", user.token(), Map.of("name", "Empty", "items", List.of())),
                400, "VALIDATION_FAILED");
        Map<String, Object> unknownFood = Map.of("name", "Unknown", "items", List.of(
                Map.of("foodId", 999999, "foodName", "X", "portionG", 1, "kcal", 1, "proteinG", 0, "fatG", 0, "carbsG", 0)));
        assertThat(expect(400, "POST", "/api/v1/saved-meals", user.token(), unknownFood).body()).contains("items[0].foodId");

        JsonNode list = expect(200, "GET", "/api/v1/saved-meals", user.token(), null).json();
        assertThat(list.size()).isEqualTo(1);

        String log = "/api/v1/saved-meals/{savedMealId}/entries";
        Map<String, Object> request = Map.of("date", "2026-11-17", "mealSlot", "BREAKFAST");
        JsonNode logged = expect(201, "POST", log, log.replace("{savedMealId}", id), user.token(), request).json();
        assertThat(logged.size()).isEqualTo(2);
        assertThat(logged.get(0).get("inputSource").asString()).isEqualTo("SAVED_MEAL");
        assertThat(logged.get(0).get("savedMealId").asString()).isEqualTo(id);
        expect(201, "POST", log, log.replace("{savedMealId}", id), user.token(), request);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.saved_meal_item WHERE saved_meal_id = ?::uuid", Integer.class, id))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.food_entry WHERE saved_meal_id = ?::uuid", Integer.class, id))
                .isEqualTo(4);

        assertProblem(expect(403, "POST", log, log.replace("{savedMealId}", id), other.token(), request), 403, "ACCESS_DENIED");
        assertProblem(expect(404, "POST", log, log.replace("{savedMealId}", UUID.randomUUID().toString()), user.token(), request),
                404, "RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("US-07: the summary matches persisted confirmed entries and the remaining targets; no profile -> null targets")
    void nutritionSummary() {
        TestUser user = newUser("summary");
        String path = "/api/v1/days/{date}/nutrition";
        JsonNode empty = expect(200, "GET", path, path.replace("{date}", "2026-11-16"), user.token(), null).json();
        assertThat(empty.get("consumed").get("kcal").asDouble()).isZero();
        assertThat(empty.get("target").get("kcal").isNull()).isTrue();
        assertThat(empty.get("remaining").get("kcal").isNull()).isTrue();
        assertThat(empty.get("entryCount").asInt()).isZero();

        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of()));
        expect(201, "POST", ENTRIES, user.token(), manual("2026-11-16", "LUNCH", "Pizza", 330, 879.9));
        expect(201, "POST", ENTRIES, user.token(), manual("2026-11-16", "DINNER", "Soup", 300, 426.4));
        expect(201, "POST", ENTRIES, user.token(), manual("2026-11-17", "DINNER", "Other day", 300, 500));

        JsonNode summary = expect(200, "GET", path, path.replace("{date}", "2026-11-16"), user.token(), null).json();
        assertThat(summary.get("consumed").get("kcal").asDouble()).isEqualTo(1306.3);
        assertThat(summary.get("target").get("kcal").asDouble()).isEqualTo(1900);
        assertThat(summary.get("remaining").get("kcal").asDouble()).isEqualTo(593.7);
        assertThat(summary.get("consumed").get("proteinG").asDouble()).isEqualTo(64.0);
        assertThat(summary.get("entryCount").asInt()).isEqualTo(2);

        TestUser other = newUser("summary-other");
        assertThat(expect(200, "GET", path, path.replace("{date}", "2026-11-16"), other.token(), null).json()
                .get("entryCount").asInt()).isZero();
        assertProblem(expect(400, "GET", path, path.replace("{date}", "16-11-2026"), user.token(), null), 400, "VALIDATION_FAILED");
    }
}
