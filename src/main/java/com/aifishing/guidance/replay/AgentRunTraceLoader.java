package com.aifishing.guidance.replay;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.UsageTelemetry;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.persistence.AgentCandidateDecisionEntity;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallEntity;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.persistence.AgentValidationEntity;
import com.aifishing.guidance.persistence.AgentValidationRepository;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.persistence.UserActionEventEntity;
import com.aifishing.guidance.persistence.UserActionEventRepository;
import com.aifishing.guidance.replay.AgentRunTraceResponse.Decision;
import com.aifishing.guidance.replay.AgentRunTraceResponse.ObservedAction;
import com.aifishing.guidance.replay.AgentRunTraceResponse.ObservedActionEvent;
import com.aifishing.guidance.replay.AgentRunTraceResponse.Outcome;
import com.aifishing.guidance.replay.AgentRunTraceResponse.OutcomeEvent;
import com.aifishing.guidance.replay.AgentRunTraceResponse.ToolCall;
import com.aifishing.guidance.replay.AgentRunTraceResponse.Usage;
import com.aifishing.guidance.runtime.GuidanceFallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconstructs one historical agent run from Phase 1 tables. Stored evidence
 * only: state, context, tools, candidate, validation, decision. Never generates
 * chain-of-thought. SHADOW may be loaded by id; it is never labeled delivered.
 */
@Service
public class AgentRunTraceLoader {

    private final AgentRunRepository agentRunRepository;
    private final AgentToolCallRepository toolCallRepository;
    private final AgentCandidateDecisionRepository candidateRepository;
    private final AgentValidationRepository validationRepository;
    private final AgentDeliveredDecisionRepository deliveredRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final OutcomeAttributionRepository outcomeAttributionRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final GuidancePlanVersionRepository planVersionRepository;
    private final GuidancePlanStepRepository planStepRepository;

    public AgentRunTraceLoader(
            AgentRunRepository agentRunRepository,
            AgentToolCallRepository toolCallRepository,
            AgentCandidateDecisionRepository candidateRepository,
            AgentValidationRepository validationRepository,
            AgentDeliveredDecisionRepository deliveredRepository,
            UserActionEventRepository userActionEventRepository,
            OutcomeAttributionRepository outcomeAttributionRepository,
            AgentFeedbackRepository feedbackRepository,
            GuidancePlanVersionRepository planVersionRepository,
            GuidancePlanStepRepository planStepRepository
    ) {
        this.agentRunRepository = agentRunRepository;
        this.toolCallRepository = toolCallRepository;
        this.candidateRepository = candidateRepository;
        this.validationRepository = validationRepository;
        this.deliveredRepository = deliveredRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.outcomeAttributionRepository = outcomeAttributionRepository;
        this.feedbackRepository = feedbackRepository;
        this.planVersionRepository = planVersionRepository;
        this.planStepRepository = planStepRepository;
    }

    @Transactional(readOnly = true)
    public AgentRunTraceResponse load(UUID runId) {
        if (runId == null) {
            throw new NotFoundException("Agent run not found");
        }
        AgentRunEntity run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Agent run not found"));
        AgentRunVisibility visibility = visibility(run);
        boolean killSwitch = isKillSwitch(run);
        Map<String, Object> candidate = candidateRepository.findFirstByRunId(runId)
                .map(AgentCandidateDecisionEntity::getDecision)
                .map(AgentRunTraceLoader::copy)
                .orElse(null);
        Map<String, Object> validation = validationRepository.findFirstByRunId(runId)
                .map(AgentValidationEntity::getResult)
                .map(AgentRunTraceLoader::copy)
                .orElse(null);
        AgentDeliveredDecisionEntity deliveredRow = deliveredRepository.findFirstByRunId(runId).orElse(null);
        boolean emptyDecision = !killSwitch && candidate == null && validation == null;
        boolean deliveredLabeled = visibility == AgentRunVisibility.PRODUCTION
                && !killSwitch
                && !emptyDecision
                && deliveredRow != null;
        Decision decision = emptyDecision
                ? null
                : new Decision(
                        candidate,
                        validation,
                        deliveredLabeled ? copy(deliveredRow.getDecision()) : null,
                        deliveredLabeled,
                        run.getFallbackReason(),
                        committedHorizon(run)
                );

        UUID deliveredId = deliveredRow == null ? null : deliveredRow.getId();
        ObservedAction observedAction = observedAction(deliveredId);
        Outcome outcome = outcome(deliveredId);

        return new AgentRunTraceResponse(
                run.getId(),
                run.getFishingSessionId(),
                run.getStatus(),
                visibility,
                run.getTriggerOutboxId(),
                run.getTrigger(),
                run.getRelatedTriggers() == null ? List.of() : List.copyOf(run.getRelatedTriggers()),
                run.getTriggerReasonCodes() == null ? List.of() : List.copyOf(run.getTriggerReasonCodes()),
                versionsOf(run),
                run.getStartedAt(),
                run.getFinishedAt(),
                latencyMs(run),
                usage(run, visibility),
                deliveredLabeled,
                copy(run.getStateSnapshot()),
                copy(run.getContextSnapshot()),
                tools(runId),
                decision,
                observedAction,
                outcome
        );
    }

    static ObservedActionKind kindOf(UserActionEventEntity action) {
        if (action == null) {
            return ObservedActionKind.UNKNOWN;
        }
        if (acknowledgedPayload(action.getPayload())) {
            return ObservedActionKind.ACKNOWLEDGED;
        }
        if (action.isFollowedPrimary() || Boolean.TRUE.equals(action.getFollowedRecommendation())) {
            return ObservedActionKind.FOLLOWED;
        }
        return ObservedActionKind.NOT_FOLLOWED;
    }

    static ObservedActionKind deriveLatestKind(
            List<ObservedActionEvent> history,
            boolean acknowledged
    ) {
        if (history != null && !history.isEmpty()) {
            ObservedActionKind kind = history.getLast().kind();
            return kind == null ? ObservedActionKind.UNKNOWN : kind;
        }
        return acknowledged ? ObservedActionKind.ACKNOWLEDGED : ObservedActionKind.UNKNOWN;
    }

    private ObservedAction observedAction(UUID deliveredId) {
        if (deliveredId == null) {
            return ObservedAction.empty();
        }
        List<UserActionEventEntity> rows = new ArrayList<>(
                userActionEventRepository.findByDeliveredDecisionIdOrderByOccurredAtAsc(deliveredId)
        );
        rows.sort(Comparator
                .comparing(UserActionEventEntity::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserActionEventEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ObservedActionEvent> history = new ArrayList<>();
        for (UserActionEventEntity row : rows) {
            Map<String, Object> payload = copy(row.getPayload());
            history.add(new ObservedActionEvent(
                    row.getId(),
                    row.getOccurredAt(),
                    row.getDeliveredDecisionId(),
                    row.isFollowedPrimary(),
                    row.getFollowedRecommendation(),
                    row.getRecommendationRole(),
                    row.getActualAction(),
                    kindOf(row),
                    payload == null ? Map.of() : payload
            ));
        }
        boolean acknowledged = false;
        for (AgentFeedbackEntity feedback : feedbackRepository.findByDeliveredDecisionIdOrderByOccurredAtAsc(deliveredId)) {
            if (feedback.getStatus() == FeedbackStatus.ACKNOWLEDGED) {
                acknowledged = true;
                break;
            }
        }
        ObservedActionKind latestKind = deriveLatestKind(history, acknowledged);
        ObservedActionEvent latest = history.isEmpty() ? null : history.getLast();
        return new ObservedAction(history, latest, latestKind, latestKind == ObservedActionKind.FOLLOWED, acknowledged);
    }

    private Outcome outcome(UUID deliveredId) {
        if (deliveredId == null) {
            return Outcome.empty();
        }
        List<OutcomeAttributionEntity> rows = new ArrayList<>(
                outcomeAttributionRepository.findByDeliveredDecisionIdOrderByAttributedAtAsc(deliveredId)
        );
        rows.sort(Comparator
                .comparing(OutcomeAttributionEntity::getAttributedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OutcomeAttributionEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<OutcomeEvent> history = new ArrayList<>();
        for (OutcomeAttributionEntity row : rows) {
            history.add(new OutcomeEvent(
                    row.getId(),
                    row.getAttributedAt(),
                    row.getDeliveredDecisionId(),
                    row.getOutcomeKind(),
                    row.getAttributionDimension(),
                    row.isFollowedRecommendation(),
                    row.getRecommendationRole(),
                    row.getWindowKind(),
                    row.getConfidence(),
                    row.getFishInteractionId()
            ));
        }
        return new Outcome(history, history.isEmpty() ? null : history.getLast());
    }

    private List<ToolCall> tools(UUID runId) {
        List<ToolCall> tools = new ArrayList<>();
        for (AgentToolCallEntity row : toolCallRepository.findByRunIdOrderByObservedAtAsc(runId)) {
            Map<String, Object> request = copy(row.getRequest());
            Map<String, Object> result = copy(row.getResult());
            tools.add(new ToolCall(
                    row.getToolName(),
                    request == null ? Map.of() : request,
                    result == null ? Map.of() : result,
                    row.getLatencyMs(),
                    row.getObservedAt()
            ));
        }
        return List.copyOf(tools);
    }

    private List<HorizonStep> committedHorizon(AgentRunEntity run) {
        Integer version = run.getGuidancePlanVersion();
        if (version == null || run.getFishingSessionId() == null) {
            return List.of();
        }
        GuidancePlanVersionEntity plan = planVersionRepository
                .findByFishingSessionIdAndVersion(run.getFishingSessionId(), version)
                .orElse(null);
        if (plan == null) {
            return List.of();
        }
        List<HorizonStep> steps = new ArrayList<>();
        for (GuidancePlanStepEntity step : planStepRepository.findByGuidancePlanVersionIdOrderByStepAsc(plan.getId())) {
            if (step.isCommitted()) {
                steps.add(new HorizonStep(
                        step.getStep(),
                        step.getType(),
                        step.getTripWaypointId(),
                        step.getDurationMinutes(),
                        true
                ));
            }
        }
        return List.copyOf(steps);
    }

    private static Usage usage(AgentRunEntity run, AgentRunVisibility visibility) {
        UsageTelemetry telemetry = convert(run.getUsageTelemetry(), UsageTelemetry.class);
        return new Usage(
                visibility,
                telemetry == null ? null : telemetry.inputTokens(),
                telemetry == null ? null : telemetry.outputTokens(),
                telemetry == null ? null : telemetry.totalTokens(),
                telemetry == null ? null : telemetry.costUsd(),
                telemetry == null ? null : telemetry.pricingVersion(),
                copy(run.getUsageTelemetry())
        );
    }

    private static EvalComponentVersions versionsOf(AgentRunEntity run) {
        return new EvalComponentVersions(
                run.getPromptVersion(),
                run.getModelProvider(),
                run.getModelName(),
                run.getModelVersion(),
                run.getToolSchemaVersion(),
                run.getContextVersion(),
                null,
                null,
                null,
                run.getAgentPolicyVersion(),
                run.getLearningAlgorithmVersion(),
                run.getLearningSnapshotVersion()
        );
    }

    private static Long latencyMs(AgentRunEntity run) {
        if (run.getStartedAt() == null || run.getFinishedAt() == null) {
            return null;
        }
        return Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis();
    }

    private static boolean isKillSwitch(AgentRunEntity run) {
        return run.getStatus() == AgentRunStatus.FALLBACK
                && GuidanceFallback.KILL_SWITCH.equals(run.getFallbackReason());
    }

    private static AgentRunVisibility visibility(AgentRunEntity run) {
        return run.getVisibility() == null ? AgentRunVisibility.PRODUCTION : run.getVisibility();
    }

    private static boolean acknowledgedPayload(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        Object status = payload.get("status");
        return status != null && FeedbackStatus.ACKNOWLEDGED.name().equals(String.valueOf(status));
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new LinkedHashMap<>(value);
    }

    private static <T> T convert(Map<String, Object> value, Class<T> type) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return GuidanceContracts.mapper().convertValue(value, type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
