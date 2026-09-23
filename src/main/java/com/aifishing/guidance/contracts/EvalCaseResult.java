package com.aifishing.guidance.contracts;

import java.util.List;
import java.util.UUID;

/**
 * Internal case result. {@code recordedOutcomeKind} describes the original action only
 * and must not be treated as shadow counterfactual success. {@code UNSCORABLE} means
 * the replayed action or target differed from the historically followed decision.
 */
public record EvalCaseResult(
        String schemaVersion,
        UUID evalRunId,
        String caseId,
        EvalCaseResultStatus status,
        List<GuidanceAction> allowedActions,
        List<GuidanceAction> forbiddenActions,
        List<String> invariants,
        DeliveredDecision actualDecision,
        GuidanceAction actualPrimaryAction,
        GuidanceAction recordedPrimaryAction,
        OutcomeKind recordedOutcomeKind,
        String skipReason,
        String errorMessage,
        String scenarioId,
        Long seed
) {
    public EvalCaseResult {
        allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        forbiddenActions = List.copyOf(forbiddenActions == null ? List.of() : forbiddenActions);
        invariants = List.copyOf(invariants == null ? List.of() : invariants);
    }
}
