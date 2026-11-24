package com.veya.profile.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Planning constraints of one user and day ({@code veya.daily_constraint}, US-03, Q3, Q5): available time, available
 * equipment ({@code daily_constraint_equipment}) and ingredients that are available or temporarily excluded
 * ({@code daily_constraint_ingredient}, ingredient code -> AVAILABLE / EXCLUDED).
 */
@Entity
@Table(name = "daily_constraint")
public class DailyConstraint {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String EXCLUDED = "EXCLUDED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "constraint_date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "available_time_min")
    private Short availableTimeMin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "daily_constraint_equipment", joinColumns = @JoinColumn(name = "daily_constraint_id"))
    @Column(name = "equipment_code", nullable = false)
    private Set<String> equipment = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "daily_constraint_ingredient", joinColumns = @JoinColumn(name = "daily_constraint_id"))
    @MapKeyColumn(name = "ingredient_code")
    @Column(name = "availability", nullable = false)
    private Map<String, String> ingredients = new HashMap<>();

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected DailyConstraint() {
    }

    public DailyConstraint(UUID userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    /** PUT semantics: replaces all values of the day. */
    public void replace(Integer availableTimeMin, Collection<String> equipment, Collection<String> available,
                        Collection<String> excluded) {
        this.availableTimeMin = availableTimeMin == null ? null : availableTimeMin.shortValue();
        this.equipment.clear();
        this.equipment.addAll(equipment);
        this.ingredients.clear();
        available.forEach(code -> this.ingredients.put(code, AVAILABLE));
        excluded.forEach(code -> this.ingredients.put(code, EXCLUDED));
    }

    public LocalDate getDate() {
        return date;
    }

    public Integer getAvailableTimeMin() {
        return availableTimeMin == null ? null : availableTimeMin.intValue();
    }

    public List<String> getEquipment() {
        return equipment.stream().sorted().toList();
    }

    public List<String> getAvailableIngredients() {
        return codes(AVAILABLE);
    }

    public List<String> getExcludedIngredients() {
        return codes(EXCLUDED);
    }

    private List<String> codes(String availability) {
        return ingredients.entrySet().stream().filter(e -> e.getValue().equals(availability)).map(Map.Entry::getKey)
                .sorted().toList();
    }
}
