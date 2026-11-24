package com.veya.planning.infrastructure;

import com.veya.planning.api.HardConstraints.CatalogExercise;
import com.veya.planning.api.HardConstraints.CatalogMeal;
import com.veya.planning.api.HardConstraints.DayConditions;
import com.veya.planning.api.PlanQueries;
import com.veya.planning.api.PlanReferences;
import com.veya.profile.api.ProfileLookup;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Plan persistence of the planning module. Deliberately JDBC: plan versions and items are immutable and can only be
 * created through the SECURITY DEFINER function {@code create_plan_version} (the application role has no INSERT on
 * those tables, NFR-09/Q2), and reads are projections of the views {@code v_active_plan_item} and
 * {@code v_meal_nutrition} (derived nutrition, ingredient arrays) that an entity mapping would only duplicate.
 */
@Repository
public class JdbcPlanStore implements PlanQueries, PlanReferences {

    /** NFR-02: at most 100 meal and 100 activity candidates are considered. */
    static final int CANDIDATE_LIMIT = 100;

    private record PlanHeader(UUID planId, UUID userId, LocalDate planDate, int currentVersionNo, boolean recoveryRequired,
                              String changeReason, Instant versionCreatedAt) {
    }

    private record ItemVersion(UUID planItemId, UUID userId, LocalDate planDate, String itemType, String status,
                               boolean active) {
    }

    private static final String HEADER = """
            SELECT dp.id AS plan_id, dp.user_id, dp.plan_date, dp.current_version_no, dp.recovery_required,
                   pv.change_reason, pv.created_at AS version_created_at
            FROM daily_plan dp
            JOIN plan_version pv ON pv.daily_plan_id = dp.id AND pv.version_no = dp.current_version_no
            """;

    private final JdbcClient jdbc;
    private final ProfileLookup profiles;

    public JdbcPlanStore(JdbcClient jdbc, ProfileLookup profiles) {
        this.jdbc = jdbc;
        this.profiles = profiles;
    }

    @Override
    public PlanView ownedPlan(UUID userId, UUID planId) {
        PlanHeader header = jdbc.sql(HEADER + " WHERE dp.id = :id").param("id", planId)
                .query((rs, n) -> header(rs)).optional().orElseThrow(ApiException::notFound);
        if (!header.userId().equals(userId)) {
            throw ApiException.accessDenied();
        }
        return view(header);
    }

    @Override
    public Optional<PlanView> planByDate(UUID userId, LocalDate date) {
        return jdbc.sql(HEADER + " WHERE dp.user_id = :user AND dp.plan_date = :date")
                .param("user", userId).param("date", date).query((rs, n) -> header(rs)).optional().map(this::view);
    }

    /** Plan id of the user's plan for the date with at least one version (used for 409 PLAN_ALREADY_EXISTS). */
    public boolean planExists(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM daily_plan WHERE user_id = :user AND plan_date = :date "
                        + "AND current_version_no >= 1)")
                .param("user", userId).param("date", date).query(Boolean.class).single();
    }

    /** Creates the next immutable version through {@code create_plan_version}; returns the new version number. */
    public int createVersion(UUID userId, LocalDate date, int expectedVersion, String reason, String itemsJson) {
        return jdbc.sql("SELECT create_plan_version(:user, :date, :expected, :reason, CAST(:items AS jsonb))")
                .param("user", userId).param("date", date).param("expected", expectedVersion).param("reason", reason)
                .param("items", itemsJson).query(Integer.class).single();
    }

    /** Records the status of a PLANNED item (app role: UPDATE(status) only; triggers record deviations, US-15). */
    public void recordStatus(UUID planItemId, String status) {
        jdbc.sql("UPDATE plan_item SET status = :status WHERE id = :id").param("status", status).param("id", planItemId)
                .update();
    }

    private PlanView view(PlanHeader h) {
        List<PlanItemView> items = jdbc.sql("""
                        SELECT v.plan_item_id, v.item_key, v.item_type, v.meal_slot_code, v.planned_time, v.meal_id,
                               v.meal_name, v.exercise_id, v.exercise_name, v.planned_kcal, v.planned_protein_g,
                               v.planned_fat_g, v.planned_carbs_g, v.duration_min, v.status
                        FROM v_active_plan_item v
                        WHERE v.daily_plan_id = :plan
                        ORDER BY v.planned_time NULLS LAST, v.item_type, v.item_key""")
                .param("plan", h.planId())
                .query((rs, n) -> new PlanItemView(rs.getObject("plan_item_id", UUID.class), rs.getObject("item_key", UUID.class),
                        rs.getString("item_type"), rs.getString("meal_slot_code"), time(rs.getTime("planned_time")),
                        rs.getObject("meal_id", Long.class), rs.getString("meal_name"),
                        rs.getObject("exercise_id", Long.class), rs.getString("exercise_name"),
                        rs.getBigDecimal("planned_kcal"), rs.getBigDecimal("planned_protein_g"),
                        rs.getBigDecimal("planned_fat_g"), rs.getBigDecimal("planned_carbs_g"),
                        rs.getObject("duration_min", Integer.class), rs.getString("status")))
                .list();
        return new PlanView(h.planId(), h.userId(), h.planDate(), h.currentVersionNo(), h.changeReason(),
                h.versionCreatedAt(), h.recoveryRequired(), items);
    }

    @Override
    public List<CatalogMeal> meals() {
        return jdbc.sql("""
                        SELECT meal_id, meal_name, meal_slot_code, prep_time_min, kcal, protein_g, fat_g, carbs_g, ingredient_codes
                        FROM v_meal_nutrition ORDER BY meal_id LIMIT :limit""")
                .param("limit", CANDIDATE_LIMIT)
                .query((rs, n) -> new CatalogMeal(rs.getLong("meal_id"), rs.getString("meal_name"),
                        rs.getString("meal_slot_code"), rs.getInt("prep_time_min"), rs.getBigDecimal("kcal"),
                        rs.getBigDecimal("protein_g"), rs.getBigDecimal("fat_g"), rs.getBigDecimal("carbs_g"),
                        codes(rs.getArray("ingredient_codes"))))
                .list();
    }

    @Override
    public List<CatalogExercise> exercises() {
        return jdbc.sql("""
                        SELECT e.id, e.name, e.met_value,
                               ARRAY(SELECT ee.equipment_code::text FROM exercise_equipment ee WHERE ee.exercise_id = e.id)
                                   AS equipment
                        FROM exercise e ORDER BY e.id LIMIT :limit""")
                .param("limit", CANDIDATE_LIMIT)
                .query((rs, n) -> new CatalogExercise(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("met_value"),
                        codes(rs.getArray("equipment"))))
                .list();
    }

    @Override
    public DayConditions conditions(UUID userId, LocalDate date) {
        Set<String> permanent = new TreeSet<>(profiles.permanentExclusions(userId));
        return profiles.dayConstraints(userId, date)
                .map(c -> new DayConditions(c.availableTimeMin(), new TreeSet<>(c.availableEquipment()),
                        new TreeSet<>(c.excludedIngredients()), permanent))
                .orElse(DayConditions.unrestricted(permanent));
    }

    @Override
    public BigDecimal consumedKcal(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT coalesce(sum(kcal), 0) FROM food_entry WHERE user_id = :user AND entry_date = :date")
                .param("user", userId).param("date", date).query(BigDecimal.class).single();
    }

    @Override
    public ActiveItem resolveActiveItem(UUID userId, UUID itemKey, LocalDate date, ItemType type, String field) {
        List<ItemVersion> versions = jdbc.sql("""
                        SELECT pi.id AS plan_item_id, dp.user_id, dp.plan_date, pi.item_type, pi.status,
                               (pv.version_no = dp.current_version_no) AS active
                        FROM plan_item pi
                        JOIN plan_version pv ON pv.id = pi.plan_version_id
                        JOIN daily_plan dp ON dp.id = pv.daily_plan_id
                        WHERE pi.item_key = :key""")
                .param("key", itemKey).query(ItemVersion.class).list();
        List<ItemVersion> own = versions.stream().filter(v -> v.userId().equals(userId)).toList();
        if (own.isEmpty()) {
            if (!versions.isEmpty()) {
                throw ApiException.accessDenied();
            }
            throw ApiException.validation(field, "is not a planned item");
        }
        ItemVersion active = own.stream().filter(ItemVersion::active).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.IMMUTABLE_HISTORY,
                        "Entries can only be linked to items of the active plan version."));
        if (!active.itemType().equals(type.name())) {
            throw ApiException.validation(field, "must be a planned " + type.name());
        }
        if (!active.planDate().equals(date)) {
            throw ApiException.validation(field, "belongs to the plan of " + active.planDate() + ", not " + date);
        }
        return new ActiveItem(active.planItemId(), itemKey, active.status());
    }

    @Override
    public Optional<Boolean> recoveryRequired(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT recovery_required FROM daily_plan WHERE user_id = :user AND plan_date = :date")
                .param("user", userId).param("date", date).query(Boolean.class).optional();
    }

    private static PlanHeader header(ResultSet rs) throws SQLException {
        return new PlanHeader(rs.getObject("plan_id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("plan_date", LocalDate.class), rs.getInt("current_version_no"),
                rs.getBoolean("recovery_required"), rs.getString("change_reason"),
                rs.getTimestamp("version_created_at").toInstant());
    }

    private static java.time.LocalTime time(Time time) {
        return time == null ? null : time.toLocalTime();
    }

    private static Set<String> codes(Array array) throws SQLException {
        return array == null ? Set.of() : new HashSet<>(Arrays.asList((String[]) array.getArray()));
    }
}
