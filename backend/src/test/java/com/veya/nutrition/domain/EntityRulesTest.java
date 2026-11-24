package com.veya.nutrition.domain;

import com.veya.activity.domain.ActivityEntry;
import com.veya.nutrition.domain.FoodEntry.InputSource;
import com.veya.profile.domain.DailyConstraint;
import com.veya.profile.domain.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("JPA entity rules: corrections, templates, workout status, profile and constraint replacement")
class EntityRulesTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("FoodEntry.correct changes only the sent fields (merge-patch, US-06); ownership check")
    void foodEntryCorrection() {
        FoodEntry entry = new FoodEntry(USER, DAY, "LUNCH", InputSource.MANUAL, null, null, null, "Pizza", d("330"), d("879.9"),
                d("32"), d("38"), d("110"));
        entry.correct(null, null, d("300"), d("799.9"), null, null, null);
        assertThat(entry.getPortionG()).isEqualByComparingTo("300");
        assertThat(entry.getKcal()).isEqualByComparingTo("799.9");
        assertThat(entry.getFoodName()).isEqualTo("Pizza");
        assertThat(entry.getMealSlot()).isEqualTo("LUNCH");
        entry.correct("DINNER", "Soup", null, null, d("1"), d("2"), d("3"));
        assertThat(List.of(entry.getMealSlot(), entry.getFoodName())).containsExactly("DINNER", "Soup");
        assertThat(List.of(entry.getProteinG(), entry.getFatG(), entry.getCarbsG())).containsExactly(d("1"), d("2"), d("3"));
        assertThat(entry.getInputSource()).isEqualTo("MANUAL");
        assertThat(entry.isOwnedBy(USER)).isTrue();
        assertThat(entry.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("SavedMeal numbers its lines from 1 and sums the totals (US-08)")
    void savedMeal() {
        SavedMeal meal = new SavedMeal(USER, "My usual breakfast");
        meal.addLine(12L, "Eggs", d("120"), d("171.6"), d("15.1"), d("11.4"), d("0.8"));
        meal.addLine(null, "Bread", d("60"), d("148.2"), d("7.8"), d("2.0"), d("24.6"));
        assertThat(meal.isNew()).isTrue();
        assertThat(meal.getItems()).extracting(SavedMealItem::getLineNo).containsExactly(1, 2);
        assertThat(meal.totalKcal()).isEqualByComparingTo("319.8");
        assertThat(meal.totalProteinG()).isEqualByComparingTo("22.9");
        assertThat(meal.totalFatG()).isEqualByComparingTo("13.4");
        assertThat(meal.totalCarbsG()).isEqualByComparingTo("25.4");
        assertThat(meal.getItems().get(1).getFoodId()).isNull();
    }

    @Test
    @DisplayName("ActivityEntry: duration required for COMPLETED/CHANGED, forbidden for SKIPPED (same rule as the DB CHECK)")
    void activityEntry() {
        assertThat(new ActivityEntry(USER, DAY, 1, null, ActivityEntry.Status.SKIPPED, null).getDurationMin()).isNull();
        assertThat(new ActivityEntry(USER, DAY, 1, null, ActivityEntry.Status.COMPLETED, 30).getDurationMin()).isEqualTo(30);
        assertThatThrownBy(() -> new ActivityEntry(USER, DAY, 1, null, ActivityEntry.Status.SKIPPED, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActivityEntry(USER, DAY, 1, null, ActivityEntry.Status.CHANGED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Profile and daily constraints use PUT semantics: everything is replaced")
    void putSemantics() {
        UserProfile profile = new UserProfile(USER);
        profile.update("LOSE_WEIGHT", 1900, d("110"), d("60"), d("220"), List.of("PEANUT", "FISH"));
        profile.update("GAIN_WEIGHT", 2500, d("150"), d("80"), d("300"), List.of("MILK"));
        assertThat(profile.getGoalCode()).isEqualTo("GAIN_WEIGHT");
        assertThat(profile.getFoodExclusions()).containsExactly("MILK");

        DailyConstraint day = new DailyConstraint(USER, DAY);
        day.replace(40, List.of("YOGA_MAT"), List.of("RICE"), List.of("FISH"));
        assertThat(day.getAvailableTimeMin()).isEqualTo(40);
        assertThat(day.getAvailableIngredients()).containsExactly("RICE");
        assertThat(day.getExcludedIngredients()).containsExactly("FISH");
        day.replace(null, List.of(), List.of(), List.of("MILK", "EGG"));
        assertThat(day.getAvailableTimeMin()).isNull();
        assertThat(day.getEquipment()).isEmpty();
        assertThat(day.getAvailableIngredients()).isEmpty();
        assertThat(day.getExcludedIngredients()).containsExactly("EGG", "MILK");
    }
}
