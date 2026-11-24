package com.veya.activity.application;

import com.veya.activity.domain.ActivityEntry;
import com.veya.activity.domain.ActivityEntry.Status;
import com.veya.activity.infrastructure.ActivityEntryRepository;
import com.veya.activity.infrastructure.ExerciseCatalog;
import com.veya.planning.api.PlanReferences;
import com.veya.planning.api.PlanReferences.ActiveItem;
import com.veya.planning.api.PlanReferences.ItemType;
import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US-13: the actual workout event (COMPLETED / SKIPPED / CHANGED) is persisted; one record per planned workout. */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    public record EntryResult(ActivityEntry entry, Boolean recoveryRequired) {
    }

    private final ActivityEntryRepository entries;
    private final ExerciseCatalog exercises;
    private final PlanReferences plans;
    private final EntityManager entityManager;

    public ActivityService(ActivityEntryRepository entries, ExerciseCatalog exercises, PlanReferences plans,
                           EntityManager entityManager) {
        this.entries = entries;
        this.exercises = exercises;
        this.plans = plans;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> day(UUID userId, LocalDate date) {
        return entries.findByUserIdAndDateOrderByCreatedAtAscIdAsc(userId, date);
    }

    @Transactional
    public EntryResult record(UUID userId, LocalDate date, long exerciseId, UUID planItemKey, Status status,
                              Integer durationMin) {
        if (!exercises.exists(exerciseId)) {
            throw ApiException.validation("exerciseId", "is not a known exercise");
        }
        UUID planItemId = null;
        if (planItemKey != null) {
            ActiveItem item = plans.resolveActiveItem(userId, planItemKey, date, ItemType.WORKOUT, "planItemKey");
            if (!"PLANNED".equals(item.status())) {
                throw alreadyRecorded();
            }
            planItemId = item.planItemId();
        }
        ActivityEntry entry;
        try {
            entry = entries.saveAndFlush(new ActivityEntry(userId, date, exerciseId, planItemId, status, durationMin));
        } catch (DataIntegrityViolationException ex) { // concurrent second record for the same planned workout
            if ("activity_entry_plan_item_uq".equals(DatabaseErrors.constraintName(ex))) {
                throw alreadyRecorded();
            }
            throw ex;
        }
        entityManager.refresh(entry);
        Boolean recoveryRequired = plans.recoveryRequired(userId, date).orElse(null);
        log.info("Workout recorded userId={} entryId={} status={} linkedToPlan={} recoveryRequired={}", userId,
                entry.getId(), status, planItemId != null, recoveryRequired);
        return new EntryResult(entry, recoveryRequired);
    }

    private static ApiException alreadyRecorded() {
        return new ApiException(ErrorCode.STATUS_ALREADY_RECORDED, "Status of this workout is already recorded.");
    }
}
