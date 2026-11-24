package com.veya.history.application;

import com.veya.history.infrastructure.DaySummaryRepository;
import com.veya.history.infrastructure.DaySummaryRepository.Consumed;
import com.veya.history.infrastructure.DaySummaryRepository.SummaryRow;
import com.veya.planning.api.PlanQueries;
import com.veya.planning.api.PlanResponse;
import com.veya.planning.api.PlanResponse.Plan;
import com.veya.profile.api.ProfileLookup;
import com.veya.profile.api.ProfileLookup.Targets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * US-20: planned and actual nutrition, remaining totals, activity and the active plan version of a day. Per Q10 there is
 * no history browsing (US-21 is Should). A day without a plan has {@code plan = null}; nutrition then comes from the
 * diary (AQ-9).
 */
@Service
public class DaySummaryService {

    public record Nutrition(BigDecimal plannedKcal, BigDecimal consumedKcal, BigDecimal consumedProteinG,
                            BigDecimal consumedFatG, BigDecimal consumedCarbsG, BigDecimal targetKcal, BigDecimal remainingKcal) {
    }

    public record Activity(int plannedWorkoutMin, int completedWorkoutMin) {
    }

    public record DaySummary(LocalDate date, Plan plan, Nutrition nutrition, Activity activity, int openItems) {
    }

    private final DaySummaryRepository repository;
    private final PlanQueries plans;
    private final ProfileLookup profiles;

    public DaySummaryService(DaySummaryRepository repository, PlanQueries plans, ProfileLookup profiles) {
        this.repository = repository;
        this.plans = plans;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public DaySummary summary(UUID userId, LocalDate date) {
        Optional<SummaryRow> planned = repository.planned(userId, date);
        if (planned.isPresent()) {
            SummaryRow s = planned.get();
            Plan plan = PlanResponse.of(plans.ownedPlan(userId, s.planId()));
            return new DaySummary(date, plan, new Nutrition(s.plannedKcal(), s.consumedKcal(), s.consumedProteinG(),
                    s.consumedFatG(), s.consumedCarbsG(), s.targetKcal(), s.remainingKcal()),
                    new Activity(s.plannedWorkoutMin(), s.completedWorkoutMin()), s.openItems());
        }
        Consumed c = repository.consumed(userId, date);
        BigDecimal target = profiles.targets(userId).map(Targets::kcal).orElse(null);
        return new DaySummary(date, null, new Nutrition(BigDecimal.ZERO, c.kcal(), c.proteinG(), c.fatG(), c.carbsG(), target,
                target == null ? null : target.subtract(c.kcal())),
                new Activity(0, repository.completedWorkoutMin(userId, date)), 0);
    }
}
