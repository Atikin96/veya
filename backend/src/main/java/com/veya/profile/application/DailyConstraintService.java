package com.veya.profile.application;

import com.veya.profile.domain.DailyConstraint;
import com.veya.profile.infrastructure.DailyConstraintRepository;
import com.veya.recovery.api.ConstraintFeasibility;
import com.veya.recovery.api.ConstraintFeasibility.PlanImpact;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** US-03 daily planning constraints (available time, equipment, available ingredients, temporary exclusions; Q3, Q5). */
@Service
public class DailyConstraintService {

    private static final Logger log = LoggerFactory.getLogger(DailyConstraintService.class);

    public record Constraints(LocalDate date, Integer availableTimeMin, List<String> availableEquipment,
                              List<String> availableIngredients, List<String> excludedIngredients) {
    }

    public record SaveResult(Constraints constraints, Optional<PlanImpact> planImpact) {
    }

    private final DailyConstraintRepository repository;
    private final ReferenceCodes codes;
    private final ConstraintFeasibility feasibility;

    public DailyConstraintService(DailyConstraintRepository repository, ReferenceCodes codes,
                                  ConstraintFeasibility feasibility) {
        this.repository = repository;
        this.codes = codes;
        this.feasibility = feasibility;
    }

    /** A day without saved constraints: empty lists and no time restriction (AQ-9, PROPOSED in the contract). */
    @Transactional(readOnly = true)
    public Constraints get(UUID userId, LocalDate date) {
        return repository.findByUserIdAndDate(userId, date).map(DailyConstraintService::view)
                .orElse(new Constraints(date, null, List.of(), List.of(), List.of()));
    }

    /** PUT semantics: replaces the day's constraints, then evaluates their impact on that day's plan (US-15 rule 3). */
    @Transactional
    public SaveResult save(UUID userId, LocalDate date, Integer availableTimeMin, List<String> equipment,
                           List<String> available, List<String> excluded) {
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.EQUIPMENT, "availableEquipment", equipment, errors);
        codes.check(Vocabulary.INGREDIENT, "availableIngredients", available, errors);
        codes.check(Vocabulary.INGREDIENT, "excludedIngredients", excluded, errors);
        Set<String> overlap = new HashSet<>(available);
        overlap.retainAll(excluded);
        if (!overlap.isEmpty()) {
            errors.add(FieldViolation.of("excludedIngredients",
                    "must not overlap availableIngredients: " + overlap.stream().sorted().toList()));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        DailyConstraint constraint = repository.findByUserIdAndDate(userId, date)
                .orElseGet(() -> new DailyConstraint(userId, date));
        constraint.replace(availableTimeMin, equipment, available, excluded);
        DailyConstraint saved = repository.saveAndFlush(constraint);
        Optional<PlanImpact> impact = feasibility.evaluate(userId, date);
        log.info("Daily constraints saved userId={} date={} infeasibleItems={}", userId, date,
                impact.map(i -> i.infeasibleItems().size()).orElse(0));
        return new SaveResult(view(saved), impact);
    }

    private static Constraints view(DailyConstraint c) {
        return new Constraints(c.getDate(), c.getAvailableTimeMin(), c.getEquipment(), c.getAvailableIngredients(),
                c.getExcludedIngredients());
    }
}
