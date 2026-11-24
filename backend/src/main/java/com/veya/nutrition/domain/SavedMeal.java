package com.veya.nutrition.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Generated;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable meal template ({@code veya.saved_meal}) with its lines ({@code veya.saved_meal_item}) as one aggregate
 * (US-08). Re-logging a template copies its lines into new diary entries and never changes the template.
 * The id is assigned in Java so the lines can share it as part of their composite key.
 */
@Entity
@Table(name = "saved_meal")
public class SavedMeal implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @OneToMany(mappedBy = "savedMeal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id.lineNo")
    private List<SavedMealItem> items = new ArrayList<>();

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew;

    protected SavedMeal() {
    }

    public SavedMeal(UUID userId, String name) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.isNew = true;
    }

    public void addLine(Long foodId, String foodName, BigDecimal portionG, BigDecimal kcal, BigDecimal proteinG,
                        BigDecimal fatG, BigDecimal carbsG) {
        items.add(new SavedMealItem(this, (short) (items.size() + 1), foodId, foodName, portionG, kcal, proteinG, fatG, carbsG));
    }

    public BigDecimal totalKcal() {
        return items.stream().map(SavedMealItem::getKcal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalProteinG() {
        return items.stream().map(SavedMealItem::getProteinG).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalFatG() {
        return items.stream().map(SavedMealItem::getFatG).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCarbsG() {
        return items.stream().map(SavedMealItem::getCarbsG).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isOwnedBy(UUID user) {
        return userId.equals(user);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public List<SavedMealItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
