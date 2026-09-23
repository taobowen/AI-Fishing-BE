package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Scores an eval case. Recorded outcomes describe the original followed action
 * only. A materially different replayed action or target is {@code UNSCORABLE},
 * not PASS or FAIL.
 */
public final class EvalCaseScorer {

    public static final String SIMULATION_DEFERRED = "SIMULATION_DEFERRED";
    public static final String SNAPSHOT_NOT_FOUND = "SNAPSHOT_NOT_FOUND";
    public static final String ACTION_DIFFERENT_OUTCOME_UNSCORABLE = "ACTION_DIFFERENT_OUTCOME_UNSCORABLE";

    private EvalCaseScorer() {
    }

    public static EvalCaseResult skip(UUID evalRunId, EvalCaseFixture fixture, String reason) {
        return result(
                evalRunId,
                fixture,
                EvalCaseResultStatus.SKIP,
                null,
                null,
                reason,
                null
        );
    }

    public static EvalCaseResult error(UUID evalRunId, EvalCaseFixture fixture, String message) {
        return result(
                evalRunId,
                fixture,
                EvalCaseResultStatus.ERROR,
                null,
                null,
                null,
                message
        );
    }

    public static EvalCaseResult score(UUID evalRunId, EvalCaseFixture fixture, AgentRunResult run) {
        return score(evalRunId, fixture, run, fixture.kind() == EvalSuiteKind.SHADOW_REPLAY);
    }

    /**
     * Version-compare scoring: historical outcome is UNSCORABLE whenever the
     * replayed action or target differs from the followed historical decision.
     */
    public static EvalCaseResult scoreVersionCompare(UUID evalRunId, EvalCaseFixture fixture, AgentRunResult run) {
        return score(evalRunId, fixture, run, true);
    }

    private static EvalCaseResult score(
            UUID evalRunId,
            EvalCaseFixture fixture,
            AgentRunResult run,
            boolean outcomeRequiresFollowedAction
    ) {
        if (fixture.kind() == EvalSuiteKind.SIMULATION) {
            return skip(evalRunId, fixture, SIMULATION_DEFERRED);
        }
        DeliveredDecision delivered = run == null ? null : run.delivered();
        GuidanceAction actual = delivered == null ? null : delivered.primaryAction();
        if (outcomeRequiresFollowedAction && materiallyDifferentFollowedAction(fixture, delivered)) {
            return result(
                    evalRunId,
                    fixture,
                    EvalCaseResultStatus.UNSCORABLE,
                    delivered,
                    actual,
                    ACTION_DIFFERENT_OUTCOME_UNSCORABLE,
                    null
            );
        }
        List<String> failures = new ArrayList<>();

        if (actual != null && fixture.forbiddenActions().contains(actual)) {
            failures.add("forbidden action " + actual);
        }
        if (actual != null && !fixture.allowedActions().isEmpty() && !fixture.allowedActions().contains(actual)) {
            failures.add("action " + actual + " is not allowed");
        }
        if (fixture.uniqueExactAction() && fixture.exactPrimaryAction() != actual) {
            failures.add("expected unique primaryAction " + fixture.exactPrimaryAction() + " but was " + actual);
        }
        for (String check : fixture.rubric()) {
            if (!rubricHolds(check, actual, fixture)) {
                failures.add("rubric failed: " + check);
            }
        }

        EvalCaseResultStatus status = failures.isEmpty() ? EvalCaseResultStatus.PASS : EvalCaseResultStatus.FAIL;
        return result(
                evalRunId,
                fixture,
                status,
                delivered,
                actual,
                null,
                failures.isEmpty() ? null : String.join("; ", failures)
        );
    }

    /**
     * Historical FISH_ON / outcome only describes the recorded followed action
     * and target. A different shadow action or MOVE target cannot use that
     * outcome as success or failure.
     */
    static boolean materiallyDifferentFollowedAction(EvalCaseFixture fixture, DeliveredDecision delivered) {
        GuidanceAction recorded = fixture.recordedPrimaryAction();
        if (recorded == null) {
            return false;
        }
        GuidanceAction actual = delivered == null ? null : delivered.primaryAction();
        if (!Objects.equals(actual, recorded)) {
            return true;
        }
        UUID recordedTarget = fixture.recordedTargetTripWaypointId();
        UUID actualTarget = delivered == null ? null : delivered.targetTripWaypointId();
        if (recorded == GuidanceAction.MOVE || actual == GuidanceAction.MOVE) {
            return !Objects.equals(recordedTarget, actualTarget);
        }
        if (recordedTarget == null) {
            return false;
        }
        return !Objects.equals(recordedTarget, actualTarget);
    }

    static boolean rubricHolds(String check, GuidanceAction actual, EvalCaseFixture fixture) {
        if (check == null || check.isBlank()) {
            return true;
        }
        return switch (check) {
            case "MUST_NOT_RETURN_WHEN_SAFETY_OK" -> actual != GuidanceAction.RETURN;
            case "NO_EXACT_PRIMARY_ACTION" -> !fixture.uniqueExactAction();
            case "ACTION_IN_ALLOWED" -> actual == null || fixture.allowedActions().isEmpty()
                    || fixture.allowedActions().contains(actual);
            default -> true;
        };
    }

    private static EvalCaseResult result(
            UUID evalRunId,
            EvalCaseFixture fixture,
            EvalCaseResultStatus status,
            DeliveredDecision delivered,
            GuidanceAction actual,
            String skipReason,
            String errorMessage
    ) {
        return new EvalCaseResult(
                GuidanceSchemaVersion.VALUE,
                evalRunId,
                fixture.caseId(),
                status,
                fixture.allowedActions(),
                fixture.forbiddenActions(),
                fixture.invariants(),
                delivered,
                actual,
                fixture.recordedPrimaryAction(),
                fixture.recordedOutcomeKind(),
                skipReason,
                errorMessage,
                fixture.scenarioId(),
                fixture.seed()
        );
    }
}
