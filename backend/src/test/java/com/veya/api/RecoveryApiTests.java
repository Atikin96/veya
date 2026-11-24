package com.veya.api;

import com.veya.recovery.application.RecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.veya.api.PlanningApiTests.itemKey;
import static com.veya.api.PlanningApiTests.path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dynamic Plan Recovery over real HTTP (EPIC 4: US-15 to US-19, UC-01, NFR-09, NFR-10), including the complete
 * end-to-end scenario of persona Anna, the no-solution case, REJECT, concurrent decisions and transaction rollback.
 */
class RecoveryApiTests extends ApiTestSupport {

    private static final String PROPOSALS = "/api/v1/plans/{planId}/recovery-proposals";
    private static final String PROPOSAL = "/api/v1/recovery-proposals/{proposalId}";
    private static final String DECISION = "/api/v1/recovery-proposals/{proposalId}/decision";
    private static final String STATUS = PlanningApiTests.STATUS;

    @Autowired
    private RecoveryService recoveryService;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Autowired
    private TransactionTemplate transactions;

    private final PlanningApiTests planning = new PlanningApiTests();

    private JsonNode annaDay(TestUser user, String date) {
        planning.jdbc = jdbc;
        expect(200, "PUT", "/api/v1/me/profile", user.token(), ProfileApiTests.profile(1900, List.of()));
        Map<String, Object> body = Map.of("date", date, "items", List.of(
                planning.meal("BREAKFAST", "08:00", "Oatmeal with banana"), planning.meal("LUNCH", "13:00", "Chicken rice bowl"),
                planning.meal("DINNER", "19:30", "Salmon with potatoes"), planning.workout("18:00", "Running, 10 km/h", 30)));
        return expect(201, "POST", PlanningApiTests.PLANS, user.token(), body).json();
    }

    /** UC-01 deviations: lunch +250 kcal, run skipped, fish unavailable, 40 minutes, no equipment. */
    private void deviate(TestUser user, JsonNode plan, String date) {
        String planId = plan.get("planId").asString();
        String lunch = itemKey(plan, "LUNCH");
        Map<String, Object> pizza = NutritionApiTests.manual(date, "LUNCH", "Office pizza (3 slices)", 330,
                Math.round((plan.get("items").get(1).get("plannedKcal").asDouble() + 250) * 10) / 10.0);
        pizza.put("planItemKey", lunch);
        expect(201, "POST", "/api/v1/food-entries", user.token(), pizza);
        expect(200, "POST", STATUS, path(STATUS, planId, lunch), user.token(), Map.of("status", "COMPLETED"));
        Map<String, Object> skip = new HashMap<>();
        skip.put("date", date);
        skip.put("exerciseId", planning.exerciseId("Running, 10 km/h"));
        skip.put("planItemKey", itemKey(plan, "WORKOUT"));
        skip.put("status", "SKIPPED");
        skip.put("durationMin", null);
        expect(201, "POST", "/api/v1/activity-entries", user.token(), skip);
        expect(200, "PUT", "/api/v1/constraints/{date}", "/api/v1/constraints/" + date, user.token(), Map.of(
                "availableTimeMin", 40, "availableEquipment", List.of(), "availableIngredients", List.of(),
                "excludedIngredients", List.of("FISH")));
    }

    private int versions(String planId) {
        return jdbc.queryForObject("SELECT count(*) FROM veya.plan_version WHERE daily_plan_id = ?::uuid", Integer.class, planId);
    }

    @Test
    @DisplayName("UC-01 end to end: deviation -> proposal with reasons -> ACCEPT -> version 2, frozen items kept, recovery resolved")
    void endToEndRecovery() {
        TestUser anna = newUser("anna");
        TestUser max = newUser("max");
        String date = "2026-11-16";
        JsonNode plan = annaDay(anna, date);
        String planId = plan.get("planId").asString();
        String dinner = itemKey(plan, "DINNER");

        assertProblem(expect(409, "POST", PROPOSALS, path(PROPOSALS, planId, null), anna.token(), null), 409, "RECOVERY_NOT_REQUIRED");
        deviate(anna, plan, date);
        JsonNode deviations = expect(200, "GET", "/api/v1/plans/{planId}/deviations", "/api/v1/plans/" + planId + "/deviations",
                anna.token(), null).json();
        assertThat(deviations.toString()).contains("KCAL_DIFFERENCE", "ITEM_SKIPPED", "CONSTRAINT_INFEASIBLE");

        Response created = expect(201, "POST", PROPOSALS, path(PROPOSALS, planId, null), anna.token(), Map.of("baseVersion", 1));
        JsonNode proposal = created.json();
        String proposalId = proposal.get("proposalId").asString();
        assertThat(created.header("Location")).isEqualTo("/api/v1/recovery-proposals/" + proposalId);
        assertThat(proposal.get("status").asString()).isEqualTo("PENDING");
        assertThat(proposal.get("baseVersion").asInt()).isEqualTo(1);
        JsonNode dinnerChange = null;
        boolean addedWorkout = false;
        for (JsonNode change : proposal.get("changes")) {
            assertThat(change.get("reason").get("text").asString()).isNotBlank(); // US-17
            if (dinner.equals(change.get("targetItemKey").asString(null))) {
                dinnerChange = change;
            }
            addedWorkout |= change.get("changeType").asString().equals("ADD");
        }
        assertThat(dinnerChange).isNotNull();
        assertThat(dinnerChange.get("reason").get("category").asString()).isEqualTo("INGREDIENT");
        assertThat(dinnerChange.get("after").get("meal").get("name").asString()).isNotEqualTo("Salmon with potatoes");
        assertThat(addedWorkout).isTrue();
        assertThat(proposal.toString()).doesNotContain(itemKey(plan, "BREAKFAST")).doesNotContain(itemKey(plan, "LUNCH"));
        JsonNode totals = proposal.get("totals");
        assertThat(totals.get("forecastKcal").asDouble())
                .isEqualTo(totals.get("consumedKcal").asDouble() + totals.get("remainingPlannedKcalAfter").asDouble());
        assertThat(versions(planId)).isEqualTo(1); // the plan does not change before a decision

        assertThat(expect(200, "GET", PROPOSAL, PROPOSAL.replace("{proposalId}", proposalId), anna.token(), null).json()
                .get("changes")).isEqualTo(proposal.get("changes"));
        assertProblem(expect(403, "GET", PROPOSAL, PROPOSAL.replace("{proposalId}", proposalId), max.token(), null), 403, "ACCESS_DENIED");
        assertProblem(expect(403, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), max.token(),
                Map.of("decision", "ACCEPT", "baseVersion", 1)), 403, "ACCESS_DENIED");
        assertProblem(expect(404, "GET", PROPOSAL, PROPOSAL.replace("{proposalId}", UUID.randomUUID().toString()), anna.token(), null),
                404, "RESOURCE_NOT_FOUND");
        assertProblem(expect(403, "POST", PROPOSALS, path(PROPOSALS, planId, null), max.token(), null), 403, "ACCESS_DENIED");

        String staleProposal = expect(201, "POST", PROPOSALS, path(PROPOSALS, planId, null), anna.token(), null)
                .json().get("proposalId").asString();
        assertProblem(expect(400, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), anna.token(),
                Map.of("decision", "MAYBE", "baseVersion", 1)), 400, "VALIDATION_FAILED");
        assertProblem(expect(400, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), anna.token(),
                Map.of("decision", "EDIT", "baseVersion", 1, "selectedChangeIds", List.of(UUID.randomUUID()))), 400, "INVALID_EDIT");

        JsonNode accepted = expect(200, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), anna.token(),
                Map.of("decision", "ACCEPT", "baseVersion", 1)).json();
        JsonNode v2 = accepted.get("plan");
        assertThat(accepted.get("decision").asString()).isEqualTo("ACCEPT");
        assertThat(v2.get("activeVersion").asInt()).isEqualTo(2);
        assertThat(v2.get("changeReason").asString()).isEqualTo("RECOVERY_ACCEPTED");
        assertThat(v2.get("recoveryRequired").asBoolean()).isFalse();
        assertThat(v2.toString()).doesNotContain("Salmon with potatoes");
        assertThat(itemKey(v2, "BREAKFAST")).isEqualTo(itemKey(plan, "BREAKFAST"));
        for (JsonNode item : v2.get("items")) { // completed and recorded items are frozen and carried over unchanged
            if (item.get("itemKey").asString().equals(itemKey(plan, "LUNCH"))) {
                assertThat(item.get("status").asString()).isEqualTo("COMPLETED");
            }
            if (item.get("itemKey").asString().equals(itemKey(plan, "WORKOUT"))) {
                assertThat(item.get("status").asString()).isEqualTo("SKIPPED");
            }
        }
        assertThat(versions(planId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.deviation_event WHERE daily_plan_id = ?::uuid AND resolved_at IS NULL",
                Integer.class, planId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.food_entry WHERE user_id = ?", Integer.class, anna.id()))
                .isEqualTo(1); // confirmed records stay distinct from planned items and unchanged

        JsonNode stale = expect(409, "POST", DECISION, DECISION.replace("{proposalId}", staleProposal), anna.token(),
                Map.of("decision", "ACCEPT", "baseVersion", 1)).json();
        assertThat(stale.get("code").asString()).isEqualTo("STALE_PLAN_VERSION");
        assertThat(stale.get("currentVersion").asInt()).isEqualTo(2);
        assertProblem(expect(409, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), anna.token(),
                Map.of("decision", "ACCEPT", "baseVersion", 1)), 409, "PROPOSAL_ALREADY_DECIDED");
        assertThat(expect(200, "GET", PROPOSAL, PROPOSAL.replace("{proposalId}", proposalId), anna.token(), null).json()
                .get("status").asString()).isEqualTo("ACCEPTED");
        assertThat(expect(200, "GET", PROPOSAL, PROPOSAL.replace("{proposalId}", staleProposal), anna.token(), null).json()
                .get("status").asString()).isEqualTo("STALE");

        JsonNode summary = expect(200, "GET", "/api/v1/days/{date}/summary", "/api/v1/days/" + date + "/summary", anna.token(), null).json();
        assertThat(summary.get("plan").get("activeVersion").asInt()).isEqualTo(2);
        assertThat(versions(planId)).isEqualTo(2);
    }

    @Test
    @DisplayName("US-18: REJECT records the decision and leaves the active plan unchanged (recoveryRequired stays true)")
    void rejectKeepsPlan() {
        TestUser user = newUser("reject");
        JsonNode plan = annaDay(user, "2026-11-16");
        String planId = plan.get("planId").asString();
        deviate(user, plan, "2026-11-16");
        String proposalId = expect(201, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), null)
                .json().get("proposalId").asString();
        JsonNode rejected = expect(200, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), user.token(),
                Map.of("decision", "REJECT", "baseVersion", 1)).json();
        assertThat(rejected.get("plan").get("activeVersion").asInt()).isEqualTo(1);
        assertThat(rejected.get("plan").get("recoveryRequired").asBoolean()).isTrue();
        assertThat(rejected.get("plan").toString()).contains("Salmon with potatoes");
        assertThat(versions(planId)).isEqualTo(1);
        assertProblem(expect(400, "POST", DECISION, DECISION.replace("{proposalId}", proposalId), user.token(),
                Map.of("decision", "REJECT", "baseVersion", 1, "selectedChangeIds", List.of(UUID.randomUUID()))), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("US-19: no feasible combination -> 422 with blockingConstraints; no proposal and no new plan version")
    void noFeasibleRecovery() {
        TestUser user = newUser("nosolution");
        JsonNode plan = annaDay(user, "2026-11-16");
        String planId = plan.get("planId").asString();
        expect(200, "PUT", "/api/v1/constraints/{date}", "/api/v1/constraints/2026-11-16", user.token(), Map.of(
                "availableTimeMin", 2, "availableEquipment", List.of(), "availableIngredients", List.of(),
                "excludedIngredients", List.of()));
        JsonNode problem = expect(422, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), null).json();
        assertThat(problem.get("code").asString()).isEqualTo("RECOVERY_NOT_FEASIBLE");
        assertThat(problem.get("blockingConstraints").size()).isPositive();
        assertThat(problem.get("blockingConstraints").get(0).get("type").asString()).isEqualTo("TIME");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.recovery_proposal WHERE daily_plan_id = ?::uuid", Integer.class, planId))
                .isZero();
        assertThat(versions(planId)).isEqualTo(1);
        assertProblem(expect(409, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), Map.of("baseVersion", 7)),
                409, "STALE_PLAN_VERSION");
        assertProblem(expect(400, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), Map.of("baseVersion", 0)),
                400, "VALIDATION_FAILED");
        assertProblem(expect(400, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), "{not json"),
                400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("Regression (smoke test): the optional body may be omitted with any Content-Type (was 415)")
    void emptyBodyWithFormContentType() throws Exception {
        TestUser user = newUser("emptybody");
        JsonNode plan = annaDay(user, "2026-11-16");
        deviate(user, plan, "2026-11-16");
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port
                        + path(PROPOSALS, plan.get("planId").asString(), null)))
                .header("Authorization", "Bearer " + user.token())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    @Test
    @DisplayName("NFR-10: concurrent ACCEPT decisions of one proposal are serialized - exactly one wins, one new version")
    void concurrentDecisions() throws Exception {
        TestUser user = newUser("race");
        JsonNode plan = annaDay(user, "2026-11-16");
        String planId = plan.get("planId").asString();
        deviate(user, plan, "2026-11-16");
        String proposalId = expect(201, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), null)
                .json().get("proposalId").asString();
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Response> call = () -> {
                start.await();
                return call("POST", DECISION.replace("{proposalId}", proposalId), user.token(), Map.of("decision", "ACCEPT", "baseVersion", 1));
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Response> f : results) {
            statuses.add(f.get().status());
        }
        pool.shutdown();
        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(threads - 1);
        assertThat(versions(planId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.recovery_decision WHERE proposal_id = ?::uuid", Integer.class,
                proposalId)).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-09: decision and new plan version are one transaction - a failure after ACCEPT rolls both back")
    void acceptIsAtomic() {
        TestUser user = newUser("atomic");
        JsonNode plan = annaDay(user, "2026-11-16");
        String planId = plan.get("planId").asString();
        deviate(user, plan, "2026-11-16");
        UUID proposalId = UUID.fromString(expect(201, "POST", PROPOSALS, path(PROPOSALS, planId, null), user.token(), null)
                .json().get("proposalId").asString());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            recoveryService.decide(user.id(), proposalId, "ACCEPT", 1);
            assertThat(versions(planId)).isEqualTo(2); // visible inside the transaction ...
            throw new IllegalStateException("simulated failure after the decision");
        })).hasMessageContaining("simulated failure");

        assertThat(versions(planId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.recovery_decision WHERE proposal_id = ?", Integer.class, proposalId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT recovery_required FROM veya.daily_plan WHERE id = ?::uuid", Boolean.class, planId)).isTrue();
        // the proposal can still be accepted afterwards
        expect(200, "POST", DECISION, DECISION.replace("{proposalId}", proposalId.toString()), user.token(),
                Map.of("decision", "ACCEPT", "baseVersion", 1));
        assertThat(versions(planId)).isEqualTo(2);
    }
}
