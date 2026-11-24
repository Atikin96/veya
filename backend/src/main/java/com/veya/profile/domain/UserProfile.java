package com.veya.profile.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Profile and goal of a user ({@code veya.user_profile}, US-02) with the permanent food exclusions
 * ({@code veya.profile_food_exclusion}, US-03 / Q5) mapped as an element collection. Targets are entered by the
 * user, never calculated (no BMR/TDEE).
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "goal_code", nullable = false)
    private String goalCode;

    @Column(name = "energy_target_kcal", nullable = false)
    private Integer energyTargetKcal;

    @Column(name = "protein_target_g", nullable = false, precision = 5, scale = 1)
    private BigDecimal proteinTargetG;

    @Column(name = "fat_target_g", nullable = false, precision = 5, scale = 1)
    private BigDecimal fatTargetG;

    @Column(name = "carbs_target_g", nullable = false, precision = 5, scale = 1)
    private BigDecimal carbsTargetG;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_food_exclusion", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "ingredient_code", nullable = false)
    private Set<String> foodExclusions = new TreeSet<>();

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(UUID userId) {
        this.userId = userId;
    }

    /** PUT semantics: replaces goal, targets and the whole exclusion list. */
    public void update(String goalCode, int energyTargetKcal, BigDecimal proteinTargetG, BigDecimal fatTargetG,
                       BigDecimal carbsTargetG, Collection<String> foodExclusions) {
        this.goalCode = goalCode;
        this.energyTargetKcal = energyTargetKcal;
        this.proteinTargetG = proteinTargetG;
        this.fatTargetG = fatTargetG;
        this.carbsTargetG = carbsTargetG;
        this.foodExclusions.clear();
        this.foodExclusions.addAll(foodExclusions);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getGoalCode() {
        return goalCode;
    }

    public int getEnergyTargetKcal() {
        return energyTargetKcal;
    }

    public BigDecimal getProteinTargetG() {
        return proteinTargetG;
    }

    public BigDecimal getFatTargetG() {
        return fatTargetG;
    }

    public BigDecimal getCarbsTargetG() {
        return carbsTargetG;
    }

    /** Sorted codes. */
    public List<String> getFoodExclusions() {
        return List.copyOf(new TreeSet<>(foodExclusions));
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
