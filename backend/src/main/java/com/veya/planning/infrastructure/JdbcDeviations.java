package com.veya.planning.infrastructure;

import com.veya.planning.api.Deviations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JDBC because the insert relies on the partial unique index {@code deviation_event_open_item_uq}
 * (ON CONFLICT ... WHERE resolved_at IS NULL DO NOTHING), which JPA cannot express.
 */
@Repository
public class JdbcDeviations implements Deviations {

    private final JdbcClient jdbc;

    public JdbcDeviations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordConstraintInfeasible(UUID planId, UUID planItemId, String details) {
        jdbc.sql("""
                        INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type, details)
                        VALUES (:plan, :item, 'CONSTRAINT_INFEASIBLE', :details)
                        ON CONFLICT (plan_item_id, deviation_type) WHERE resolved_at IS NULL AND plan_item_id IS NOT NULL
                        DO NOTHING""")
                .param("plan", planId).param("item", planItemId)
                .param("details", details.length() > 500 ? details.substring(0, 500) : details).update();
    }

    @Override
    public List<Deviation> open(UUID planId) {
        return jdbc.sql("""
                        SELECT d.id, d.deviation_type, pi.item_key, d.kcal_difference, d.details, d.detected_at
                        FROM deviation_event d LEFT JOIN plan_item pi ON pi.id = d.plan_item_id
                        WHERE d.daily_plan_id = :plan AND d.resolved_at IS NULL
                        ORDER BY d.detected_at, d.id""")
                .param("plan", planId)
                .query((rs, n) -> new Deviation(rs.getObject("id", UUID.class), rs.getString("deviation_type"),
                        rs.getObject("item_key", UUID.class), rs.getBigDecimal("kcal_difference"), rs.getString("details"),
                        rs.getTimestamp("detected_at").toInstant()))
                .list();
    }
}
