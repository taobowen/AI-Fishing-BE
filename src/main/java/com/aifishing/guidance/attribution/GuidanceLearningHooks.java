package com.aifishing.guidance.attribution;

import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.learning.GuidanceLearningOutboxService;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GuidanceLearningHooks {

    private static final Logger log = LoggerFactory.getLogger(GuidanceLearningHooks.class);

    private final OutcomeAttributor outcomeAttributor;
    private final UserActionObserver userActionObserver;
    private final SessionEventWriter sessionEventWriter;
    private final SessionEventRepository sessionEventRepository;
    private final GuidanceLearningOutboxService learningOutboxService;
    private final GuidanceProperties properties;
    private final Clock clock;

    public GuidanceLearningHooks(
            OutcomeAttributor outcomeAttributor,
            UserActionObserver userActionObserver,
            @Lazy SessionEventWriter sessionEventWriter,
            SessionEventRepository sessionEventRepository,
            GuidanceLearningOutboxService learningOutboxService,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.outcomeAttributor = outcomeAttributor;
        this.userActionObserver = userActionObserver;
        this.sessionEventWriter = sessionEventWriter;
        this.sessionEventRepository = sessionEventRepository;
        this.learningOutboxService = learningOutboxService;
        this.properties = properties;
        this.clock = clock;
    }

    public void onAdviceDelivered(UUID fishingSessionId, DeliveredDecision delivered, AgentDeliveredDecisionEntity entity) {
        if (fishingSessionId == null || delivered == null || entity == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sourceEventId", "advice-created:" + entity.getId());
            payload.put("onWaypoint", onWaypoint(fishingSessionId));
            if (delivered.targetTripWaypointId() != null) {
                payload.put("waypointId", delivered.targetTripWaypointId().toString());
            }
            DeliveredSnapshot snapshot = new DeliveredSnapshot(
                    entity.getId(),
                    entity.getRunId(),
                    entity.getCreatedAt() == null ? clock.instant() : entity.getCreatedAt(),
                    delivered
            );
            userActionObserver.observe(
                    fishingSessionId,
                    snapshot,
                    SessionEventType.ADVICE_CREATED,
                    clock.instant(),
                    payload
            );
            enqueueAttribute(fishingSessionId, "advice-created:" + entity.getId(), clock.instant());
            Instant windowEnd = clock.instant().plus(maxWindow(delivered));
            enqueueAttribute(fishingSessionId, "advice-window:" + entity.getId(), windowEnd);
        } catch (RuntimeException ex) {
            log.warn("Learning hook after advice failed: {}", ex.getMessage());
        }
    }

    public void onFeedback(
            UUID fishingSessionId,
            UUID userId,
            AgentDeliveredDecisionEntity delivered,
            DeliveredDecision decision,
            FeedbackStatus status,
            GuidanceRejectReason rejectReason,
            String note,
            UUID feedbackId
    ) {
        if (fishingSessionId == null || delivered == null || status == null) {
            return;
        }
        try {
            SessionEventType type = switch (status) {
                case REJECTED -> SessionEventType.ADVICE_REJECTED;
                case ACKNOWLEDGED -> SessionEventType.ADVICE_ACKNOWLEDGED;
                case ACCEPTED, PARTIALLY_FOLLOWED -> SessionEventType.ADVICE_ACCEPTED;
            };
            Map<String, Object> payload = new HashMap<>();
            payload.put("deliveredDecisionId", delivered.getId().toString());
            payload.put("status", status.name());
            if (rejectReason != null) {
                payload.put("rejectReason", rejectReason.name());
            }
            payload.put("onWaypoint", onWaypoint(fishingSessionId));
            sessionEventWriter.writeAudit(fishingSessionId, new SessionEvent(
                    GuidanceSchemaVersion.VALUE,
                    type,
                    clock.instant(),
                    EventSource.CLIENT,
                    "advice-feedback:" + feedbackId,
                    payload,
                    null
            ));
            DeliveredSnapshot snapshot = new DeliveredSnapshot(
                    delivered.getId(),
                    delivered.getRunId(),
                    delivered.getCreatedAt(),
                    decision
            );
            payload.put("sourceEventId", "advice-feedback:" + feedbackId);
            userActionObserver.observe(fishingSessionId, snapshot, type, clock.instant(), payload);
            enqueueAttribute(fishingSessionId, "feedback:" + feedbackId, clock.instant());
            learningOutboxService.enqueue(
                    fishingSessionId,
                    LearningJobType.REFLECTION_EVAL,
                    "reflection-eval:feedback:" + feedbackId,
                    Map.of("feedbackId", feedbackId.toString())
            );
            if (status == FeedbackStatus.REJECTED) {
                Map<String, Object> pref = new HashMap<>();
                pref.put("rejectReason", rejectReason == null ? GuidanceRejectReason.UNSPECIFIED.name() : rejectReason.name());
                if (note != null) {
                    pref.put("note", note);
                }
                if (userId != null) {
                    pref.put("userId", userId.toString());
                }
                pref.put("deliveredDecisionId", delivered.getId().toString());
                learningOutboxService.enqueue(
                        fishingSessionId,
                        LearningJobType.PREFERENCE_UPDATE,
                        "preference-update:" + feedbackId,
                        pref
                );
            }
        } catch (RuntimeException ex) {
            log.warn("Learning hook after feedback failed: {}", ex.getMessage());
        }
    }

    public void onObservationalEvent(UUID fishingSessionId, SessionEventEntity event) {
        if (fishingSessionId == null || event == null) {
            return;
        }
        try {
            Map<String, Object> payload = event.getPayload() == null ? new HashMap<>() : new HashMap<>(event.getPayload());
            payload.put("sourceEventId", event.getId().toString());
            for (DeliveredSnapshot snapshot : outcomeAttributor.loadDecisions(fishingSessionId)) {
                userActionObserver.observe(
                        fishingSessionId,
                        snapshot,
                        event.getType(),
                        event.getOccurredAt(),
                        payload
                );
            }
            enqueueAttribute(fishingSessionId, "event:" + event.getId(), clock.instant());
            if (event.getType() == SessionEventType.WAYPOINT_ENTERED) {
                Instant moveEnd = event.getOccurredAt().plus(Duration.ofMinutes(properties.getAttribution().getMoveWindowMinutes()));
                enqueueAttribute(fishingSessionId, "move-window:" + event.getId(), moveEnd);
            }
        } catch (RuntimeException ex) {
            log.warn("Learning hook after {} failed: {}", event.getType(), ex.getMessage());
        }
    }

    public void onCatchOutcomeChanged(UUID fishingSessionId, CatchEvent catchEvent) {
        if (fishingSessionId == null || catchEvent == null) {
            return;
        }
        try {
            enqueueAttribute(fishingSessionId, "catch:" + catchEvent.getId() + ":" + catchEvent.getOutcome(), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Learning hook after catch update failed: {}", ex.getMessage());
        }
    }

    private void enqueueAttribute(UUID fishingSessionId, String token, Instant availableAt) {
        learningOutboxService.enqueue(
                fishingSessionId,
                LearningJobType.ATTRIBUTE_OUTCOME,
                "attribute-outcome:" + fishingSessionId + ":" + token,
                Map.of("source", token),
                availableAt
        );
    }

    private boolean onWaypoint(UUID fishingSessionId) {
        List<SessionEventEntity> events = sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(
                fishingSessionId,
                List.of(SessionEventType.WAYPOINT_ENTERED, SessionEventType.WAYPOINT_LEFT)
        );
        if (events.isEmpty()) {
            return false;
        }
        return events.getLast().getType() == SessionEventType.WAYPOINT_ENTERED;
    }

    private Duration maxWindow(DeliveredDecision delivered) {
        Duration max = Duration.ZERO;
        for (GuidanceAction action : new GuidanceAction[] { delivered.primaryAction(), delivered.secondaryAction() }) {
            Duration length = AttributionWindows.windowLength(action, delivered.reevaluateAfterMinutes(), properties.getAttribution());
            if (length.compareTo(max) > 0) {
                max = length;
            }
        }
        return max.isZero() ? Duration.ofMinutes(properties.getAttribution().getMaxStayAttributionMinutes()) : max;
    }
}
