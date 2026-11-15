package com.veya.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Declarative integrity: PK/FK/UNIQUE/NOT NULL/CHECK constraints reject invalid rows with the expected
 * SQLSTATE (23505 unique, 23503 foreign key, 23502 not null, 23514 check). Statements run as veya_admin_user,
 * which has full DML, so the failures come from the schema and not from missing privileges.
 */
@DisplayName("Constraints and relationships")
class ConstraintAndRelationshipTests extends DatabaseTestSupport {

    @ParameterizedTest(name = "{0} -> SQLSTATE {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            duplicate e-mail, case-insensitive            | INSERT INTO app_user (email, password_hash, adult_confirmed_at) SELECT upper(email), password_hash, now() FROM app_user WHERE id = '#U' | 23505
            malformed e-mail                              | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('not-an-email', '$2a$10$UpgecL5vsIFUfSNZZIBdZO6AqF5jQfw34dMFxdLo1e2HIYgAsNuOm', now()) | 23514
            missing password hash (NOT NULL)              | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('x-#U@example.org', NULL, now()) | 23502
            missing adult confirmation (Q8)               | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('y-#U@example.org', '$2a$10$UpgecL5vsIFUfSNZZIBdZO6AqF5jQfw34dMFxdLo1e2HIYgAsNuOm', NULL) | 23502
            second profile for one user                   | INSERT INTO user_profile (user_id, goal_code, energy_target_kcal, protein_target_g, fat_target_g, carbs_target_g) VALUES ('#U', 'LOSE_WEIGHT', 1800, 90, 60, 200) | 23505
            energy target out of range (US-02)            | UPDATE user_profile SET energy_target_kcal = 100 WHERE user_id = '#U' | 23514
            unknown goal code (FK)                        | UPDATE user_profile SET goal_code = 'FLY' WHERE user_id = '#U' | 23503
            unknown excluded ingredient (FK)              | INSERT INTO profile_food_exclusion (user_id, ingredient_code) VALUES ('#U', 'UNOBTAINIUM') | 23503
            two constraint rows for one day               | INSERT INTO daily_constraint (user_id, constraint_date) VALUES ('#U', DATE '2026-11-20'), ('#U', DATE '2026-11-20') | 23505
            available time over 24 h                      | INSERT INTO daily_constraint (user_id, constraint_date, available_time_min) VALUES ('#U', DATE '2026-11-21', 1441) | 23514
            invalid ingredient availability               | INSERT INTO daily_constraint_ingredient VALUES ((SELECT id FROM daily_constraint WHERE user_id = '#U'), 'FISH', 'MAYBE') | 23514
            zero portion (US-06)                          | INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g) VALUES ('#U', DATE '2026-11-16', 'LUNCH', 'MANUAL', 'Soup', 0, 100, 1, 1, 1) | 23514
            negative kcal                                 | INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g) VALUES ('#U', DATE '2026-11-16', 'LUNCH', 'MANUAL', 'Soup', 100, -1, 1, 1, 1) | 23514
            CATALOG entry without food item               | INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g) VALUES ('#U', DATE '2026-11-16', 'LUNCH', 'CATALOG', 'Soup', 100, 50, 1, 1, 1) | 23514
            unknown meal slot (FK)                        | INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g) VALUES ('#U', DATE '2026-11-16', 'BRUNCH', 'MANUAL', 'Soup', 100, 50, 1, 1, 1) | 23503
            entry for unknown user (FK)                   | INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g) VALUES (gen_random_uuid(), DATE '2026-11-16', 'LUNCH', 'MANUAL', 'Soup', 100, 50, 1, 1, 1) | 23503
            blank food name                               | INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, food_name, portion_g, kcal, protein_g, fat_g, carbs_g) VALUES ('#U', DATE '2026-11-16', 'LUNCH', 'MANUAL', '   ', 100, 50, 1, 1, 1) | 23514
            SKIPPED workout with a duration (US-13)       | INSERT INTO activity_entry (user_id, entry_date, exercise_id, status, duration_min) VALUES ('#U', DATE '2026-11-16', (SELECT min(id) FROM exercise), 'SKIPPED', 20) | 23514
            COMPLETED workout without duration            | INSERT INTO activity_entry (user_id, entry_date, exercise_id, status) VALUES ('#U', DATE '2026-11-16', (SELECT min(id) FROM exercise), 'COMPLETED') | 23514
            unknown activity status                       | INSERT INTO activity_entry (user_id, entry_date, exercise_id, status, duration_min) VALUES ('#U', DATE '2026-11-16', (SELECT min(id) FROM exercise), 'PAUSED', 20) | 23514
            duplicate saved meal name per user            | INSERT INTO saved_meal (user_id, name) VALUES ('#U', 'Lunch box'), ('#U', 'Lunch box') | 23505
            consent withdrawn before it was granted       | INSERT INTO user_consent (user_id, consent_document_id, granted_at, withdrawn_at) VALUES ('#U', 1, now(), now() - interval '1 day') | 23514
            second active consent for the same document   | INSERT INTO user_consent (user_id, consent_document_id) VALUES ('#U', 1), ('#U', 1) | 23505
            second recovery_setting row                   | INSERT INTO recovery_setting VALUES (false, 200) | 23514
            duplicate recovery_setting row                | INSERT INTO recovery_setting VALUES (true, 150) | 23505
            deviation threshold out of range              | UPDATE recovery_setting SET kcal_deviation_threshold = 0 | 23514
            plain-text password (LEG-5)                   | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('p-#U@example.org', 'MySecretPassword123!', now()) | 23514
            MD5 password digest                           | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('m-#U@example.org', md5('secret'), now()) | 23514
            SHA-1 password digest                         | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('s-#U@example.org', 'e5e9fa1ba31ecd1ae84f75caaa474f3a663f05f4', now()) | 23514
            unsalted SHA-256 password digest              | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('h-#U@example.org', encode(sha256('secret'), 'hex'), now()) | 23514
            bcrypt with too low cost                      | INSERT INTO app_user (email, password_hash, adult_confirmed_at) VALUES ('c-#U@example.org', '$2a$03$UpgecL5vsIFUfSNZZIBdZO6AqF5jQfw34dMFxdLo1e2HIYgAsNuOm', now()) | 23514
            food macros above 100 g per 100 g             | INSERT INTO food_item (name, source, kcal_per_100g, protein_per_100g, fat_per_100g, carbs_per_100g) VALUES ('Impossible', 'LOCAL', 500, 60, 30, 20) | 23514
            PROVIDER food without external reference      | INSERT INTO food_item (name, source, kcal_per_100g, protein_per_100g, fat_per_100g, carbs_per_100g) VALUES ('Cached', 'PROVIDER', 100, 1, 1, 1) | 23514
            duplicate LOCAL food name                     | INSERT INTO food_item (name, source, kcal_per_100g, protein_per_100g, fat_per_100g, carbs_per_100g) VALUES ('BANANA', 'LOCAL', 89, 1, 0, 23) | 23505
            MET value out of range                        | INSERT INTO exercise (name, met_value, source) VALUES ('Teleporting', 0.5, 'LOCAL') | 23514
            deleting a food item used by a meal (FK)      | DELETE FROM food_item WHERE name = 'Banana' | 23503
            constraint deviation without details          | INSERT INTO deviation_event (daily_plan_id, deviation_type) SELECT id, 'CONSTRAINT_INFEASIBLE' FROM daily_plan WHERE user_id = '#U' | 23514
            """)
    void invalidRowsAreRejected(String description, String sqlTemplate, String expectedState) {
        Plan plan = createPlan();
        admin.update("INSERT INTO daily_constraint (user_id, constraint_date) VALUES (?, DATE '2026-11-19')", plan.userId());
        String sql = sqlTemplate.replace("#U", plan.userId().toString());

        assertSqlState(expectedState, () -> admin.update(sql));
    }

    @Test
    @DisplayName("Deviation CHECK: KCAL_DIFFERENCE requires kcal_difference; other types forbid it")
    void deviationShapeIsChecked() {
        Plan plan = createPlan();
        UUID lunch = activeItem(plan.lunch());

        assertSqlState("23514", () -> admin.update(
                "INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type) VALUES (?, ?, 'KCAL_DIFFERENCE')",
                planId(plan), lunch));
        assertSqlState("23514", () -> admin.update(
                "INSERT INTO deviation_event (daily_plan_id, plan_item_id, deviation_type, kcal_difference) VALUES (?, ?, 'ITEM_SKIPPED', 10)",
                planId(plan), lunch));
    }

    @Test
    @DisplayName("Recovery change CHECKs: REMOVE carries no new item, ADD has no target, reason is mandatory (US-17)")
    void recoveryChangeShapeIsChecked() {
        Plan plan = createPlan();
        causeDeviation(plan);
        UUID proposal = createProposal(plan, 1);
        UUID dinner = activeItem(plan.dinner());

        assertSqlState("23514", () -> app.update("""
                INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, item_type, reason_category, reason_text)
                VALUES (?, 1, 'REMOVE', ?, 'MEAL', 'TIME', 'No time')""", proposal, dinner));
        assertSqlState("23514", () -> app.update("""
                INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, item_type, exercise_id, duration_min,
                                             reason_category, reason_text)
                VALUES (?, 1, 'ADD', ?, 'WORKOUT', (SELECT min(id) FROM exercise), 20, 'TIME', 'Fits')""", proposal, dinner));
        assertSqlState("23514", () -> app.update("""
                INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, reason_category, reason_text)
                VALUES (?, 1, 'REMOVE', ?, 'TIME', '  ')""", proposal, dinner));
        assertSqlState("23502", () -> app.update("""
                INSERT INTO recovery_change (proposal_id, change_no, change_type, target_item_id, reason_category)
                VALUES (?, 1, 'REMOVE', ?, 'TIME')""", proposal, dinner));
        // NULL must not slip through a CHECK: ADD without an item type is rejected.
        assertSqlState("23514", () -> app.update("""
                INSERT INTO recovery_change (proposal_id, change_no, change_type, reason_category, reason_text)
                VALUES (?, 1, 'ADD', 'TIME', 'Fits')""", proposal));
    }

    @Test
    @DisplayName("Erasing a plan keeps the confirmed actual records, only unlinked (history stays distinct)")
    void erasingPlanKeepsActualRecords() {
        Plan plan = createPlan();
        UUID entry = logMeal(plan, plan.lunch(), 0);
        UUID workout = activeItem(plan.workout());
        UUID activity = app.queryForObject("""
                INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status, duration_min)
                VALUES (?, ?, (SELECT exercise_id FROM plan_item WHERE id = ?), ?, 'COMPLETED', 30) RETURNING id""",
                UUID.class, plan.userId(), plan.date(), workout, workout);

        admin.update("DELETE FROM daily_plan WHERE id = ?", planId(plan));

        assertThat(app.queryForObject("SELECT plan_item_id IS NULL FROM food_entry WHERE id = ?", Boolean.class, entry)).isTrue();
        assertThat(app.queryForObject("SELECT plan_item_id IS NULL FROM activity_entry WHERE id = ?", Boolean.class, activity)).isTrue();
    }

    @Test
    @DisplayName("US-04/LEG-4: deleting an account erases all personal records and keeps the catalogue")
    void accountDeletionCascades() {
        Plan plan = createPlan();
        UUID user = plan.userId();
        causeDeviation(plan);
        UUID proposal = createProposal(plan, 1);
        addReplaceMeal(proposal, 1, plan.dinner(), "Tofu vegetable stir-fry");
        decide(user, proposal, "ACCEPT", 1, null);
        app.update("INSERT INTO user_consent (user_id, consent_document_id) VALUES (?, 1)", user);
        app.update("INSERT INTO profile_food_exclusion (user_id, ingredient_code) VALUES (?, 'PEANUT')", user);
        app.update("INSERT INTO saved_meal (user_id, name) VALUES (?, 'Box')", user);
        UUID constraintId = app.queryForObject(
                "INSERT INTO daily_constraint (user_id, constraint_date, available_time_min) VALUES (?, ?, 30) RETURNING id",
                UUID.class, user, DAY);
        app.update("INSERT INTO daily_constraint_equipment VALUES (?, 'DUMBBELLS')", constraintId);
        app.update("INSERT INTO activity_entry (user_id, entry_date, exercise_id, plan_item_id, status) VALUES (?, ?, (SELECT exercise_id FROM v_active_plan_item WHERE item_key = ?), ?, 'SKIPPED')",
                user, DAY, plan.workout(), activeItem(plan.workout()));
        int catalogueBefore = app.queryForObject("SELECT count(*) FROM food_item", Integer.class);

        int deleted = app.update("DELETE FROM app_user WHERE id = ?", user);

        assertThat(deleted).isEqualTo(1);
        List<String> personalTables = List.of("user_profile", "user_consent", "profile_food_exclusion", "daily_constraint",
                "saved_meal", "daily_plan", "food_entry", "activity_entry");
        for (String table : personalTables) {
            assertThat(admin.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, user))
                    .as(table).isZero();
        }
        assertThat(admin.queryForObject("SELECT count(*) FROM daily_constraint_equipment WHERE daily_constraint_id = ?",
                Integer.class, constraintId)).isZero();
        assertThat(admin.queryForObject("SELECT count(*) FROM recovery_proposal WHERE id = ?", Integer.class, proposal)).isZero();
        assertThat(admin.queryForObject("SELECT count(*) FROM recovery_decision WHERE proposal_id = ?", Integer.class, proposal)).isZero();
        assertThat(app.queryForObject("SELECT count(*) FROM food_item", Integer.class)).isEqualTo(catalogueBefore);
    }

    @Test
    @DisplayName("ON DELETE SET NULL: deleting a saved meal keeps diary entries created from it (US-08)")
    void deletingSavedMealKeepsEntries() {
        UUID user = createUser();
        UUID savedMeal = app.queryForObject("INSERT INTO saved_meal (user_id, name) VALUES (?, 'Usual') RETURNING id", UUID.class, user);
        UUID entry = app.queryForObject("""
                INSERT INTO food_entry (user_id, entry_date, meal_slot_code, input_source, saved_meal_id, food_name, portion_g, kcal, protein_g, fat_g, carbs_g)
                VALUES (?, ?, 'BREAKFAST', 'SAVED_MEAL', ?, 'Eggs', 120, 172, 15, 11, 1) RETURNING id""", UUID.class, user, DAY, savedMeal);

        app.update("DELETE FROM saved_meal WHERE id = ?", savedMeal);

        assertThat(app.queryForObject("SELECT saved_meal_id IS NULL FROM food_entry WHERE id = ?", Boolean.class, entry)).isTrue();
    }
}
