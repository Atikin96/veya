package com.veya.recovery.application;

import com.veya.planning.api.PlanQueries;
import com.veya.planning.api.PlanQueries.PlanView;
import com.veya.profile.api.ProfileLookup;
import com.veya.profile.api.ProfileLookup.Targets;
import com.veya.recovery.domain.RecoveryEngine;
import com.veya.recovery.domain.RecoveryEngine.Input;
import com.veya.recovery.domain.RecoveryEngine.Result;
import com.veya.recovery.domain.RecoveryEngine.Totals;
import com.veya.recovery.infrastructure.RecoveryRepository;
import com.veya.recovery.infrastructure.RecoveryRepository.ChangeRow;
import com.veya.recovery.infrastructure.RecoveryRepository.DecisionRow;
import com.veya.recovery.infrastructure.RecoveryRepository.ProposalRow;
import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.BlockingConstraint;
import com.veya.shared.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dynamic Plan Recovery use cases (US-16 to US-19, UC-01). The active plan changes only through an explicit,
 * authorized ACCEPT whose baseVersion is current; the decision and the new version are saved in one transaction by
 * {@code apply_recovery_decision} (NFR-09). EDIT is not available while its meaning is open (Q12).
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    public record Proposal(ProposalRow header, List<ChangeRow> changes, Totals totals) {
    }

    public record Decision(UUID proposalId, String decision, java.time.Instant decidedAt, PlanView plan) {
    }

    private final PlanQueries plans;
    private final ProfileLookup profiles;
    private final RecoveryRepository repository;

    public RecoveryService(PlanQueries plans, ProfileLookup profiles, RecoveryRepository repository) {
        this.plans = plans;
        this.profiles = profiles;
        this.repository = repository;
    }

    /** US-16/17/19: generates and stores a proposal; the plan is not changed. No feasible combination -> 422. */
    @Transactional
    public Proposal create(UUID userId, UUID planId, Integer clientBaseVersion) {
        PlanView plan = plans.ownedPlan(userId, planId);
        if (clientBaseVersion != null && clientBaseVersion != plan.activeVersion()) {
            throw stale(clientBaseVersion, plan.activeVersion());
        }
        if (!plan.recoveryRequired()) {
            throw new ApiException(ErrorCode.RECOVERY_NOT_REQUIRED, "No significant deviation is open for this plan.");
        }
        Result result = RecoveryEngine.propose(new Input(plan, plans.conditions(userId, plan.date()), plans.meals(),
                plans.exercises(), profiles.targets(userId).map(Targets::kcal).orElse(null),
                plans.consumedKcal(userId, plan.date())));
        if (!result.feasible()) {
            log.info("Recovery not feasible userId={} planId={} blocking={}", userId, planId, result.blocking().size());
            throw new ApiException(ErrorCode.RECOVERY_NOT_FEASIBLE,
                    "No combination of remaining meals and workouts satisfies your constraints.", List.of(),
                    Map.of("blockingConstraints", result.blocking().stream()
                            .map(b -> new BlockingConstraint(b.type().name(), b.message(), b.itemKey())).toList()));
        }
        UUID proposalId;
        try {
            proposalId = repository.insert(planId, plan.activeVersion(), result.changes());
        } catch (DataAccessException ex) { // the plan changed between reading and storing
            if ("VY409".equals(DatabaseErrors.sqlState(ex))) {
                throw stale(plan.activeVersion(), plan.activeVersion() + 1);
            }
            throw ex;
        }
        log.info("Recovery proposal created userId={} planId={} proposalId={} baseVersion={} changes={}", userId, planId,
                proposalId, plan.activeVersion(), result.changes().size());
        return read(proposalId);
    }

    @Transactional(readOnly = true)
    public Proposal get(UUID userId, UUID proposalId) {
        owned(userId, proposalId);
        return read(proposalId);
    }

    /**
     * US-18: ACCEPT or REJECT. Ownership and version are checked here for precise errors and again, under a row lock,
     * by the database procedure; concurrent decisions are serialized and exactly one wins (NFR-10).
     */
    @Transactional
    public Decision decide(UUID userId, UUID proposalId, String decision, int baseVersion) {
        if ("EDIT".equals(decision)) {
            throw new ApiException(ErrorCode.INVALID_EDIT, "EDIT is not available yet: its meaning is an open question (Q12). "
                    + "Use ACCEPT or REJECT.");
        }
        ProposalRow proposal = owned(userId, proposalId);
        if (repository.decision(proposalId).isPresent()) {
            throw alreadyDecided();
        }
        if (baseVersion != proposal.baseVersion() || proposal.baseVersion() != proposal.currentVersion()) {
            throw stale(baseVersion, proposal.currentVersion());
        }
        try {
            repository.decide(userId, proposalId, decision, baseVersion);
        } catch (DataAccessException ex) {
            ApiException mapped = DatabaseErrors.toApiException(ex);
            if (mapped != null && mapped.code() == ErrorCode.STALE_PLAN_VERSION) {
                throw mapped.getMessage() != null && mapped.getMessage().contains("already decided")
                        ? alreadyDecided() : stale(baseVersion, baseVersion + 1);
            }
            throw ex;
        }
        DecisionRow row = repository.decision(proposalId).orElseThrow();
        PlanView plan = plans.ownedPlan(userId, proposal.planId());
        log.info("Recovery decision userId={} proposalId={} decision={} activeVersion={}", userId, proposalId, decision,
                plan.activeVersion());
        return new Decision(proposalId, row.decision(), row.decidedAt(), plan);
    }

    private Proposal read(UUID proposalId) {
        ProposalRow header = repository.find(proposalId).orElseThrow(ApiException::notFound);
        List<ChangeRow> changes = repository.changes(proposalId);
        BigDecimal before = repository.remainingMealKcal(header.planId(), header.baseVersion());
        BigDecimal after = remainingAfter(before, changes);
        BigDecimal consumed = plans.consumedKcal(header.userId(), plans.ownedPlan(header.userId(), header.planId()).date());
        BigDecimal target = profiles.targets(header.userId()).map(Targets::kcal).orElse(null);
        return new Proposal(header, changes, new Totals(target, consumed, before, after, consumed.add(after)));
    }

    /** Remaining planned meal kcal if the proposal is accepted (pure, unit-tested). */
    static BigDecimal remainingAfter(BigDecimal before, List<ChangeRow> changes) {
        BigDecimal after = before;
        for (ChangeRow c : changes) {
            if (c.target() != null && c.target().isMeal()) {
                after = after.subtract(c.target().plannedKcal());
            }
            if (c.plannedKcal() != null) {
                after = after.add(c.plannedKcal());
            }
        }
        return after;
    }

    /** AQ-2 (contract): unknown proposal -> 404, another user's -> 403. */
    private ProposalRow owned(UUID userId, UUID proposalId) {
        ProposalRow proposal = repository.find(proposalId).orElseThrow(ApiException::notFound);
        if (!proposal.userId().equals(userId)) {
            throw ApiException.accessDenied();
        }
        return proposal;
    }

    private static ApiException alreadyDecided() {
        return new ApiException(ErrorCode.PROPOSAL_ALREADY_DECIDED, "This proposal was already decided.");
    }

    private static ApiException stale(int baseVersion, int currentVersion) {
        return new ApiException(ErrorCode.STALE_PLAN_VERSION, "Stale baseVersion " + baseVersion + ": active plan version is "
                + currentVersion + ".", List.of(), Map.of("currentVersion", currentVersion));
    }
}
