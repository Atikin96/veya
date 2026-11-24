package com.veya.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Actual workout event ({@code veya.activity_entry}, US-13). Immutable once recorded: no setters, no updatable
 * columns, and the database guard trigger rejects changes of owner, item and status. Inserting a record linked to a
 * planned workout sets the planned item's status in the database (trigger activity_entry_after_insert); SKIPPED then
 * records a deviation (US-15 rule 2).
 */
@Entity
@Table(name = "activity_entry")
public class ActivityEntry {

    public enum Status { COMPLETED, SKIPPED, CHANGED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "exercise_id", nullable = false, updatable = false)
    private Long exerciseId;

    @Formula("(select e.name from exercise e where e.id = exercise_id)")
    private String exerciseName;

    @Column(name = "plan_item_id", updatable = false)
    private UUID planItemId;

    @Formula("(select pi.item_key from plan_item pi where pi.id = plan_item_id)")
    private UUID planItemKey;

    @Column(nullable = false, updatable = false)
    private String status;

    @Column(name = "duration_min", updatable = false)
    private Short durationMin;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ActivityEntry() {
    }

    /** Same rule as the database CHECK activity_entry_duration_by_status. */
    public ActivityEntry(UUID userId, LocalDate date, long exerciseId, UUID planItemId, Status status, Integer durationMin) {
        if ((status == Status.SKIPPED) != (durationMin == null)) {
            throw new IllegalArgumentException("durationMin is required for COMPLETED/CHANGED and must be null for SKIPPED");
        }
        this.userId = userId;
        this.date = date;
        this.exerciseId = exerciseId;
        this.planItemId = planItemId;
        this.status = status.name();
        this.durationMin = durationMin == null ? null : durationMin.shortValue();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public UUID getPlanItemKey() {
        return planItemKey;
    }

    public String getStatus() {
        return status;
    }

    public Integer getDurationMin() {
        return durationMin == null ? null : durationMin.intValue();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
