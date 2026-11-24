package com.veya.profile.web;

import com.veya.identity.api.CurrentUser;
import com.veya.profile.application.ProfileService;
import com.veya.profile.domain.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** operationIds getMyProfile, saveMyProfile (US-02, US-03). Targets are entered by the user, never calculated. */
@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {

    public record ProfileRequest(
            @NotNull String goal,
            @NotNull @Min(500) @Max(10000) Integer energyTargetKcal,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal proteinTargetG,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal fatTargetG,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal carbsTargetG,
            @NotNull List<String> foodExclusions) {
    }

    public record Profile(String goal, int energyTargetKcal, BigDecimal proteinTargetG, BigDecimal fatTargetG,
                          BigDecimal carbsTargetG, List<String> foodExclusions, Instant updatedAt) {
    }

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public Profile getMyProfile(CurrentUser user) {
        return map(profiles.get(user.id()));
    }

    @PutMapping
    public Profile saveMyProfile(CurrentUser user, @Valid @RequestBody ProfileRequest request) {
        return map(profiles.save(user.id(), request.goal(), request.energyTargetKcal(), request.proteinTargetG(),
                request.fatTargetG(), request.carbsTargetG(), request.foodExclusions()));
    }

    private static Profile map(UserProfile p) {
        return new Profile(p.getGoalCode(), p.getEnergyTargetKcal(), p.getProteinTargetG(), p.getFatTargetG(),
                p.getCarbsTargetG(), p.getFoodExclusions(), p.getUpdatedAt());
    }
}
