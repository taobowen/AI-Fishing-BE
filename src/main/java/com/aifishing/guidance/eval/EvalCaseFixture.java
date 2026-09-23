package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.contracts.ReplayMode;

import java.util.List;
import java.util.UUID;

/**
 * Classpath fixture for an offline eval case. {@code exactPrimaryAction} is set
 * only when the expected action is unique for the scene.
 */
public record EvalCaseFixture(
        String caseId,
        EvalSuiteKind kind,
        ReplayMode replayMode,
        List<RecomputedComponent> recomputedComponents,
        List<GuidanceAction> allowedActions,
        List<GuidanceAction> forbiddenActions,
        List<String> invariants,
        GuidanceAction exactPrimaryAction,
        List<String> rubric,
        FrozenAgentRunSnapshot snapshot,
        UUID sourceRunId,
        GuidanceAction recordedPrimaryAction,
        UUID recordedTargetTripWaypointId,
        OutcomeKind recordedOutcomeKind,
        String scenarioId,
        Long seed,
        String skipReason
) {
    public EvalCaseFixture {
        recomputedComponents = List.copyOf(recomputedComponents == null ? List.of() : recomputedComponents);
        allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        forbiddenActions = List.copyOf(forbiddenActions == null ? List.of() : forbiddenActions);
        invariants = List.copyOf(invariants == null ? List.of() : invariants);
        rubric = List.copyOf(rubric == null ? List.of() : rubric);
    }

    boolean uniqueExactAction() {
        return exactPrimaryAction != null;
    }
}
