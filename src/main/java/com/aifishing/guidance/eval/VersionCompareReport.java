package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.ReplayMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Raw metrics for frozen {@code v1} × {@code v2} replay. Outcome-rate
 * denominators include only comparable followed-action cases (same primary
 * action and same target as historical). UNSCORABLE rows are excluded from
 * success and failure.
 */
public record VersionCompareReport(
        UUID evalRunId,
        List<String> versions,
        ReplayMode replayMode,
        int scenariosEvaluated,
        int scenariosSkipped,
        int scenariosUnscorable,
        Double agreementRate,
        Double validationPassRate,
        int safetyBlockCount,
        int invalidMoveCount,
        int moveOscillationCount,
        int opportunityInCooldownCount,
        List<ActionDifference> actionDifferences,
        OutcomeRates outcomeRates,
        List<VersionReplay> replays,
        Instant startedAt,
        Instant finishedAt
) {
    public VersionCompareReport {
        versions = List.copyOf(versions == null ? List.of() : versions);
        actionDifferences = List.copyOf(actionDifferences == null ? List.of() : actionDifferences);
        replays = List.copyOf(replays == null ? List.of() : replays);
        if (outcomeRates == null) {
            outcomeRates = OutcomeRates.empty();
        }
    }

    public record ActionDifference(
            String caseId,
            String leftLabel,
            String rightLabel,
            GuidanceAction leftAction,
            UUID leftTargetTripWaypointId,
            GuidanceAction rightAction,
            UUID rightTargetTripWaypointId
    ) {
    }

    /**
     * Outcome rates over comparable followed-action replays only.
     */
    public record OutcomeRates(
            int comparableFollowedCases,
            int successCount,
            int failureCount,
            Double successRate,
            Double failureRate
    ) {
        public static OutcomeRates empty() {
            return new OutcomeRates(0, 0, 0, null, null);
        }
    }

    public record VersionReplay(
            String caseId,
            String policyVersion,
            EvalCaseResult result,
            AgentRunResult run
    ) {
        public DeliveredDecision delivered() {
            return run == null ? null : run.delivered();
        }
    }
}
