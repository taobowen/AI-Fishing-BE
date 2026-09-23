package com.aifishing.guidance.replay;

import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxStatus;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.UserActionEventEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds timeline events from Phase 1 tables. Trigger↔run correlation uses
 * {@code agent_runs.trigger_outbox_id} only — never timestamp joins.
 */
final class TimelineAssembler {

    private static final Set<SessionEventType> ADVICE_TYPES = EnumSet.of(
            SessionEventType.ADVICE_CREATED,
            SessionEventType.ADVICE_ACCEPTED,
            SessionEventType.ADVICE_REJECTED,
            SessionEventType.ADVICE_ACKNOWLEDGED
    );

    private TimelineAssembler() {
    }

    static List<SessionTimelineEvent> assemble(
            List<SessionEventEntity> sessionEvents,
            List<GuidanceTriggerOutboxEntity> triggers,
            List<AgentRunEntity> runs,
            List<UserActionEventEntity> observedActions,
            List<OutcomeAttributionEntity> outcomes,
            List<GuidancePlanVersionEntity> horizons,
            List<CatchEvent> catches
    ) {
        Map<UUID, AgentRunEntity> productionRunByOutbox = productionRunByOutbox(runs);
        List<SessionTimelineEvent> events = new ArrayList<>();
        for (SessionEventEntity event : safe(sessionEvents)) {
            if (event.getType() == SessionEventType.GPS_UPDATED) {
                continue;
            }
            events.add(sessionEvent(event));
        }
        for (GuidanceTriggerOutboxEntity trigger : safe(triggers)) {
            events.add(triggerEvent(trigger, productionRunByOutbox.get(trigger.getId())));
        }
        for (AgentRunEntity run : safe(runs)) {
            events.add(agentRunEvent(run));
        }
        for (UserActionEventEntity action : safe(observedActions)) {
            events.add(observedAction(action));
        }
        for (OutcomeAttributionEntity outcome : safe(outcomes)) {
            events.add(outcome(outcome));
        }
        for (GuidancePlanVersionEntity horizon : safe(horizons)) {
            events.add(horizon(horizon));
        }
        for (CatchEvent catchEvent : safe(catches)) {
            if (catchEvent.getStatus() == CatchStatus.VOIDED) {
                continue;
            }
            if (catchEvent.getOutcome() != CatchOutcome.LANDED && catchEvent.getOutcome() != CatchOutcome.LOST) {
                continue;
            }
            events.add(catchRow(catchEvent));
        }
        events.sort(TimelineEventOrder.COMPARATOR);
        return List.copyOf(events);
    }

    /**
     * Exact FK match. Unexecuted triggers stay {@code runId=null}. When several
     * PRODUCTION rows share an outbox id, the latest {@code startedAt} wins.
     */
    static Map<UUID, AgentRunEntity> productionRunByOutbox(List<AgentRunEntity> runs) {
        Map<UUID, AgentRunEntity> byOutbox = new HashMap<>();
        Comparator<AgentRunEntity> latest = Comparator
                .comparing(AgentRunEntity::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgentRunEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        for (AgentRunEntity run : safe(runs)) {
            if (run.getTriggerOutboxId() == null || run.getVisibility() == AgentRunVisibility.SHADOW) {
                continue;
            }
            byOutbox.merge(
                    run.getTriggerOutboxId(),
                    run,
                    (existing, candidate) -> latest.compare(existing, candidate) >= 0 ? existing : candidate
            );
        }
        return byOutbox;
    }

    static TriggerDispatchStatus dispatchStatus(GuidanceTriggerOutboxEntity trigger) {
        if (trigger.getRelatedTriggers() != null && !trigger.getRelatedTriggers().isEmpty()) {
            return TriggerDispatchStatus.COALESCED;
        }
        GuidanceTriggerOutboxStatus status = trigger.getStatus();
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> TriggerDispatchStatus.PENDING;
            case CLAIMED -> TriggerDispatchStatus.CLAIMED;
            case DONE -> TriggerDispatchStatus.DONE;
            case FAILED -> TriggerDispatchStatus.FAILED;
        };
    }

    private static SessionTimelineEvent sessionEvent(SessionEventEntity event) {
        TimelineSource source = ADVICE_TYPES.contains(event.getType()) ? TimelineSource.ADVICE : TimelineSource.SESSION_EVENT;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", event.getType() == null ? null : event.getType().name());
        detail.put("eventSource", event.getSource() == null ? null : event.getSource().name());
        detail.put("payload", event.getPayload() == null ? Map.of() : event.getPayload());
        if (event.getFishInteractionId() != null) {
            detail.put("fishInteractionId", event.getFishInteractionId().toString());
        }
        return new SessionTimelineEvent(
                event.getId(),
                event.getOccurredAt(),
                source,
                event.getType() == null ? "UNKNOWN" : event.getType().name(),
                null,
                null,
                null,
                null,
                null,
                detail
        );
    }

    private static SessionTimelineEvent triggerEvent(GuidanceTriggerOutboxEntity trigger, AgentRunEntity matchedRun) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("outboxStatus", trigger.getStatus() == null ? null : trigger.getStatus().name());
        detail.put("primaryTrigger", trigger.getPrimaryTrigger() == null ? null : trigger.getPrimaryTrigger().name());
        detail.put("relatedTriggers", trigger.getRelatedTriggers() == null
                ? List.of()
                : trigger.getRelatedTriggers().stream().map(GuidanceTrigger::name).toList());
        detail.put("reasonCodes", trigger.getReasonCodes() == null ? List.of() : trigger.getReasonCodes());
        detail.put("source", trigger.getSource() == null ? null : trigger.getSource().name());
        return new SessionTimelineEvent(
                trigger.getId(),
                trigger.getCreatedAt(),
                TimelineSource.TRIGGER,
                "AGENT_TRIGGER",
                dispatchStatus(trigger),
                matchedRun == null ? null : matchedRun.getId(),
                null,
                null,
                trigger.getPrimaryTrigger(),
                detail
        );
    }

    private static SessionTimelineEvent agentRunEvent(AgentRunEntity run) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", run.getStatus() == null ? null : run.getStatus().name());
        detail.put("visibility", visibility(run).name());
        detail.put("trigger", run.getTrigger() == null ? null : run.getTrigger().name());
        detail.put("fallbackReason", run.getFallbackReason());
        detail.put("triggerOutboxId", run.getTriggerOutboxId() == null ? null : run.getTriggerOutboxId().toString());
        detail.put("agentPolicyVersion", run.getAgentPolicyVersion());
        detail.put("relatedTriggers", run.getRelatedTriggers() == null
                ? List.of()
                : run.getRelatedTriggers().stream().map(GuidanceTrigger::name).toList());
        detail.put("reasonCodes", run.getTriggerReasonCodes() == null ? List.of() : run.getTriggerReasonCodes());
        return new SessionTimelineEvent(
                run.getId(),
                run.getStartedAt(),
                TimelineSource.AGENT_RUN,
                "AGENT_RUN",
                null,
                run.getId(),
                visibility(run),
                run.getStatus(),
                run.getTrigger(),
                detail
        );
    }

    private static SessionTimelineEvent observedAction(UserActionEventEntity action) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("deliveredDecisionId", action.getDeliveredDecisionId() == null
                ? null
                : action.getDeliveredDecisionId().toString());
        detail.put("followedPrimary", action.isFollowedPrimary());
        detail.put("followedRecommendation", action.getFollowedRecommendation());
        detail.put("recommendationRole", action.getRecommendationRole() == null
                ? null
                : action.getRecommendationRole().name());
        detail.put("actualAction", action.getActualAction() == null ? null : action.getActualAction().name());
        detail.put("payload", action.getPayload() == null ? Map.of() : action.getPayload());
        return new SessionTimelineEvent(
                action.getId(),
                action.getOccurredAt(),
                TimelineSource.OBSERVED_ACTION,
                "OBSERVED_ACTION",
                null,
                null,
                null,
                null,
                null,
                detail
        );
    }

    private static SessionTimelineEvent outcome(OutcomeAttributionEntity outcome) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("deliveredDecisionId", outcome.getDeliveredDecisionId() == null
                ? null
                : outcome.getDeliveredDecisionId().toString());
        detail.put("outcomeKind", outcome.getOutcomeKind() == null ? null : outcome.getOutcomeKind().name());
        detail.put("attributionDimension", outcome.getAttributionDimension() == null
                ? null
                : outcome.getAttributionDimension().name());
        detail.put("followedRecommendation", outcome.isFollowedRecommendation());
        detail.put("recommendationRole", outcome.getRecommendationRole() == null
                ? null
                : outcome.getRecommendationRole().name());
        detail.put("windowKind", outcome.getWindowKind() == null ? null : outcome.getWindowKind().name());
        detail.put("confidence", outcome.getConfidence());
        if (outcome.getFishInteractionId() != null) {
            detail.put("fishInteractionId", outcome.getFishInteractionId().toString());
        }
        return new SessionTimelineEvent(
                outcome.getId(),
                outcome.getAttributedAt(),
                TimelineSource.OUTCOME,
                "OUTCOME",
                null,
                null,
                null,
                null,
                null,
                detail
        );
    }

    private static SessionTimelineEvent horizon(GuidancePlanVersionEntity horizon) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", horizon.getVersion());
        detail.put("parentVersion", horizon.getParentVersion());
        detail.put("replanReason", horizon.getReplanReason());
        detail.put("replanScope", horizon.getReplanScope() == null ? null : horizon.getReplanScope().name());
        detail.put("createdBy", horizon.getCreatedBy() == null ? null : horizon.getCreatedBy().name());
        return new SessionTimelineEvent(
                horizon.getId(),
                horizon.getCreatedAt(),
                TimelineSource.HORIZON,
                "HORIZON_CHANGED",
                null,
                null,
                null,
                null,
                null,
                detail
        );
    }

    private static SessionTimelineEvent catchRow(CatchEvent catchEvent) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("outcome", catchEvent.getOutcome().name());
        detail.put("species", catchEvent.getSpecies() == null ? null : catchEvent.getSpecies().name());
        detail.put("tripWaypointId", catchEvent.getTripWaypointId() == null
                ? null
                : catchEvent.getTripWaypointId().toString());
        return new SessionTimelineEvent(
                catchEvent.getId(),
                catchEvent.getOccurredAt(),
                TimelineSource.CATCH,
                catchEvent.getOutcome().name(),
                null,
                null,
                null,
                null,
                null,
                detail
        );
    }

    private static AgentRunVisibility visibility(AgentRunEntity run) {
        return run.getVisibility() == null ? AgentRunVisibility.PRODUCTION : run.getVisibility();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
