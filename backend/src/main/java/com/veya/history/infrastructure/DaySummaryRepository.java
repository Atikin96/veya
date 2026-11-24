package com.veya.history.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * US-20 dashboard figures. JDBC over the database view {@code v_day_summary} (active version only, Q10) and, for a day
 * without a plan, over the diary and workout records - the aggregation is defined once in the database.
 */
@Repository
public class DaySummaryRepository {

    public record SummaryRow(UUID planId, BigDecimal plannedKcal, BigDecimal consumedKcal, BigDecimal consumedProteinG,
                             BigDecimal consumedFatG, BigDecimal consumedCarbsG, BigDecimal targetKcal,
                             BigDecimal remainingKcal, int plannedWorkoutMin, int completedWorkoutMin, int openItems) {
    }

    public record Consumed(BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
    }

    private final JdbcClient jdbc;

    public DaySummaryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SummaryRow> planned(UUID userId, LocalDate date) {
        return jdbc.sql("""
                        SELECT daily_plan_id AS plan_id, planned_kcal, consumed_kcal, consumed_protein_g, consumed_fat_g,
                               consumed_carbs_g, target_kcal, remaining_kcal, planned_workout_min, completed_workout_min,
                               open_items
                        FROM v_day_summary WHERE user_id = :user AND plan_date = :date AND active_version_no >= 1""")
                .param("user", userId).param("date", date).query(SummaryRow.class).optional();
    }

    public Consumed consumed(UUID userId, LocalDate date) {
        return jdbc.sql("""
                        SELECT coalesce(sum(kcal), 0) AS kcal, coalesce(sum(protein_g), 0) AS protein_g,
                               coalesce(sum(fat_g), 0) AS fat_g, coalesce(sum(carbs_g), 0) AS carbs_g
                        FROM food_entry WHERE user_id = :user AND entry_date = :date""")
                .param("user", userId).param("date", date).query(Consumed.class).single();
    }

    public int completedWorkoutMin(UUID userId, LocalDate date) {
        return jdbc.sql("""
                        SELECT coalesce(sum(duration_min), 0) FROM activity_entry
                        WHERE user_id = :user AND entry_date = :date AND status IN ('COMPLETED', 'CHANGED')""")
                .param("user", userId).param("date", date).query(Integer.class).single();
    }
}
