package com.veya.recovery.web;

import com.veya.identity.api.CurrentUser;
import com.veya.planning.api.PlanResponse;
import com.veya.planning.api.PlanResponse.ExerciseRef;
import com.veya.planning.api.PlanResponse.ItemRef;
import com.veya.planning.api.PlanResponse.MealRef;
import com.veya.planning.api.PlanResponse.Plan;
import com.veya.recovery.application.RecoveryService;
import com.veya.recovery.application.RecoveryService.Decision;
import com.veya.recovery.application.RecoveryService.Proposal;
import com.veya.recovery.domain.RecoveryEngine.Totals;
import com.veya.recovery.infrastructure.RecoveryRepository.ChangeRow;
import com.veya.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Recovery operations of docs/api/openapi.yaml (tag recovery, US-16 to US-19). */
@RestController
@RequestMapping("/api/v1")
public class RecoveryController {

    public record CreateRecoveryProposalRequest(Integer baseVersion) {
    }

    public record DecisionRequest(
            @NotNull @Pattern(regexp = "ACCEPT|EDIT|REJECT", message = "must be one of ACCEPT, EDIT, REJECT") String decision,
            @NotNull @Min(1) Integer baseVersion,
            @Size(min = 1) List<@NotNull UUID> selectedChangeIds) {
    }

    public record ReasonView(String category, String text) {
    }

    public record RecoveryChange(UUID changeId, int changeNo, String changeType, UUID targetItemKey, PlanResponse.Item before,
                                 ItemRef after, ReasonView reason) {
    }

    public record RecoveryProposal(UUID proposalId, UUID planId, int baseVersion, String status, Instant createdAt,
                                   List<RecoveryChange> changes, Totals totals) {
    }

    public record DecisionResult(UUID proposalId, String decision, Instant decidedAt, Plan plan) {
    }

    private final RecoveryService recovery;
    private final JsonMapper json;

    public RecoveryController(RecoveryService recovery, JsonMapper json) {
        this.recovery = recovery;
        this.json = json;
    }

    /**
     * operationId createRecoveryProposal (US-16, US-17, US-19). The body is optional: an empty body is accepted with any
     * Content-Type (clients often send none or a form type for an empty POST); a non-empty body must be JSON.
     */
    @PostMapping("/plans/{planId}/recovery-proposals")
    public ResponseEntity<RecoveryProposal> createRecoveryProposal(CurrentUser user, @PathVariable("planId") UUID planId,
            @RequestBody(required = false) String body) {
        Integer baseVersion = baseVersion(body);
        Proposal proposal = recovery.create(user.id(), planId, baseVersion);
        return ResponseEntity.created(URI.create("/api/v1/recovery-proposals/" + proposal.header().proposalId()))
                .body(view(proposal));
    }

    /** operationId getRecoveryProposal. */
    @GetMapping("/recovery-proposals/{proposalId}")
    public RecoveryProposal getRecoveryProposal(CurrentUser user, @PathVariable("proposalId") UUID proposalId) {
        return view(recovery.get(user.id(), proposalId));
    }

    /** operationId decideRecoveryProposal (US-18): ACCEPT / REJECT; EDIT is blocked by Q12. */
    @PostMapping("/recovery-proposals/{proposalId}/decision")
    public DecisionResult decideRecoveryProposal(CurrentUser user, @PathVariable("proposalId") UUID proposalId,
                                                 @Valid @RequestBody DecisionRequest r) {
        if (r.selectedChangeIds() != null && !"EDIT".equals(r.decision())) {
            throw ApiException.validation("selectedChangeIds", "must be absent for " + r.decision());
        }
        Decision d = recovery.decide(user.id(), proposalId, r.decision(), r.baseVersion());
        return new DecisionResult(d.proposalId(), d.decision(), d.decidedAt(), PlanResponse.of(d.plan()));
    }

    /** Parses the optional {@code CreateRecoveryProposalRequest}; unknown fields and invalid values -> 400. */
    private Integer baseVersion(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        CreateRecoveryProposalRequest request;
        try {
            request = json.readValue(body, CreateRecoveryProposalRequest.class);
        } catch (RuntimeException ex) {
            throw ApiException.validation("baseVersion", "the body must be JSON {\"baseVersion\": <integer >= 1>}");
        }
        if (request.baseVersion() != null && request.baseVersion() < 1) {
            throw ApiException.validation("baseVersion", "must be greater than or equal to 1");
        }
        return request.baseVersion();
    }

    private static RecoveryProposal view(Proposal p) {
        return new RecoveryProposal(p.header().proposalId(), p.header().planId(), p.header().baseVersion(), p.header().status(),
                p.header().createdAt(), p.changes().stream().map(RecoveryController::change).toList(), p.totals());
    }

    private static RecoveryChange change(ChangeRow c) {
        ItemRef after = "REMOVE".equals(c.changeType()) ? null : new ItemRef(c.itemType(), c.mealSlot(),
                PlanResponse.time(c.plannedTime()), c.mealId() == null ? null : new MealRef(c.mealId(), c.mealName()),
                c.exerciseId() == null ? null : new ExerciseRef(c.exerciseId(), c.exerciseName()), c.plannedKcal(),
                c.plannedProteinG(), c.plannedFatG(), c.plannedCarbsG(), c.durationMin());
        return new RecoveryChange(c.changeId(), c.changeNo(), c.changeType(), c.target() == null ? null : c.target().itemKey(),
                c.target() == null ? null : PlanResponse.item(c.target()), after, new ReasonView(c.reasonCategory(), c.reasonText()));
    }
}
