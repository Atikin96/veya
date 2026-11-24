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

/** Profile, goal, permanent exclusions and daily constraints over real HTTP (US-02, US-03, US-15 rule 3, Q3, Q5). */
class ProfileApiTests extends ApiTestSupport {

    private static final String PROFILE = "/api/v1/me/profile";
    private static final String CONSTRAINTS = "/api/v1/constraints/{date}";

    static Map<String, Object> profile(int energy, List<String> exclusions) {
        return Map.of("goal", "LOSE_WEIGHT", "energyTargetKcal", energy, "proteinTargetG", 110, "fatTargetG", 60.5,
                "carbsTargetG", 220, "foodExclusions", exclusions);
    }

    @Test
    @DisplayName("US-02: a saved profile appears on subsequent requests; before that GET returns 404 PROFILE_NOT_FOUND")
    void saveAndReadProfile() {
        TestUser user = newUser("profile");
        assertProblem(expect(404, "GET", PROFILE, user.token(), null), 404, "PROFILE_NOT_FOUND");

        Response saved = expect(200, "PUT", PROFILE, user.token(), profile(1900, List.of("PEANUT", "FISH")));
        assertThat(saved.json().get("fatTargetG").asDouble()).isEqualTo(60.5);
        assertThat(saved.json().get("foodExclusions").toString()).isEqualTo("[\"FISH\",\"PEANUT\"]");

        Response read = expect(200, "GET", PROFILE, user.token(), null);
        assertThat(read.json().get("energyTargetKcal").asInt()).isEqualTo(1900);
        assertThat(expect(200, "GET", "/api/v1/me", user.token(), null).json().get("hasProfile").asBoolean()).isTrue();

        expect(200, "PUT", PROFILE, user.token(), profile(2100, List.of()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.profile_food_exclusion WHERE user_id = ?", Integer.class,
                user.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT energy_target_kcal FROM veya.user_profile WHERE user_id = ?", Integer.class,
                user.id())).isEqualTo(2100);
    }

    @Test
    @DisplayName("US-02: invalid ranges, unknown codes and extra decimals return field errors and do not change stored data")
    void invalidProfileDoesNotChangeData() {
        TestUser user = newUser("invalid");
        expect(200, "PUT", PROFILE, user.token(), profile(1900, List.of("PEANUT")));

        Response range = expect(400, "PUT", PROFILE, user.token(), profile(100, List.of()));
        assertProblem(range, 400, "VALIDATION_FAILED");
        JsonNode error = range.json().get("errors").get(0);
        assertThat(error.get("field").asString()).isEqualTo("energyTargetKcal");
        assertThat(error.get("rejectedValue").asInt()).isEqualTo(100);

        Map<String, Object> unknownCode = new HashMap<>(profile(1900, List.of("PEANUT", "UNOBTAINIUM")));
        assertThat(expect(400, "PUT", PROFILE, user.token(), unknownCode).body()).contains("foodExclusions[1]");

        Map<String, Object> unknownGoal = new HashMap<>(profile(1900, List.of()));
        unknownGoal.put("goal", "BECOME_FAMOUS");
        assertThat(expect(400, "PUT", PROFILE, user.token(), unknownGoal).body()).contains("\"goal\"");

        Map<String, Object> decimals = new HashMap<>(profile(1900, List.of()));
        decimals.put("proteinTargetG", 110.25);
        assertThat(expect(400, "PUT", PROFILE, user.token(), decimals).body()).contains("proteinTargetG");

        Map<String, Object> fractionalKcal = new HashMap<>(profile(1900, List.of()));
        fractionalKcal.put("energyTargetKcal", 1900.5);
        assertThat(expect(400, "PUT", PROFILE, user.token(), fractionalKcal).body()).contains("energyTargetKcal");

        JsonNode stored = expect(200, "GET", PROFILE, user.token(), null).json();
        assertThat(stored.get("energyTargetKcal").asInt()).isEqualTo(1900);
        assertThat(stored.get("foodExclusions").toString()).isEqualTo("[\"PEANUT\"]");
        assertProblem(expect(401, "PUT", PROFILE, null, profile(1900, List.of())), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("US-03: daily constraints are stored per user and day; an unsaved day has empty lists")
    void dailyConstraints() {
        TestUser anna = newUser("anna");
        TestUser max = newUser("max");
        String date = "2026-11-16";
        String path = CONSTRAINTS.replace("{date}", date);

        JsonNode empty = expect(200, "GET", CONSTRAINTS, path, anna.token(), null).json();
        assertThat(empty.get("availableTimeMin").isNull()).isTrue();
        assertThat(empty.get("excludedIngredients").size()).isZero();

        Map<String, Object> body = Map.of("availableTimeMin", 40, "availableEquipment", List.of("YOGA_MAT"),
                "availableIngredients", List.of("RICE"), "excludedIngredients", List.of("FISH"));
        JsonNode saved = expect(200, "PUT", CONSTRAINTS, path, anna.token(), body).json();
        assertThat(saved.get("constraints").get("date").asString()).isEqualTo(date);
        assertThat(saved.get("planImpact").isNull()).isTrue();

        JsonNode read = expect(200, "GET", CONSTRAINTS, path, anna.token(), null).json();
        assertThat(read.get("availableTimeMin").asInt()).isEqualTo(40);
        assertThat(read.get("availableIngredients").toString()).isEqualTo("[\"RICE\"]");
        assertThat(read.get("excludedIngredients").toString()).isEqualTo("[\"FISH\"]");

        // another user sees only their own (empty) constraints for the same date
        assertThat(expect(200, "GET", CONSTRAINTS, path, max.token(), null).json().get("excludedIngredients").size()).isZero();

        // PUT replaces the day's values
        Map<String, Object> replaced = new HashMap<>();
        replaced.put("availableTimeMin", null);
        replaced.put("availableEquipment", List.of());
        replaced.put("availableIngredients", List.of());
        replaced.put("excludedIngredients", List.of());
        expect(200, "PUT", CONSTRAINTS, path, anna.token(), replaced);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.daily_constraint_ingredient i JOIN veya.daily_constraint c "
                + "ON c.id = i.daily_constraint_id WHERE c.user_id = ?", Integer.class, anna.id())).isZero();
    }

    @Test
    @DisplayName("US-03: invalid constraints (negative time, unknown codes, overlap, bad date) are rejected")
    void invalidConstraints() {
        TestUser user = newUser("constraints");
        String path = CONSTRAINTS.replace("{date}", "2026-11-16");
        Map<String, Object> negative = Map.of("availableTimeMin", -5, "availableEquipment", List.of(),
                "availableIngredients", List.of(), "excludedIngredients", List.of());
        assertThat(expect(400, "PUT", CONSTRAINTS, path, user.token(), negative).body()).contains("availableTimeMin");

        Map<String, Object> overlap = Map.of("availableTimeMin", 30, "availableEquipment", List.of("TRAMPOLINE"),
                "availableIngredients", List.of("FISH"), "excludedIngredients", List.of("FISH"));
        String errors = expect(400, "PUT", CONSTRAINTS, path, user.token(), overlap).body();
        assertThat(errors).contains("availableEquipment[0]", "excludedIngredients");

        assertProblem(expect(400, "GET", CONSTRAINTS, CONSTRAINTS.replace("{date}", "2026-13-45"), user.token(), null),
                400, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.daily_constraint WHERE user_id = ?", Integer.class,
                user.id())).isZero();
    }

    @Test
    @DisplayName("US-15 rule 3: constraints that make a remaining planned item infeasible record a deviation and set recoveryRequired")
    void constraintsMakeAPlannedItemInfeasible() {
        TestUser user = newUser("infeasible");
        LocalDate date = LocalDate.of(2026, 11, 20);
        UUID dinnerKey = UUID.randomUUID();
        UUID breakfastKey = UUID.randomUUID();
        UUID workoutKey = UUID.randomUUID();
        PlanFixtures.createPlan(jdbc, user.id(), date, List.of(
                PlanFixtures.meal(breakfastKey, "BREAKFAST", "08:00", "Oatmeal with banana"),
                PlanFixtures.meal(dinnerKey, "DINNER", "19:30", "Salmon with potatoes"),
                PlanFixtures.workout(workoutKey, "18:00", "Dumbbell strength training", 45)));

        Map<String, Object> body = Map.of("availableTimeMin", 120, "availableEquipment", List.of("DUMBBELLS"),
                "availableIngredients", List.of(), "excludedIngredients", List.of("FISH"));
        JsonNode result = expect(200, "PUT", CONSTRAINTS, CONSTRAINTS.replace("{date}", date.toString()), user.token(), body).json();

        JsonNode impact = result.get("planImpact");
        assertThat(impact.get("recoveryRequired").asBoolean()).isTrue();
        assertThat(impact.get("infeasibleItems").size()).isEqualTo(1);
        assertThat(impact.get("infeasibleItems").get(0).get("itemKey").asString()).isEqualTo(dinnerKey.toString());
        assertThat(impact.get("infeasibleItems").get(0).get("reason").asString()).contains("FISH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.deviation_event d JOIN veya.daily_plan p ON p.id = d.daily_plan_id "
                + "WHERE p.user_id = ? AND d.deviation_type = 'CONSTRAINT_INFEASIBLE'", Integer.class, user.id())).isEqualTo(1);

        // no equipment and 30 minutes: the 45-minute dumbbell workout becomes infeasible as well; saving again is idempotent
        Map<String, Object> stricter = Map.of("availableTimeMin", 30, "availableEquipment", List.of(),
                "availableIngredients", List.of(), "excludedIngredients", List.of("FISH"));
        JsonNode second = expect(200, "PUT", CONSTRAINTS, CONSTRAINTS.replace("{date}", date.toString()), user.token(), stricter).json();
        assertThat(second.get("planImpact").get("infeasibleItems").size()).isEqualTo(2);
        expect(200, "PUT", CONSTRAINTS, CONSTRAINTS.replace("{date}", date.toString()), user.token(), stricter);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.deviation_event d JOIN veya.daily_plan p ON p.id = d.daily_plan_id "
                + "WHERE p.user_id = ? AND d.deviation_type = 'CONSTRAINT_INFEASIBLE'", Integer.class, user.id())).isEqualTo(2);
        // the plan itself is unchanged
        assertThat(jdbc.queryForObject("SELECT current_version_no FROM veya.daily_plan WHERE user_id = ?", Integer.class,
                user.id())).isEqualTo(1);
    }
}
