package com.aifishing.guidance.replay;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import com.aifishing.guidance.contracts.ToolName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stored-evidence run inspector. Sections are {@code state}, {@code context},
 * {@code decision}, {@code observedAction}, and {@code outcome}. No generated
 * chain-of-thought. SHADOW and kill-switch are never {@code deliveredLabeled}.
 */
public record AgentRunTraceResponse(
        UUID runId,
        UUID sessionId,
        AgentRunStatus status,
        AgentRunVisibility visibility,
        UUID triggerOutboxId,
        GuidanceTrigger trigger,
        List<GuidanceTrigger> relatedTriggers,
        List<String> reasonCodes,
        EvalComponentVersions versions,
        Instant startedAt,
        Instant finishedAt,
        Long latencyMs,
        Usage usage,
        boolean deliveredLabeled,
        Map<String, Object> state,
        Map<String, Object> context,
        List<ToolCall> tools,
        Decision decision,
        ObservedAction observedAction,
        Outcome outcome
) {
    public AgentRunTraceResponse {
        relatedTriggers = List.copyOf(relatedTriggers == null ? List.of() : relatedTriggers);
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        tools = List.copyOf(tools == null ? List.of() : tools);
        if (observedAction == null) {
            observedAction = ObservedAction.empty();
        }
        if (outcome == null) {
            outcome = Outcome.empty();
        }
    }

    public record Usage(
            AgentRunVisibility visibility,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Double costUsd,
            String pricingVersion,
            Map<String, Object> telemetry
    ) {
    }

    public record ToolCall(
            ToolName toolName,
            Map<String, Object> request,
            Map<String, Object> result,
            Integer latencyMs,
            Instant observedAt
    ) {
    }

    public record Decision(
            Map<String, Object> candidate,
            Map<String, Object> validation,
            Map<String, Object> delivered,
            boolean deliveredLabeled,
            String fallbackReason,
            List<HorizonStep> committedHorizon
    ) {
        public Decision {
            committedHorizon = List.copyOf(committedHorizon == null ? List.of() : committedHorizon);
        }
    }

    public record ObservedAction(
            List<ObservedActionEvent> history,
            ObservedActionEvent latest,
            ObservedActionKind latestKind,
            boolean followed,
            boolean acknowledged
    ) {
        public ObservedAction {
            history = List.copyOf(history == null ? List.of() : history);
            if (latestKind == null) {
                latestKind = ObservedActionKind.UNKNOWN;
            }
            followed = latestKind == ObservedActionKind.FOLLOWED;
        }

        static ObservedAction empty() {
            return new ObservedAction(List.of(), null, ObservedActionKind.UNKNOWN, false, false);
        }
    }

    public record ObservedActionEvent(
            UUID id,
            Instant occurredAt,
            UUID deliveredDecisionId,
            boolean followedPrimary,
            Boolean followedRecommendation,
            RecommendationRole recommendationRole,
            GuidanceAction actualAction,
            ObservedActionKind kind,
            Map<String, Object> payload
    ) {
    }

    public record Outcome(
            List<OutcomeEvent> history,
            OutcomeEvent latest
    ) {
        public Outcome {
            history = List.copyOf(history == null ? List.of() : history);
        }

        static Outcome empty() {
            return new Outcome(List.of(), null);
        }
    }

    public record OutcomeEvent(
            UUID id,
            Instant attributedAt,
            UUID deliveredDecisionId,
            OutcomeKind outcomeKind,
            AttributionDimension attributionDimension,
            boolean followedRecommendation,
            RecommendationRole recommendationRole,
            AttributionWindowKind windowKind,
            BigDecimal confidence,
            UUID fishInteractionId
    ) {
    }
}
