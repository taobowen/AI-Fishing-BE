package com.aifishing.guidance.replay;

import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.eval.VersionCompareReport;
import com.aifishing.guidance.eval.VersionCompareReport.ActionDifference;
import com.aifishing.guidance.eval.VersionCompareReport.OutcomeRates;
import com.aifishing.guidance.eval.VersionCompareReport.VersionReplay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Frozen evaluate of one historical run. Writes {@code guidance_eval_runs} only.
 * Replay decisions are eval output, not a historical delivered label.
 */
public record AgentRunEvaluateResponse(
        UUID runId,
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
        List<Replay> replays,
        Instant startedAt,
        Instant finishedAt
) {
    public AgentRunEvaluateResponse {
        versions = List.copyOf(versions == null ? List.of() : versions);
        actionDifferences = List.copyOf(actionDifferences == null ? List.of() : actionDifferences);
        replays = List.copyOf(replays == null ? List.of() : replays);
        if (outcomeRates == null) {
            outcomeRates = OutcomeRates.empty();
        }
    }

    static AgentRunEvaluateResponse from(UUID runId, VersionCompareReport report) {
        List<Replay> replays = new ArrayList<>();
        for (VersionReplay replay : report.replays()) {
            replays.add(Replay.from(replay));
        }
        return new AgentRunEvaluateResponse(
                runId,
                report.evalRunId(),
                report.versions(),
                report.replayMode(),
                report.scenariosEvaluated(),
                report.scenariosSkipped(),
                report.scenariosUnscorable(),
                report.agreementRate(),
                report.validationPassRate(),
                report.safetyBlockCount(),
                report.invalidMoveCount(),
                report.moveOscillationCount(),
                report.opportunityInCooldownCount(),
                report.actionDifferences(),
                report.outcomeRates(),
                replays,
                report.startedAt(),
                report.finishedAt()
        );
    }

    public record Replay(
            String caseId,
            String policyVersion,
            EvalCaseResultStatus status,
            GuidanceAction actualPrimaryAction,
            GuidanceAction recordedPrimaryAction,
            OutcomeKind recordedOutcomeKind,
            String skipReason,
            String errorMessage,
            DeliveredDecision delivered
    ) {
        static Replay from(VersionReplay replay) {
            var result = replay.result();
            DeliveredDecision delivered = result == null ? null : result.actualDecision();
            if (delivered == null) {
                delivered = replay.delivered();
            }
            return new Replay(
                    replay.caseId(),
                    replay.policyVersion(),
                    result == null ? null : result.status(),
                    result == null ? null : result.actualPrimaryAction(),
                    result == null ? null : result.recordedPrimaryAction(),
                    result == null ? null : result.recordedOutcomeKind(),
                    result == null ? null : result.skipReason(),
                    result == null ? null : result.errorMessage(),
                    delivered
            );
        }
    }
}
