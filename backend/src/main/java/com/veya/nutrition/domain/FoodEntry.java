package com.veya.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A confirmed diary entry ({@code veya.food_entry}, US-06). Values are stored exactly as confirmed by the user.
 * The guard trigger re-checks ownership and plan links; linking or correcting the kcal of an entry of a confirmed
 * planned meal re-evaluates the 200 kcal deviation in the database (US-15 rule 1). The logical key of the linked
 * planned item and the meal-slot order are read-only formulas.
 */
@Entity
@Table(name = "food_entry")
@DynamicUpdate
public class FoodEntry {

    public enum InputSource { CATALOG, MANUAL, SAVED_MEAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "meal_slot_code", nullable = false)
    private String mealSlot;

    @Column(name = "input_source", nullable = false, updatable = false)
    private String inputSource;

    @Column(name = "food_item_id", updatable = false)
    private Long foodId;

    @Column(name = "saved_meal_id", updatable = false)
    private UUID savedMealId;

    @Column(name = "plan_item_id", updatable = false)
    private UUID planItemId;

    @Formula("(select pi.item_key from plan_item pi where pi.id = plan_item_id)")
    private UUID planItemKey;

    @Formula("(select ms.sort_order from meal_slot ms where ms.code = meal_slot_code)")
    private Integer slotOrder;

    @Column(name = "food_name", nullable = false)
    private String foodName;

    @Column(name = "portion_g", nullable = false, precision = 7, scale = 1)
    private BigDecimal portionG;

    @Column(nullable = false, precision = 7, scale = 1)
    private BigDecimal kcal;

    @Column(name = "protein_g", nullable = false, precision = 6, scale = 1)
    private BigDecimal proteinG;

    @Column(name = "fat_g", nullable = false, precision = 6, scale = 1)
    private BigDecimal fatG;

    @Column(name = "carbs_g", nullable = false, precision = 6, scale = 1)
    private BigDecimal carbsG;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected FoodEntry() {
    }

    public FoodEntry(UUID userId, LocalDate date, String mealSlot, InputSource inputSource, Long foodId, UUID savedMealId,
                     UUID planItemId, String foodName, BigDecimal portionG, BigDecimal kcal, BigDecimal proteinG,
                     BigDecimal fatG, BigDecimal carbsG) {
        this.userId = userId;
        this.date = date;
        this.mealSlot = mealSlot;
        this.inputSource = inputSource.name();
        this.foodId = foodId;
        this.savedMealId = savedMealId;
        this.planItemId = planItemId;
        this.foodName = foodName;
        this.portionG = portionG;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.carbsG = carbsG;
    }

    /** US-06 correction: {@code null} keeps the current value. Date, source and links never change. */
    public void correct(String mealSlot, String foodName, BigDecimal portionG, BigDecimal kcal, BigDecimal proteinG,
                        BigDecimal fatG, BigDecimal carbsG) {
        if (mealSlot != null) {
            this.mealSlot = mealSlot;
        }
        if (foodName != null) {
            this.foodName = foodName;
        }
        if (portionG != null) {
            this.portionG = portionG;
        }
        if (kcal != null) {
            this.kcal = kcal;
        }
        if (proteinG != null) {
            this.proteinG = proteinG;
        }
        if (fatG != null) {
            this.fatG = fatG;
        }
        if (carbsG != null) {
            this.carbsG = carbsG;
        }
    }

    public boolean isOwnedBy(UUID user) {
        return userId.equals(user);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getMealSlot() {
        return mealSlot;
    }

    public String getInputSource() {
        return inputSource;
    }

    public Long getFoodId() {
        return foodId;
    }

    public UUID getSavedMealId() {
        return savedMealId;
    }

    public UUID getPlanItemKey() {
        return planItemKey;
    }

    public String getFoodName() {
        return foodName;
    }

    public BigDecimal getPortionG() {
        return portionG;
    }

    public BigDecimal getKcal() {
        return kcal;
    }

    public BigDecimal getProteinG() {
        return proteinG;
    }

    public BigDecimal getFatG() {
        return fatG;
    }

    public BigDecimal getCarbsG() {
        return carbsG;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
