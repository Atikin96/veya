package com.veya.activity.web;

import com.veya.activity.application.ActivityService;
import com.veya.activity.application.ActivityService.EntryResult;
import com.veya.activity.domain.ActivityEntry.Status;
import com.veya.activity.infrastructure.ExerciseCatalog;
import com.veya.identity.api.CurrentUser;
import com.veya.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Activity operations of docs/api/openapi.yaml (tag activity): US-12 exercise selection, US-13 workout records. */
@RestController
@RequestMapping("/api/v1")
public class ActivityController {

    public record Exercise(long exerciseId, String name, BigDecimal metValue, List<String> requiredEquipment) {
    }

    public record ActivityEntryRequest(
            @NotNull LocalDate date,
            @NotNull @Positive Long exerciseId,
            UUID planItemKey,
            @NotNull @Pattern(regexp = "COMPLETED|SKIPPED|CHANGED", message = "must be COMPLETED, SKIPPED or CHANGED") String status,
            @Min(1) @Max(600) Integer durationMin) {
    }

    public record ActivityEntry(UUID entryId, LocalDate date, long exerciseId, String exerciseName, UUID planItemKey,
                                String status, Integer durationMin, Instant createdAt) {
    }

    public record ActivityEntryResult(ActivityEntry entry, Boolean recoveryRequired) {
    }

    private final ExerciseCatalog repository;
    private final ActivityService service;

    public ActivityController(ExerciseCatalog repository, ActivityService service) {
        this.repository = repository;
        this.service = service;
    }

    /** operationId listExercises (US-12). Filters by muscle/difficulty/equipment are US-11 (Should, not MVP). */
    @GetMapping("/exercises")
    @Transactional(readOnly = true)
    public List<Exercise> listExercises(CurrentUser user,
                                        @RequestParam(name = "q", required = false) @Size(min = 2, max = 100) String q,
                                        @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(100) int limit) {
        return repository.exercises(q == null ? null : q.trim(), limit).stream()
                .map(e -> new Exercise(e.exerciseId(), e.name(), e.metValue(), e.requiredEquipment()))
                .toList();
    }

    /** operationId listActivityEntries (US-13, US-20). */
    @GetMapping("/activity-entries")
    public List<ActivityEntry> listActivityEntries(CurrentUser user,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(user.id(), date).stream().map(ActivityController::entry).toList();
    }

    /** operationId createActivityEntry (US-13, US-15). */
    @PostMapping("/activity-entries")
    public ResponseEntity<ActivityEntryResult> createActivityEntry(CurrentUser user,
                                                                   @Valid @RequestBody ActivityEntryRequest r) {
        // Same rule as the database CHECK activity_entry_duration_by_status (contract: if/then/else on status).
        if ("SKIPPED".equals(r.status()) ? r.durationMin() != null : r.durationMin() == null) {
            throw ApiException.validation("durationMin", "SKIPPED".equals(r.status())
                    ? "must be null for SKIPPED" : "is required for " + r.status());
        }
        EntryResult result = service.record(user.id(), r.date(), r.exerciseId(), r.planItemKey(), Status.valueOf(r.status()),
                r.durationMin());
        return ResponseEntity.created(URI.create("/api/v1/activity-entries/" + result.entry().getId()))
                .body(new ActivityEntryResult(entry(result.entry()), result.recoveryRequired()));
    }

    private static ActivityEntry entry(com.veya.activity.domain.ActivityEntry e) {
        return new ActivityEntry(e.getId(), e.getDate(), e.getExerciseId(), e.getExerciseName(), e.getPlanItemKey(),
                e.getStatus(), e.getDurationMin(), e.getCreatedAt());
    }
}
