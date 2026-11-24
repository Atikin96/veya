package com.veya.recovery.application;

import com.veya.planning.api.PlanQueries;
import com.veya.profile.api.ProfileLookup;
import com.veya.recovery.infrastructure.RecoveryRepository;
import com.veya.recovery.infrastructure.RecoveryRepository.ChangeRow;
import com.veya.recovery.infrastructure.RecoveryRepository.DecisionRow;
import com.veya.recovery.infrastructure.RecoveryRepository.ProposalRow;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.veya.planning.api.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RecoveryService with test doubles (Mockito) for the repositories: authorization, version and decision rules. */
@Tag("unit")
@DisplayName("RecoveryService: ownership, version checks and decision error mapping (US-18, NFR-10)")
class RecoveryServiceTest {

    private final PlanQueries plans = mock(PlanQueries.class);
    private final ProfileLookup profiles = mock(ProfileLookup.class);
    private final RecoveryRepository repository = mock(RecoveryRepository.class);
    private final RecoveryService service = new RecoveryService(plans, profiles, repository);
    private final UUID owner = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();

    @BeforeEach
    void proposal() {
        when(repository.find(proposalId)).thenReturn(Optional.of(
                new ProposalRow(proposalId, UUID.randomUUID(), owner, 1, 1, "PENDING", Instant.now())));
    }

    @Test
    @DisplayName("EDIT is rejected with INVALID_EDIT before anything is read (Q12 open)")
    void editBlocked() {
        assertThatThrownBy(() -> service.decide(owner, proposalId, "EDIT", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_EDIT));
        verify(repository, never()).decide(any(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Another user's proposal -> 403, unknown -> 404; the procedure is never called")
    void ownership() {
        assertThatThrownBy(() -> service.decide(UUID.randomUUID(), proposalId, "ACCEPT", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.decide(owner, UUID.randomUUID(), "ACCEPT", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(repository, never()).decide(any(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Stale baseVersion -> 409 STALE_PLAN_VERSION with currentVersion; already decided -> 409 PROPOSAL_ALREADY_DECIDED")
    void versionAndRepeat() {
        assertThatThrownBy(() -> service.decide(owner, proposalId, "ACCEPT", 2))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.STALE_PLAN_VERSION);
                    assertThat(e.properties()).containsEntry("currentVersion", 1);
                });
        when(repository.decision(proposalId)).thenReturn(Optional.of(new DecisionRow("ACCEPT", Instant.now())));
        assertThatThrownBy(() -> service.decide(owner, proposalId, "ACCEPT", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROPOSAL_ALREADY_DECIDED));
    }

    @Test
    @DisplayName("A concurrent winner detected by the database procedure (VY409 'already decided') is reported as already decided")
    void lostRace() {
        when(repository.decision(proposalId)).thenReturn(Optional.empty());
        when(repository.decide(owner, proposalId, "ACCEPT", 1)).thenThrow(new DataIntegrityViolationException("x",
                new PSQLException(new ServerErrorMessage("SERROR\0CVY409\0MProposal is already decided\0"))));
        assertThatThrownBy(() -> service.decide(owner, proposalId, "ACCEPT", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROPOSAL_ALREADY_DECIDED));
    }

    @Test
    @DisplayName("Proposal creation requires recoveryRequired and the current version")
    void createPreconditions() {
        var notRequired = plan(false, mealItem(SALMON, "DINNER", "19:30", "PLANNED"));
        when(plans.ownedPlan(owner, notRequired.planId())).thenReturn(notRequired);
        assertThatThrownBy(() -> service.create(owner, notRequired.planId(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RECOVERY_NOT_REQUIRED));
        assertThatThrownBy(() -> service.create(owner, notRequired.planId(), 3))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.STALE_PLAN_VERSION));
        verify(repository, never()).insert(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Totals after acceptance: replaced meal kcal removed, new meal kcal added, workouts ignored")
    void remainingAfter() {
        ChangeRow replaceDinner = new ChangeRow(UUID.randomUUID(), 1, "REPLACE", mealItem(SALMON, "DINNER", "19:30", "PLANNED"),
                "MEAL", "DINNER", null, TOFU.id(), TOFU.name(), null, null, TOFU.kcal(), null, null, null, null, "INGREDIENT", "r");
        ChangeRow addWorkout = new ChangeRow(UUID.randomUUID(), 2, "ADD", null, "WORKOUT", null, null, null, null, CIRCUIT.id(),
                CIRCUIT.name(), null, null, null, null, 20, "TIME", "r");
        ChangeRow removeWorkout = new ChangeRow(UUID.randomUUID(), 3, "REMOVE", workoutItem(RUN, "18:00", 30, "PLANNED"),
                null, null, null, null, null, null, null, null, null, null, null, null, "TIME", "r");
        assertThat(RecoveryService.remainingAfter(new BigDecimal("560.5"), List.of(replaceDinner, addWorkout, removeWorkout)))
                .isEqualByComparingTo("565.4");
        assertThat(RecoveryService.remainingAfter(BigDecimal.TEN, List.of())).isEqualByComparingTo("10");
    }
}
