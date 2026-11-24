package com.veya.profile.web;

import com.veya.identity.api.CurrentUser;
import com.veya.profile.application.DailyConstraintService;
import com.veya.profile.application.DailyConstraintService.Constraints;
import com.veya.profile.application.DailyConstraintService.SaveResult;
import com.veya.recovery.api.ConstraintFeasibility.PlanImpact;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** operationIds getDailyConstraints, saveDailyConstraints (US-03, US-15). */
@RestController
@RequestMapping("/api/v1/constraints/{date}")
public class DailyConstraintController {

    public record DailyConstraintsRequest(
            @Min(0) @Max(1440) Integer availableTimeMin,
            @NotNull List<String> availableEquipment,
            @NotNull List<String> availableIngredients,
            @NotNull List<String> excludedIngredients) {
    }

    public record DailyConstraintsResult(Constraints constraints, PlanImpact planImpact) {
    }

    private final DailyConstraintService service;

    public DailyConstraintController(DailyConstraintService service) {
        this.service = service;
    }

    @GetMapping
    public Constraints getDailyConstraints(CurrentUser user,
                                           @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.get(user.id(), date);
    }

    @PutMapping
    public DailyConstraintsResult saveDailyConstraints(CurrentUser user,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DailyConstraintsRequest request) {
        SaveResult result = service.save(user.id(), date, request.availableTimeMin(), request.availableEquipment(),
                request.availableIngredients(), request.excludedIngredients());
        return new DailyConstraintsResult(result.constraints(), result.planImpact().orElse(null));
    }
}
