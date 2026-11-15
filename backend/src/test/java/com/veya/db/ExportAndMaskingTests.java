package com.veya.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** LEG-4 / Q6 data export; LEG-5 / LEG-8 masking and pseudonymisation for read-only reporting. */
@DisplayName("Data export, masking and anonymisation")
class ExportAndMaskingTests extends DatabaseTestSupport {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "anna.k@example.org, a***@e***.org",
            "max@mail.example.com, m***@m***.com",
            "x@y.io, x***@y***.io"
    })
    void emailMasking(String email, String masked) {
        assertThat(app.queryForObject("SELECT mask_email(?)", String.class, email)).isEqualTo(masked);
    }

    @Test
    @DisplayName("Pseudonym is stable, short and does not contain the user id")
    void pseudonym() {
        UUID id = UUID.randomUUID();
        String first = app.queryForObject("SELECT pseudonym(?)", String.class, id);
        String second = app.queryForObject("SELECT pseudonym(?)", String.class, id);

        assertThat(first).isEqualTo(second).matches("U-[0-9A-F]{12}").doesNotContain(id.toString());
    }

    @Test
    @DisplayName("Anonymised reporting views expose no identifiers")
    void reportingViewsHaveNoIdentifiers() {
        Plan plan = createPlan();
        logMeal(plan, plan.breakfast(), 0);
        String email = app.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, plan.userId());
        String ref = app.queryForObject("SELECT pseudonym(?)", String.class, plan.userId());

        String userRow = readonly.queryForObject(
                "SELECT row_to_json(v)::text FROM v_app_user_masked v WHERE user_ref = ?", String.class, ref);
        String nutritionRow = readonly.queryForObject(
                "SELECT row_to_json(v)::text FROM v_analytics_daily_nutrition v WHERE user_ref = ?", String.class, ref);

        assertThat(userRow).doesNotContain(email).doesNotContain(plan.userId().toString()).doesNotContain("$2a$");
        assertThat(nutritionRow).doesNotContain(plan.userId().toString());
        assertThat(readonly.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'v_app_user_masked' AND column_name IN ('id', 'email', 'password_hash')",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("LEG-4/Q6: export contains the user's own records and no password hash")
    void exportContainsOnlyOwnData() {
        Plan plan = createPlan();
        logMeal(plan, plan.breakfast(), 0);
        Plan other = createPlan();
        logMeal(other, other.breakfast(), 0);

        Map<String, Object> export = app.queryForMap("""
                SELECT e -> 'account' ->> 'id'                                  AS account_id,
                       jsonb_array_length(e -> 'foodEntries')                    AS food_entries,
                       jsonb_array_length(e #> '{plans,0,versions,0,items}')     AS plan_items,
                       (e #>> '{profile,energy_target_kcal}')::int               AS energy_target,
                       e::text                                                   AS json
                FROM export_user_data(?) AS e""", plan.userId());

        assertThat(export.get("account_id")).isEqualTo(plan.userId().toString());
        assertThat(export.get("food_entries")).isEqualTo(1);
        assertThat(export.get("plan_items")).isEqualTo(4);
        assertThat(export.get("energy_target")).isEqualTo(2000);
        assertThat((String) export.get("json")).doesNotContain("password").doesNotContain(other.userId().toString());
        assertThat(app.queryForObject("SELECT export_user_data(gen_random_uuid()) IS NULL", Boolean.class)).isTrue();
    }
}
