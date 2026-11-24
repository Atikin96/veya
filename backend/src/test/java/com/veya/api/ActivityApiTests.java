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

/** Activity API over real HTTP: US-12 exercise selection, US-13 workout records, US-15 rule 2 (skipped workout). */
class ActivityApiTests extends ApiTestSupport {

    private static final String ENTRIES = "/api/v1/activity-entries";

    private Map<String, Object> record(String date, String exercise, UUID planItemKey, String status, Integer duration) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("exerciseId", jdbc.queryForObject("SELECT id FROM veya.exercise WHERE name = ?", Long.class, exercise));
        body.put("planItemKey", planItemKey == null ? null : planItemKey.toString());
        body.put("status", status);
        body.put("durationMin", duration);
        return body;
    }

    @Test
    @DisplayName("US-12: exercises list the required equipment; filtering by name and limit are validated")
    void listExercises() {
        TestUser user = newUser("exercises");
        JsonNode all = expect(200, "GET", "/api/v1/exercises", user.token(), null).json();
        assertThat(all.size()).isPositive();
        JsonNode dumbbell = expect(200, "GET", "/api/v1/exercises?q=dumbbell", user.token(), null).json();
        assertThat(dumbbell.get(0).get("requiredEquipment").toString()).contains("DUMBBELLS");
        assertProblem(expect(400, "GET", "/api/v1/exercises?limit=0", user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(400, "GET", "/api/v1/exercises?q=d", user.token(), null), 400, "VALIDATION_FAILED");
        assertProblem(expect(401, "GET", "/api/v1/exercises", null, null), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("US-13: an unplanned workout is recorded; duration must match the status; unknown exercises are rejected")
    void recordUnplannedWorkout() {
        TestUser user = newUser("workout");
        Response created = expect(201, "POST", ENTRIES, user.token(), record("2026-11-16", "Bodyweight circuit", null, "COMPLETED", 20));
        assertThat(created.json().get("entry").get("durationMin").asInt()).isEqualTo(20);
        assertThat(created.json().get("recoveryRequired").isNull()).isTrue();
        assertThat(created.header("Location")).startsWith("/api/v1/activity-entries/");

        assertThat(expect(400, "POST", ENTRIES, user.token(), record("2026-11-16", "Bodyweight circuit", null, "COMPLETED", null))
                .body()).contains("\"durationMin\"");
        assertProblem(expect(400, "POST", ENTRIES, user.token(), record("2026-11-16", "Bodyweight circuit", null, "SKIPPED", 20)),
                400, "VALIDATION_FAILED");
        assertProblem(expect(400, "POST", ENTRIES, user.token(), record("2026-11-16", "Bodyweight circuit", null, "COMPLETED", 601)),
                400, "VALIDATION_FAILED");
        assertProblem(expect(400, "POST", ENTRIES, user.token(), record("2026-11-16", "Bodyweight circuit", null, "MISSED", null)),
                400, "VALIDATION_FAILED");
        Map<String, Object> unknown = record("2026-11-16", "Bodyweight circuit", null, "COMPLETED", 20);
        unknown.put("exerciseId", 999999);
        assertThat(expect(400, "POST", ENTRIES, user.token(), unknown).body()).contains("exerciseId");
        assertProblem(expect(401, "POST", ENTRIES, null, record("2026-11-16", "Bodyweight circuit", null, "COMPLETED", 20)),
                401, "AUTHENTICATION_REQUIRED");

        JsonNode day = expect(200, "GET", ENTRIES, ENTRIES + "?date=2026-11-16", user.token(), null).json();
        assertThat(day.size()).isEqualTo(1);
        assertThat(day.get(0).get("exerciseName").asString()).isEqualTo("Bodyweight circuit");
        TestUser other = newUser("workout-other");
        assertThat(expect(200, "GET", ENTRIES, ENTRIES + "?date=2026-11-16", other.token(), null).json().size()).isZero();
    }

    @Test
    @DisplayName("US-13/US-15: skipping the planned workout persists the event, sets the item status and recoveryRequired; only once")
    void skipPlannedWorkout() {
        TestUser user = newUser("skip");
        TestUser other = newUser("skip-other");
        LocalDate date = LocalDate.of(2026, 11, 16);
        UUID run = UUID.randomUUID();
        UUID lunch = UUID.randomUUID();
        PlanFixtures.createPlan(jdbc, user.id(), date, List.of(
                PlanFixtures.meal(lunch, "LUNCH", "13:00", "Chicken rice bowl"),
                PlanFixtures.workout(run, "18:00", "Running, 10 km/h", 30)));

        assertProblem(expect(403, "POST", ENTRIES, other.token(), record("2026-11-16", "Running, 10 km/h", run, "SKIPPED", null)),
                403, "ACCESS_DENIED");
        assertThat(expect(400, "POST", ENTRIES, user.token(), record("2026-11-16", "Running, 10 km/h", lunch, "SKIPPED", null))
                .body()).contains("planItemKey");

        JsonNode created = expect(201, "POST", ENTRIES, user.token(), record("2026-11-16", "Running, 10 km/h", run, "SKIPPED", null)).json();
        assertThat(created.get("entry").get("planItemKey").asString()).isEqualTo(run.toString());
        assertThat(created.get("entry").get("durationMin").isNull()).isTrue();
        assertThat(created.get("recoveryRequired").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM veya.v_active_plan_item WHERE item_key = ?", String.class, run))
                .isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.deviation_event d JOIN veya.daily_plan p ON p.id = d.daily_plan_id "
                + "WHERE p.user_id = ? AND d.deviation_type = 'ITEM_SKIPPED'", Integer.class, user.id())).isEqualTo(1);

        assertProblem(expect(409, "POST", ENTRIES, user.token(), record("2026-11-16", "Running, 10 km/h", run, "COMPLETED", 30)),
                409, "STATUS_ALREADY_RECORDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.activity_entry WHERE user_id = ?", Integer.class, user.id()))
                .isEqualTo(1);
    }
}
