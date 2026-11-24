package com.veya.activity.infrastructure;

import com.veya.shared.persistence.SqlLike;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Exercise catalogue listing (US-12). JDBC because it aggregates the required equipment into an array per exercise
 * with a case-insensitive substring filter in one bounded query.
 */
@Repository
public class ExerciseCatalog {

    public record ExerciseRow(long exerciseId, String name, BigDecimal metValue, List<String> requiredEquipment) {
    }

    private final JdbcClient jdbc;

    public ExerciseCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExerciseRow> exercises(String text, int limit) {
        String escaped = SqlLike.escape(text);
        return jdbc.sql("""
                        SELECT e.id, e.name, e.met_value,
                               ARRAY(SELECT ee.equipment_code::text FROM exercise_equipment ee
                                     WHERE ee.exercise_id = e.id ORDER BY ee.equipment_code) AS equipment
                        FROM exercise e
                        WHERE CAST(:text AS varchar) IS NULL OR lower(e.name) LIKE '%' || :text || '%'
                        ORDER BY lower(e.name), e.id
                        LIMIT :limit""")
                .param("text", escaped).param("limit", limit)
                .query((rs, n) -> new ExerciseRow(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("met_value"),
                        strings(rs.getArray("equipment"))))
                .list();
    }

    public boolean exists(long exerciseId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM exercise WHERE id = :id)").param("id", exerciseId)
                .query(Boolean.class).single();
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
