package com.veya.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Planning API over real HTTP: US-09, US-10, US-12, US-13, US-14, US-15, UC-02. */
class PlanningApiTests extends ApiTestSupport {

    static final String PLANS = "/api/v1/plans";
    static final String PLAN = "/api/v1/plans/{planId}";
    static final String REPLACE = "/api/v1/plans/{planId}/items/{itemKey}/replacement";
    static final String STATUS = "/api/v1/plans/{planId}/items/{itemKey}/status";

    long mealId(String name) {
        return jdbc.queryForObject("SELECT id FROM veya.meal WHERE name = ?", Long.class, name);
    }

    long exerciseId(String name) {
        return jdbc.queryForObject("SELECT id FROM veya.exercise WHERE name = ?", Long.class, name);
    }

    Map<String, Object> meal(String slot, String time, String name) {
        return Map.of("itemType", "MEAL", "mealSlot", slot, "plannedTime", time, "mealId", mealId(name));
    }

    Map<String, Object> workout(String time, String name, int minutes) {
        return Map.of("itemType", "WORKOUT", "plannedTime", time, "exerciseId", exerciseId(name), "durationMin", minutes);
    }

    /** The UC-01 day of persona Anna: breakfast, lunch, run, dinner. */
    JsonNode createAnnaPlan(TestUser user, String date) {
        Map<String, Object> body = Map.of("date", date, "items", List.of(
                meal("BREAKFAST", "08:00", "Oatmeal with banana"), meal("LUNCH", "13:00", "Chicken rice bowl"),
                meal("DINNER", "19:30", "Salmon with potatoes"), workout("18:00", "Running, 10 km/h", 30)));
        return expect(201, "POST", PLANS, user.token(), body).json();
    }

    static String itemKey(JsonNode plan, String slotOrType) {
        for (JsonNode item : plan.get("items")) {
            if (slotOrType.equals(item.get("mealSlot").asString(null)) || slotOrType.equals(item.get("itemType").asString())) {
                return item.get("itemKey").asString();
            }
        }
        throw new AssertionError("no item " + slotOrType);
    }

    static String path(String template, String planId, String itemKey) {
        return template.replace("{planId}", planId).replace("{itemKey}", itemKey == null ? "" : itemKey);
    }

    @Test
    @DisplayName("US-09: a suggested menu excludes prohibited items, fits the target range and stores nothing")
    void suggestMealPlan() {
        TestUser user = newUser("suggest");
        Map<String, Object> request = Map.of("date", "2026-11-17", "mealSlots", List.of("BREAKFAST", "LUNCH", "DINNER"));
        assertProblem(expect(422, "POST", "/api/v1/meal-plan-suggestions", user.token(), request), 422, "NO_FEASIBLE_MEAL_PLAN");

        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of("FISH", "PEANUT")));
        JsonNode suggestion = expect(200, "POST", "/api/v1/meal-plan-suggestions", user.token(), request).json();
        assertThat(suggestion.get("items").size()).isEqualTo(3);
        double total = suggestion.get("totals").get("kcal").asDouble();
        assertThat(total).isBetween(1900 * 0.8, 1900 * 1.2);
        assertThat(suggestion.toString()).doesNotContain("Salmon", "Peanut");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.daily_plan WHERE user_id = ?", Integer.class, user.id())).isZero();
        // deterministic for the same data
        assertThat(expect(200, "POST", "/api/v1/meal-plan-suggestions", user.token(), request).json()).isEqualTo(suggestion);

        Map<String, Object> noTime = Map.of("availableTimeMin", 2, "availableEquipment", List.of(), "availableIngredients",
                List.of(), "excludedIngredients", List.of());
        expect(200, "PUT", "/api/v1/constraints/{date}", "/api/v1/constraints/2026-11-17", user.token(), noTime);
        JsonNode blocked = expect(422, "POST", "/api/v1/meal-plan-suggestions", user.token(), request).json();
        assertThat(blocked.get("blockingConstraints").get(0).get("type").asString()).isEqualTo("TIME");

        assertProblem(expect(400, "POST", "/api/v1/meal-plan-suggestions", user.token(),
                Map.of("date", "2026-11-17", "mealSlots", List.of("BRUNCH"))), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("US-09/US-12: a saved plan becomes version 1 with a catalogue snapshot; one plan per date; validation and hard constraints")
    void createAndReadPlan() {
        TestUser user = newUser("plan");
        TestUser other = newUser("plan-other");
        JsonNode plan = createAnnaPlan(user, "2026-11-17");
        assertThat(plan.get("activeVersion").asInt()).isEqualTo(1);
        assertThat(plan.get("changeReason").asString()).isEqualTo("INITIAL_PLAN");
        assertThat(plan.get("recoveryRequired").asBoolean()).isFalse();
        assertThat(plan.get("items").size()).isEqualTo(4);
        assertThat(plan.get("items").get(0).get("plannedTime").asString()).isEqualTo("08:00");
        assertThat(plan.get("items").get(2).get("itemType").asString()).isEqualTo("WORKOUT"); // sorted by time
        assertThat(plan.get("items").get(1).get("plannedKcal").asDouble()).isEqualTo(
                jdbc.queryForObject("SELECT kcal FROM veya.v_meal_nutrition WHERE meal_name = 'Chicken rice bowl'", Double.class));
        String planId = plan.get("planId").asString();

        assertProblem(expect(409, "POST", PLANS, user.token(), Map.of("date", "2026-11-17", "items",
                List.of(meal("LUNCH", "13:00", "Lentil soup")))), 409, "PLAN_ALREADY_EXISTS");
        assertThat(expect(400, "POST", PLANS, user.token(), Map.of("date", "2026-11-18", "items",
                List.of(workout("18:00", "Running, 10 km/h", 0)))).body()).contains("durationMin");
        assertThat(expect(400, "POST", PLANS, user.token(), Map.of("date", "2026-11-18", "items",
                List.of(Map.of("itemType", "WORKOUT", "exerciseId", exerciseId("Running, 10 km/h"))))).body()).contains("items[0]");
        assertThat(expect(400, "POST", PLANS, user.token(), Map.of("date", "2026-11-18", "items",
                List.of(Map.of("itemType", "MEAL", "mealSlot", "LUNCH", "mealId", 99999)))).body()).contains("items[0].mealId");
        assertThat(expect(400, "POST", PLANS, user.token(), Map.of("date", "2026-11-18", "items", List.of())).body()).contains("items");

        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of("PEANUT")));
        JsonNode violation = expect(422, "POST", PLANS, user.token(), Map.of("date", "2026-11-18", "items",
                List.of(meal("SNACK", "16:00", "Peanut butter toast")))).json();
        assertThat(violation.get("code").asString()).isEqualTo("HARD_CONSTRAINT_VIOLATION");
        assertThat(violation.get("blockingConstraints").get(0).get("type").asString()).isEqualTo("FOOD_EXCLUSION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.daily_plan WHERE user_id = ? AND plan_date = '2026-11-18'",
                Integer.class, user.id())).isZero();

        assertThat(expect(200, "GET", PLAN, path(PLAN, planId, null), user.token(), null).json()).isEqualTo(plan);
        assertThat(expect(200, "GET", PLANS, PLANS + "?date=2026-11-17", user.token(), null).json().get("planId").asString())
                .isEqualTo(planId);
        assertProblem(expect(404, "GET", PLANS, PLANS + "?date=2026-11-20", user.token(), null), 404, "PLAN_NOT_FOUND");
        assertProblem(expect(403, "GET", PLAN, path(PLAN, planId, null), other.token(), null), 403, "ACCESS_DENIED");
        assertProblem(expect(404, "GET", PLAN, path(PLAN, UUID.randomUUID().toString(), null), user.token(), null),
                404, "RESOURCE_NOT_FOUND");
        assertProblem(expect(401, "GET", PLAN, path(PLAN, planId, null), null, null), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("US-10/US-14: options are hard-constraint compliant with reasons; nothing is saved; none -> 422")
    void replacementOptions() {
        TestUser user = newUser("options");
        TestUser other = newUser("options-other");
        JsonNode plan = createAnnaPlan(user, "2026-11-17");
        String planId = plan.get("planId").asString();
        String dinner = itemKey(plan, "DINNER");
        expect(200, "PUT", "/api/v1/constraints/{date}", "/api/v1/constraints/2026-11-17", user.token(), Map.of(
                "availableTimeMin", 40, "availableEquipment", List.of(), "availableIngredients", List.of(),
                "excludedIngredients", List.of("FISH")));

        JsonNode options = expect(200, "POST", "/api/v1/meal-options", user.token(), Map.of("planId", planId, "itemKey", dinner)).json();
        assertThat(options.get("baseVersion").asInt()).isEqualTo(1);
        assertThat(options.get("options").size()).isPositive();
        for (JsonNode option : options.get("options")) {
            assertThat(option.get("name").asString()).isNotEqualTo("Salmon with potatoes");
            assertThat(option.get("prepTimeMin").asInt()).isLessThanOrEqualTo(40);
            assertThat(option.get("reasons").size()).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT current_version_no FROM veya.daily_plan WHERE id = ?::uuid", Integer.class, planId))
                .isEqualTo(1);

        JsonNode workouts = expect(200, "POST", "/api/v1/exercise-options", user.token(),
                Map.of("planId", planId, "itemKey", itemKey(plan, "WORKOUT"))).json();
        assertThat(workouts.get("options").get(0).get("durationMin").asInt()).isLessThanOrEqualTo(30);
        assertThat(workouts.toString()).doesNotContain("DUMBBELLS");

        assertThat(expect(400, "POST", "/api/v1/meal-options", user.token(),
                Map.of("planId", planId, "itemKey", itemKey(plan, "WORKOUT"))).body()).contains("itemKey");
        assertProblem(expect(403, "POST", "/api/v1/meal-options", other.token(), Map.of("planId", planId, "itemKey", dinner)),
                403, "ACCESS_DENIED");
        assertProblem(expect(404, "POST", "/api/v1/meal-options", user.token(),
                Map.of("planId", planId, "itemKey", UUID.randomUUID().toString())), 404, "RESOURCE_NOT_FOUND");

        expect(200, "PUT", "/api/v1/constraints/{date}", "/api/v1/constraints/2026-11-17", user.token(), Map.of(
                "availableTimeMin", 2, "availableEquipment", List.of(), "availableIngredients", List.of(),
                "excludedIngredients", List.of()));
        JsonNode none = expect(422, "POST", "/api/v1/meal-options", user.token(), Map.of("planId", planId, "itemKey", dinner)).json();
        assertThat(none.get("code").asString()).isEqualTo("NO_FEASIBLE_ALTERNATIVE");
        assertThat(none.get("blockingConstraints").get(0).get("itemKey").asString()).isEqualTo(dinner);

        String breakfast = itemKey(plan, "BREAKFAST");
        expect(200, "POST", STATUS, path(STATUS, planId, breakfast), user.token(), Map.of("status", "COMPLETED"));
        assertProblem(expect(409, "POST", "/api/v1/meal-options", user.token(), Map.of("planId", planId, "itemKey", breakfast)),
                409, "ITEM_FROZEN");
    }

    @Test
    @DisplayName("US-10/UC-02: a confirmed replacement creates version 2, keeps history; stale version -> 409, nothing changes")
    void replaceItem() {
        TestUser user = newUser("replace");
        JsonNode plan = createAnnaPlan(user, "2026-11-17");
        String planId = plan.get("planId").asString();
        String dinner = itemKey(plan, "DINNER");

        JsonNode v2 = expect(200, "POST", REPLACE, path(REPLACE, planId, dinner), user.token(),
                Map.of("baseVersion", 1, "mealId", mealId("Tofu vegetable stir-fry"))).json();
        assertThat(v2.get("activeVersion").asInt()).isEqualTo(2);
        assertThat(v2.get("changeReason").asString()).isEqualTo("MEAL_REPLACEMENT");
        assertThat(v2.toString()).contains("Tofu vegetable stir-fry").doesNotContain("Salmon with potatoes");
        assertThat(itemKey(v2, "DINNER")).isEqualTo(dinner); // the logical item keeps its key
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.plan_version WHERE daily_plan_id = ?::uuid", Integer.class, planId))
                .isEqualTo(2);

        JsonNode stale = expect(409, "POST", REPLACE, path(REPLACE, planId, dinner), user.token(),
                Map.of("baseVersion", 1, "mealId", mealId("Lentil soup"))).json();
        assertThat(stale.get("code").asString()).isEqualTo("STALE_PLAN_VERSION");
        assertThat(stale.get("currentVersion").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.plan_version WHERE daily_plan_id = ?::uuid", Integer.class, planId))
                .isEqualTo(2);

        JsonNode workout = expect(200, "POST", REPLACE, path(REPLACE, planId, itemKey(v2, "WORKOUT")), user.token(),
                Map.of("baseVersion", 2, "exerciseId", exerciseId("Bodyweight circuit"), "durationMin", 20)).json();
        assertThat(workout.get("changeReason").asString()).isEqualTo("EXERCISE_REPLACEMENT");

        assertThat(expect(400, "POST", REPLACE, path(REPLACE, planId, dinner), user.token(),
                Map.of("baseVersion", 3)).body()).contains("mealId");
        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of("PEANUT")));
        assertProblem(expect(422, "POST", REPLACE, path(REPLACE, planId, itemKey(v2, "LUNCH")), user.token(),
                Map.of("baseVersion", 3, "mealId", mealId("Peanut butter toast"))), 422, "HARD_CONSTRAINT_VIOLATION");
    }

    @Test
    @DisplayName("US-15: confirming a meal 250 kcal over the plan sets recoveryRequired; a 100 kcal difference does not; status only once")
    void recordItemStatus() {
        TestUser user = newUser("status");
        JsonNode plan = createAnnaPlan(user, "2026-11-16");
        String planId = plan.get("planId").asString();
        String breakfast = itemKey(plan, "BREAKFAST");
        String lunch = itemKey(plan, "LUNCH");
        double lunchKcal = plan.get("items").get(1).get("plannedKcal").asDouble();
        double breakfastKcal = plan.get("items").get(0).get("plannedKcal").asDouble();

        Map<String, Object> small = NutritionApiTests.manual("2026-11-16", "BREAKFAST", "Oatmeal", 300,
                Math.round((breakfastKcal + 100) * 10) / 10.0);
        small.put("planItemKey", breakfast);
        expect(201, "POST", "/api/v1/food-entries", user.token(), small);
        JsonNode ok = expect(200, "POST", STATUS, path(STATUS, planId, breakfast), user.token(), Map.of("status", "COMPLETED")).json();
        assertThat(ok.get("recoveryRequired").asBoolean()).isFalse();
        assertThat(ok.get("newDeviations").size()).isZero();
        assertThat(ok.get("item").get("status").asString()).isEqualTo("COMPLETED");

        Map<String, Object> big = NutritionApiTests.manual("2026-11-16", "LUNCH", "Office pizza", 330,
                Math.round((lunchKcal + 250) * 10) / 10.0);
        big.put("planItemKey", lunch);
        expect(201, "POST", "/api/v1/food-entries", user.token(), big);
        JsonNode deviation = expect(200, "POST", STATUS, path(STATUS, planId, lunch), user.token(), Map.of("status", "COMPLETED")).json();
        assertThat(deviation.get("recoveryRequired").asBoolean()).isTrue();
        assertThat(deviation.get("newDeviations").get(0).get("type").asString()).isEqualTo("KCAL_DIFFERENCE");
        assertThat(deviation.get("newDeviations").get(0).get("kcalDifference").asDouble()).isEqualTo(250.0);

        assertProblem(expect(409, "POST", STATUS, path(STATUS, planId, lunch), user.token(), Map.of("status", "SKIPPED")),
                409, "STATUS_ALREADY_RECORDED");
        String run = itemKey(plan, "WORKOUT");
        assertThat(expect(400, "POST", STATUS, path(STATUS, planId, run), user.token(), Map.of("status", "COMPLETED")).body())
                .contains("activity-entries");
        JsonNode missed = expect(200, "POST", STATUS, path(STATUS, planId, run), user.token(), Map.of("status", "MISSED")).json();
        assertThat(missed.get("newDeviations").get(0).get("type").asString()).isEqualTo("ITEM_MISSED");
        assertProblem(expect(400, "POST", STATUS, path(STATUS, planId, lunch), user.token(), Map.of("status", "DONE")),
                400, "VALIDATION_FAILED");

        JsonNode open = expect(200, "GET", "/api/v1/plans/{planId}/deviations", "/api/v1/plans/" + planId + "/deviations",
                user.token(), null).json();
        assertThat(open.size()).isEqualTo(2);
        assertProblem(expect(403, "GET", "/api/v1/plans/{planId}/deviations", "/api/v1/plans/" + planId + "/deviations",
                newUser("dev-other").token(), null), 403, "ACCESS_DENIED");
    }

    @Test
    @DisplayName("US-20/Q10: the day summary shows the active version, planned vs actual nutrition and activity; no plan -> plan null")
    void daySummary() {
        TestUser user = newUser("summary");
        String path = "/api/v1/days/{date}/summary";
        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of()));
        expect(201, "POST", "/api/v1/food-entries", user.token(), NutritionApiTests.manual("2026-11-19", "LUNCH", "Soup", 300, 400));
        JsonNode empty = expect(200, "GET", path, path.replace("{date}", "2026-11-19"), user.token(), null).json();
        assertThat(empty.get("plan").isNull()).isTrue();
        assertThat(empty.get("nutrition").get("consumedKcal").asDouble()).isEqualTo(400);
        assertThat(empty.get("nutrition").get("remainingKcal").asDouble()).isEqualTo(1500);

        JsonNode plan = createAnnaPlan(user, "2026-11-16");
        Map<String, Object> run = new HashMap<>();
        run.put("date", "2026-11-16");
        run.put("exerciseId", exerciseId("Running, 10 km/h"));
        run.put("planItemKey", itemKey(plan, "WORKOUT"));
        run.put("status", "COMPLETED");
        run.put("durationMin", 25);
        expect(201, "POST", "/api/v1/activity-entries", user.token(), run);
        JsonNode summary = expect(200, "GET", path, path.replace("{date}", "2026-11-16"), user.token(), null).json();
        assertThat(summary.get("plan").get("activeVersion").asInt()).isEqualTo(1);
        assertThat(summary.get("activity").get("plannedWorkoutMin").asInt()).isEqualTo(30);
        assertThat(summary.get("activity").get("completedWorkoutMin").asInt()).isEqualTo(25);
        assertThat(summary.get("openItems").asInt()).isEqualTo(3);
        assertThat(summary.get("nutrition").get("targetKcal").asDouble()).isEqualTo(1900);
        assertProblem(expect(400, "GET", path, path.replace("{date}", "2026-13-45"), user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(401, "GET", path, path.replace("{date}", "2026-11-16"), null, null), 401, "AUTHENTICATION_REQUIRED");
    }
}
