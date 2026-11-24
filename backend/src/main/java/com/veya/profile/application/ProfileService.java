package com.veya.profile.application;

import com.veya.profile.api.ProfileLookup;
import com.veya.profile.domain.UserProfile;
import com.veya.profile.infrastructure.DailyConstraintRepository;
import com.veya.profile.infrastructure.ProfileRepository;
import com.veya.shared.persistence.ReferenceCodes;
import com.veya.shared.persistence.ReferenceCodes.Vocabulary;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import com.veya.shared.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * US-02 profile and goal; US-03 / Q5 permanent food exclusions. Invalid values are rejected before anything is
 * written, so stored data does not change (US-02). Also the published {@link ProfileLookup} for other modules.
 */
@Service
public class ProfileService implements ProfileLookup {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profiles;
    private final DailyConstraintRepository constraints;
    private final ReferenceCodes codes;

    public ProfileService(ProfileRepository profiles, DailyConstraintRepository constraints, ReferenceCodes codes) {
        this.profiles = profiles;
        this.constraints = constraints;
        this.codes = codes;
    }

    @Transactional(readOnly = true)
    public UserProfile get(UUID userId) {
        return profiles.findById(userId).orElseThrow(() ->
                new ApiException(ErrorCode.PROFILE_NOT_FOUND, "Create the profile with PUT /api/v1/me/profile."));
    }

    /** PUT semantics: replaces the whole profile including the exclusion list. */
    @Transactional
    public UserProfile save(UUID userId, String goal, int energy, BigDecimal protein, BigDecimal fat, BigDecimal carbs,
                            List<String> exclusions) {
        List<FieldViolation> errors = new ArrayList<>();
        codes.check(Vocabulary.GOAL, "goal", goal, errors);
        codes.check(Vocabulary.INGREDIENT, "foodExclusions", exclusions, errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        UserProfile profile = profiles.findById(userId).orElseGet(() -> new UserProfile(userId));
        profile.update(goal, energy, protein, fat, carbs, exclusions);
        UserProfile saved = profiles.saveAndFlush(profile);
        log.info("Profile saved userId={} exclusions={}", userId, exclusions.size());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasProfile(UUID userId) {
        return profiles.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DayConstraintsView> dayConstraints(UUID userId, LocalDate date) {
        return constraints.findByUserIdAndDate(userId, date).map(c -> new DayConstraintsView(c.getAvailableTimeMin(),
                c.getEquipment(), c.getAvailableIngredients(), c.getExcludedIngredients()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Targets> targets(UUID userId) {
        return profiles.findById(userId).map(p -> new Targets(BigDecimal.valueOf(p.getEnergyTargetKcal()),
                p.getProteinTargetG(), p.getFatTargetG(), p.getCarbsTargetG()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> permanentExclusions(UUID userId) {
        return profiles.findById(userId).map(UserProfile::getFoodExclusions).orElse(List.of());
    }
}
