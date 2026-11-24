package com.veya.nutrition.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** US-07 totals from the database view {@code v_daily_nutrition_summary} (aggregation stays in one place). */
@Repository
public class DailyNutritionView {

    public record DayTotals(BigDecimal kcal, BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG, int entryCount) {

        public static final DayTotals NONE = new DayTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    private final JdbcClient jdbc;

    public DailyNutritionView(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DayTotals> dayTotals(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT consumed_kcal AS kcal, consumed_protein_g AS protein_g, consumed_fat_g AS fat_g, "
                        + "consumed_carbs_g AS carbs_g, entry_count FROM v_daily_nutrition_summary "
                        + "WHERE user_id = :user AND entry_date = :date")
                .param("user", userId).param("date", date).query(DayTotals.class).optional();
    }
}
