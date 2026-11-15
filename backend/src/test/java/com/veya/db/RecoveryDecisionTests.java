package com.veya.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** US-16..US-18, NFR-09 (atomicity), NFR-10 (stale baseVersion -> 409), UC-01. */
@DisplayName("Recovery proposals and decisions (apply_recovery_decision)")
class RecoveryDecisionTests extends DatabaseTestSupport {

    @Autowired
    private DataSource appDataSource;

    private record Scenario(Plan plan, UUID proposal, UUID replaceDinner, UUID addWorkout) {
    }

    /** UC-01: lunch +250 kcal, proposal replaces the dinner and adds a short workout. */
    private Scenario scenario() {
        Plan plan = createPlan();
        causeDeviation(plan);
        UUID proposal = createProposal(plan, 1);
        UUID replace = addReplaceMeal(proposal, 1, plan.dinner(), "Tofu vegetable stir-fry");
        UUID add = addWorkout(proposal, 2, "Bodyweight circuit", 20);
        return new Scenario(plan, proposal, replace, add);
    }

    private int decisions(UUID proposal) {
        return app.queryForObject("SELECT count(*) FROM recovery_decision WHERE proposal_id = ?", Integer.class, proposal);
    }

    private String proposalStatus(UUID proposal) {
        return app.queryForObject("SELECT status FROM v_recovery_proposal_overview WHERE proposal_id = ?", String.class, proposal);
    }

    @Test
    @DisplayName("US-18 ACCEPT: new version with frozen items, applied changes, decision and resolved deviations")
    void accept() {
        Scenario s = scenario();
        Plan plan = s.plan();

        Integer newVersion = decide(plan.userId(), s.proposal(), "ACCEPT", 1, null);

        assertThat(newVersion).isEqualTo(2);
        assertThat(currentVersion(plan)).isEqualTo(2);
        List<Map<String, Object>> items = app.queryForList("""
                SELECT item_key, item_type, status, coalesce(meal_name, exercise_name) AS name
                FROM v_active_plan_item WHERE daily_plan_id = ? ORDER BY planned_time, item_type""", planId(plan));
        assertThat(items).extracting(i -> i.get("name")).containsExactlyInAnyOrder(
                "Oatmeal with banana", "Chicken rice bowl", "Running, 10 km/h", "Tofu vegetable stir-fry", "Bodyweight circuit");
        assertThat(activeStatus(plan.breakfast())).isEqualTo("COMPLETED");   // frozen
        assertThat(activeStatus(plan.lunch())).isEqualTo("COMPLETED");       // frozen
        assertThat(app.queryForObject("SELECT meal_name FROM v_active_plan_item WHERE item_key = ?", String.class, plan.dinner()))
                .isEqualTo("Tofu vegetable stir-fry");                         // replaced, same logical item
        assertThat(app.queryForObject("""
                SELECT d.decision || ':' || pv.version_no || ':' || pv.change_reason
                FROM recovery_decision d JOIN plan_version pv ON pv.id = d.resulting_version_id WHERE d.proposal_id = ?""",
                String.class, s.proposal())).isEqualTo("ACCEPT:2:RECOVERY_ACCEPTED");
        assertThat(recoveryRequired(plan)).isFalse();
        assertThat(openDeviations(plan)).isZero();
        assertThat(proposalStatus(s.proposal())).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("US-18 REJECT: decision recorded, active plan unchanged, recoveryRequired stays set")
    void reject() {
        Scenario s = scenario();

        Integer newVersion = decide(s.plan().userId(), s.proposal(), "REJECT", 1, null);

        assertThat(newVersion).isNull();
        assertThat(currentVersion(s.plan())).isEqualTo(1);
        assertThat(versionCount(s.plan())).isEqualTo(1);
        assertThat(app.queryForObject("SELECT meal_name FROM v_active_plan_item WHERE item_key = ?", String.class, s.plan().dinner()))
                .isEqualTo("Salmon with potatoes");
        assertThat(recoveryRequired(s.plan())).isTrue();
        assertThat(proposalStatus(s.proposal())).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("US-18 EDIT: only the selected changes are applied (Q12 provisional interpretation)")
    void edit() {
        Scenario s = scenario();

        Integer newVersion = decide(s.plan().userId(), s.proposal(), "EDIT", 1, "{" + s.addWorkout() + "}");

        assertThat(newVersion).isEqualTo(2);
        assertThat(app.queryForObject("SELECT meal_name FROM v_active_plan_item WHERE item_key = ?", String.class, s.plan().dinner()))
                .isEqualTo("Salmon with potatoes");   // replacement not selected
        assertThat(app.queryForObject("SELECT count(*) FROM v_active_plan_item WHERE daily_plan_id = ? AND exercise_name = 'Bodyweight circuit'",
                Integer.class, planId(s.plan()))).isEqualTo(1);
        assertThat(proposalStatus(s.proposal())).isEqualTo("EDITED");
    }

    @Test
    @DisplayName("Invalid decision input -> VY400 (HTTP 400) and no data changed")
    void invalidEdit() {
        Scenario s = scenario();
        UUID user = s.plan().userId();
        Scenario other = scenario();

        assertSqlState("VY400", () -> decide(user, s.proposal(), "EDIT", 1, null));
        assertSqlState("VY400", () -> decide(user, s.proposal(), "EDIT", 1, "{}"));
        assertSqlState("VY400", () -> decide(user, s.proposal(), "EDIT", 1, "{" + other.replaceDinner() + "}"));
        assertSqlState("VY400", () -> decide(user, s.proposal(), "EDIT", 1, "{" + s.addWorkout() + "," + s.addWorkout() + "}"));
        assertSqlState("VY400", () -> decide(user, s.proposal(), "ACCEPT", 1, "{" + s.addWorkout() + "}"));
        assertSqlState("VY400", () -> decide(user, s.proposal(), "MAYBE", 1, null));
        assertThat(decisions(s.proposal())).isZero();
        assertThat(versionCount(s.plan())).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-10: stale baseVersion -> VY409 (HTTP 409); no decision and no version created")
    void staleBaseVersion() {
        Scenario s = scenario();
        UUID second = createProposal(s.plan(), 1);
        addReplaceMeal(second, 1, s.plan().dinner(), "Lentil soup");
        decide(s.plan().userId(), s.proposal(), "ACCEPT", 1, null);

        assertThat(proposalStatus(second)).isEqualTo("STALE");
        assertSqlState("VY409", () -> decide(s.plan().userId(), second, "ACCEPT", 1, null));
        assertSqlState("VY409", () -> decide(s.plan().userId(), second, "REJECT", 1, null));
        assertThat(decisions(second)).isZero();
        assertThat(currentVersion(s.plan())).isEqualTo(2);
        assertThat(versionCount(s.plan())).isEqualTo(2);
    }

    @Test
    @DisplayName("A client baseVersion different from the proposal's is a conflict")
    void clientBaseVersionMustMatch() {
        Scenario s = scenario();

        assertSqlState("VY409", () -> decide(s.plan().userId(), s.proposal(), "ACCEPT", 2, null));
        assertThat(decisions(s.proposal())).isZero();
    }

    @Test
    @DisplayName("A proposal is decided once")
    void decidedOnce() {
        Scenario s = scenario();
        decide(s.plan().userId(), s.proposal(), "REJECT", 1, null);

        assertSqlState("VY409", () -> decide(s.plan().userId(), s.proposal(), "ACCEPT", 1, null));
        assertSqlState("VY001", () -> addWorkout(s.proposal(), 3, "Yoga", 15));
    }

    @Test
    @DisplayName("LEG-3: another user's proposal is reported as not found (VY404)")
    void ownershipChecked() {
        Scenario s = scenario();
        UUID stranger = createUser();

        assertSqlState("VY404", () -> decide(stranger, s.proposal(), "ACCEPT", 1, null));
        assertSqlState("VY404", () -> decide(s.plan().userId(), UUID.randomUUID(), "ACCEPT", 1, null));
        assertThat(decisions(s.proposal())).isZero();
    }

    @Test
    @DisplayName("US-16 preconditions: proposals need recoveryRequired and the current version; completed items are frozen")
    void proposalPreconditions() {
        Plan calm = createPlan();
        assertSqlState("VY400", () -> createProposal(calm, 1));            // no significant deviation

        Scenario s = scenario();
        assertSqlState("VY409", () -> createProposal(s.plan(), 2));        // base version is not the active one
        UUID proposal = createProposal(s.plan(), 1);
        assertSqlState("VY400", () -> addReplaceMeal(proposal, 1, s.plan().lunch(), "Lentil soup"));   // lunch is COMPLETED
        assertSqlState("VY400", () -> app.update("""
                INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, item_type, planned_time, exercise_id,
                                             duration_min, reason_category, reason_text)
                VALUES (?, 1, 'REPLACE', ?, 'WORKOUT', '19:00', (SELECT min(id) FROM exercise), 20, 'TIME', 'x')""",
                proposal, activeItem(s.plan().dinner())));                   // a meal can only be replaced by a meal
    }

    @Test
    @DisplayName("NFR-09: an injected failure after the version insert rolls back the whole decision")
    void injectedFailureLeavesNoPartialPlan() {
        Scenario s = scenario();
        owner.execute("""
                CREATE FUNCTION veya.test_fail_decision() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.proposal_id = '%s' THEN
                        RAISE EXCEPTION 'injected failure' USING ERRCODE = 'VYTST';
                    END IF;
                    RETURN NEW;
                END $$""".formatted(s.proposal()));
        owner.execute("""
                CREATE TRIGGER test_fail_decision BEFORE INSERT ON veya.recovery_decision
                FOR EACH ROW EXECUTE FUNCTION veya.test_fail_decision()""");
        try {
            assertSqlState("VYTST", () -> decide(s.plan().userId(), s.proposal(), "ACCEPT", 1, null));
        } finally {
            owner.execute("DROP TRIGGER test_fail_decision ON veya.recovery_decision");
            owner.execute("DROP FUNCTION veya.test_fail_decision()");
        }

        assertThat(currentVersion(s.plan())).isEqualTo(1);
        assertThat(versionCount(s.plan())).isEqualTo(1);
        assertThat(decisions(s.proposal())).isZero();
        assertThat(recoveryRequired(s.plan())).isTrue();
        assertThat(openDeviations(s.plan())).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-10: two concurrent ACCEPTs on the same base version - exactly one wins, the other gets VY409")
    void concurrentDecisions() throws Exception {
        Scenario s = scenario();
        UUID second = createProposal(s.plan(), 1);
        addReplaceMeal(second, 1, s.plan().dinner(), "Lentil soup");
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(appDataSource));
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> tx.execute(status -> {
                Integer version = decide(s.plan().userId(), s.proposal(), "ACCEPT", 1, null);
                firstHoldsLock.countDown();
                sleep(800);   // keep the plan row locked while the second decision arrives
                return version;
            }));
            Future<Throwable> secondResult = pool.submit(() -> {
                firstHoldsLock.await(10, TimeUnit.SECONDS);
                try {
                    decide(s.plan().userId(), second, "ACCEPT", 1, null);   // blocks on the row lock, then sees version 2
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });

            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(2);
            Throwable loser = secondResult.get(15, TimeUnit.SECONDS);
            assertThat(loser).isNotNull();
            assertThat(sqlState(loser)).isEqualTo("VY409");
        } finally {
            pool.shutdownNow();
        }
        assertThat(currentVersion(s.plan())).isEqualTo(2);
        assertThat(versionCount(s.plan())).isEqualTo(2);
        assertThat(decisions(second)).isZero();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
