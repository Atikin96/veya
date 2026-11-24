package com.veya.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** One line of a saved meal ({@code veya.saved_meal_item}, composite key saved_meal_id + line_no). */
@Entity
@Table(name = "saved_meal_item")
public class SavedMealItem {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "saved_meal_id")
        private UUID savedMealId;

        @Column(name = "line_no")
        private Short lineNo;

        protected Key() {
        }

        Key(UUID savedMealId, Short lineNo) {
            this.savedMealId = savedMealId;
            this.lineNo = lineNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(savedMealId, k.savedMealId) && Objects.equals(lineNo, k.lineNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(savedMealId, lineNo);
        }
    }

    @EmbeddedId
    private Key id;

    @MapsId("savedMealId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_meal_id")
    private SavedMeal savedMeal;

    /** Catalogue food, if the line came from the catalogue (set to null if the food is removed). */
    @Column(name = "food_item_id")
    private Long foodId;

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

    protected SavedMealItem() {
    }

    SavedMealItem(SavedMeal savedMeal, short lineNo, Long foodId, String foodName, BigDecimal portionG, BigDecimal kcal,
                  BigDecimal proteinG, BigDecimal fatG, BigDecimal carbsG) {
        this.id = new Key(savedMeal.getId(), lineNo);
        this.savedMeal = savedMeal;
        this.foodId = foodId;
        this.foodName = foodName;
        this.portionG = portionG;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.carbsG = carbsG;
    }

    public int getLineNo() {
        return id.lineNo;
    }

    public Long getFoodId() {
        return foodId;
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
}
