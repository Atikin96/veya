package com.veya.recovery.infrastructure;

import com.veya.planning.api.PlanQueries.PlanItemView;
import com.veya.recovery.domain.RecoveryEngine.Change;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recovery proposals, changes and decisions (US-16 to US-18). JDBC: proposal status is derived by the view
 * {@code v_recovery_proposal_overview}, and a decision is applied only through the SECURITY DEFINER procedure
 * {@code apply_recovery_decision}, which checks ownership and baseVersion under a row lock and saves the decision and
 * the new plan version in the caller's transaction (NFR-09, NFR-10). The application role cannot write
 * {@code recovery_decision} or plan versions directly.
 */
@Repository
public class RecoveryRepository {

    public record ProposalRow(UUID proposalId, UUID planId, UUID userId, int baseVersion, int currentVersion, String status,
                              Instant createdAt) {
    }

    /** A stored change; {@code target} is the replaced/removed item of the base version (null for ADD). */
    public record ChangeRow(UUID changeId, int changeNo, String changeType, PlanItemView target, String itemType,
                            String mealSlot, LocalTime plannedTime, Long mealId, String mealName, Long exerciseId,
                            String exerciseName, BigDecimal plannedKcal, BigDecimal plannedProteinG, BigDecimal plannedFatG,
                            BigDecimal plannedCarbsG, Integer durationMin, String reasonCategory, String reasonText) {
    }

    public record DecisionRow(String decision, Instant decidedAt) {
    }

    private final JdbcClient jdbc;

    public RecoveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores the proposal and its changes; the insert trigger re-checks baseVersion and recoveryRequired. */
    public UUID insert(UUID planId, int baseVersion, List<Change> changes) {
        UUID proposalId = jdbc.sql("INSERT INTO recovery_proposal (daily_plan_id, base_version_no) VALUES (:plan, :base) RETURNING id")
                .param("plan", planId).param("base", baseVersion).query(UUID.class).single();
        int no = 1;
        for (Change c : changes) {
            boolean meal = c.newMeal() != null;
            jdbc.sql("""
                            INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, item_type,
                                meal_slot_code, planned_time, meal_id, exercise_id, planned_kcal, planned_protein_g,
                                planned_fat_g, planned_carbs_g, duration_min, reason_category, reason_text)
                            VALUES (:proposal, :no, :type, :target, :itemType, :slot, :time, :meal, :exercise, :kcal, :protein,
                                    :fat, :carbs, :duration, :category, :text)""")
                    .param("proposal", proposalId).param("no", no++).param("type", c.type().name())
                    .param("target", c.target() == null ? null : c.target().planItemId())
                    .param("itemType", c.itemType()).param("slot", meal ? c.mealSlot() : null)
                    .param("time", c.plannedTime() == null ? null : Time.valueOf(c.plannedTime()))
                    .param("meal", meal ? c.newMeal().id() : null)
                    .param("exercise", c.newExercise() == null ? null : c.newExercise().id())
                    .param("kcal", meal ? c.newMeal().kcal() : null).param("protein", meal ? c.newMeal().proteinG() : null)
                    .param("fat", meal ? c.newMeal().fatG() : null).param("carbs", meal ? c.newMeal().carbsG() : null)
                    .param("duration", c.durationMin())
                    .param("category", c.reason().category().name())
                    .param("text", c.reason().text().length() > 500 ? c.reason().text().substring(0, 500) : c.reason().text())
                    .update();
        }
        return proposalId;
    }

    public Optional<ProposalRow> find(UUID proposalId) {
        return jdbc.sql("""
                        SELECT proposal_id, daily_plan_id AS plan_id, user_id, base_version_no AS base_version,
                               current_version_no AS current_version, status, created_at
                        FROM v_recovery_proposal_overview WHERE proposal_id = :id""")
                .param("id", proposalId)
                .query((rs, n) -> new ProposalRow(rs.getObject("proposal_id", UUID.class), rs.getObject("plan_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getInt("base_version"), rs.getInt("current_version"),
                        rs.getString("status"), rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    public List<ChangeRow> changes(UUID proposalId) {
        return jdbc.sql("""
                        SELECT c.id, c.change_no, c.change_type, c.item_type, c.meal_slot_code, c.planned_time, c.meal_id,
                               m.name AS meal_name, c.exercise_id, e.name AS exercise_name, c.planned_kcal, c.planned_protein_g,
                               c.planned_fat_g, c.planned_carbs_g, c.duration_min, c.reason_category, c.reason_text,
                               t.id AS t_id, t.item_key AS t_key, t.item_type AS t_type, t.meal_slot_code AS t_slot,
                               t.planned_time AS t_time, t.meal_id AS t_meal, tm.name AS t_meal_name, t.exercise_id AS t_exercise,
                               te.name AS t_exercise_name, t.planned_kcal AS t_kcal, t.planned_protein_g AS t_protein,
                               t.planned_fat_g AS t_fat, t.planned_carbs_g AS t_carbs, t.duration_min AS t_duration,
                               t.status AS t_status
                        FROM recovery_change c
                        LEFT JOIN meal m ON m.id = c.meal_id
                        LEFT JOIN exercise e ON e.id = c.exercise_id
                        LEFT JOIN plan_item t ON t.id = c.target_item_id
                        LEFT JOIN meal tm ON tm.id = t.meal_id
                        LEFT JOIN exercise te ON te.id = t.exercise_id
                        WHERE c.proposal_id = :id ORDER BY c.change_no""")
                .param("id", proposalId)
                .query((rs, n) -> new ChangeRow(rs.getObject("id", UUID.class), rs.getInt("change_no"), rs.getString("change_type"),
                        target(rs), rs.getString("item_type"), rs.getString("meal_slot_code"), time(rs.getTime("planned_time")),
                        rs.getObject("meal_id", Long.class), rs.getString("meal_name"), rs.getObject("exercise_id", Long.class),
                        rs.getString("exercise_name"), rs.getBigDecimal("planned_kcal"), rs.getBigDecimal("planned_protein_g"),
                        rs.getBigDecimal("planned_fat_g"), rs.getBigDecimal("planned_carbs_g"),
                        rs.getObject("duration_min", Integer.class), rs.getString("reason_category"), rs.getString("reason_text")))
                .list();
    }

    /** Planned kcal of the PLANNED meals of a plan version (remaining meals the proposal was made for). */
    public BigDecimal remainingMealKcal(UUID planId, int versionNo) {
        return jdbc.sql("""
                        SELECT coalesce(sum(pi.planned_kcal), 0) FROM plan_item pi
                        JOIN plan_version pv ON pv.id = pi.plan_version_id
                        WHERE pv.daily_plan_id = :plan AND pv.version_no = :version AND pi.item_type = 'MEAL'
                          AND pi.status = 'PLANNED'""")
                .param("plan", planId).param("version", versionNo).query(BigDecimal.class).single();
    }

    /** {@code CALL apply_recovery_decision}: ACCEPT creates the next version, REJECT only records the decision. */
    public Integer decide(UUID userId, UUID proposalId, String decision, int baseVersion) {
        return jdbc.sql("CALL apply_recovery_decision(:user, :proposal, :decision, :base, NULL::uuid[], NULL)")
                .param("user", userId).param("proposal", proposalId).param("decision", decision).param("base", baseVersion)
                .query((rs, n) -> rs.getObject(1, Integer.class)).optional().orElse(null);
    }

    public Optional<DecisionRow> decision(UUID proposalId) {
        return jdbc.sql("SELECT decision, decided_at FROM recovery_decision WHERE proposal_id = :id").param("id", proposalId)
                .query((rs, n) -> new DecisionRow(rs.getString("decision"), rs.getTimestamp("decided_at").toInstant()))
                .optional();
    }

    private static PlanItemView target(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("t_id", UUID.class);
        if (id == null) {
            return null;
        }
        return new PlanItemView(id, rs.getObject("t_key", UUID.class), rs.getString("t_type"), rs.getString("t_slot"),
                time(rs.getTime("t_time")), rs.getObject("t_meal", Long.class), rs.getString("t_meal_name"),
                rs.getObject("t_exercise", Long.class), rs.getString("t_exercise_name"), rs.getBigDecimal("t_kcal"),
                rs.getBigDecimal("t_protein"), rs.getBigDecimal("t_fat"), rs.getBigDecimal("t_carbs"),
                rs.getObject("t_duration", Integer.class), rs.getString("t_status"));
    }

    private static LocalTime time(Time time) {
        return time == null ? null : time.toLocalTime();
    }
}
