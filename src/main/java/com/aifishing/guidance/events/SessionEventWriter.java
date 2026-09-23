package com.aifishing.guidance.events;

import com.aifishing.feedback.catchlog.dto.CreateCatchRequest;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.dto.ClientEventRequest;
import com.aifishing.fishingsession.dto.StartFishingSessionRequest;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LureEvent;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.dispatch.GuidanceTriggerOutboxService;
import com.aifishing.guidance.learning.GuidanceLearningOutboxService;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.persistence.LureEventEntity;
import com.aifishing.guidance.persistence.LureEventRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.attribution.GuidanceLearningHooks;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.guidance.spi.TriggerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionEventWriter {

    private static final Logger log = LoggerFactory.getLogger(SessionEventWriter.class);

    private final SessionEventRepository sessionEventRepository;
    private final LureEventRepository lureEventRepository;
    private final ActivityStateUpdater activityStateUpdater;
    private final TriggerRouter triggerRouter;
    private final GuidanceTriggerOutboxService outboxService;
    private final GuidanceLearningOutboxService learningOutboxService;
    private final FishingSessionStateBuilder stateBuilder;
    private final GuidanceLearningHooks learningHooks;
    private final Clock clock;

    public SessionEventWriter(
            SessionEventRepository sessionEventRepository,
            LureEventRepository lureEventRepository,
            ActivityStateUpdater activityStateUpdater,
            TriggerRouter triggerRouter,
            GuidanceTriggerOutboxService outboxService,
            GuidanceLearningOutboxService learningOutboxService,
            FishingSessionStateBuilder stateBuilder,
            @Lazy GuidanceLearningHooks learningHooks,
            Clock clock
    ) {
        this.sessionEventRepository = sessionEventRepository;
        this.lureEventRepository = lureEventRepository;
        this.activityStateUpdater = activityStateUpdater;
        this.triggerRouter = triggerRouter;
        this.outboxService = outboxService;
        this.learningOutboxService = learningOutboxService;
        this.stateBuilder = stateBuilder;
        this.learningHooks = learningHooks;
        this.clock = clock;
    }

    @Transactional
    public void onArrive(FishingSession session, UUID waypointId, ClientEventRequest request) {
        activityStateUpdater.refresh(session, request.occurredAt());
        writeAndRoute(session, event(
                SessionEventType.WAYPOINT_ENTERED,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                Map.of("waypointId", waypointId.toString()),
                null
        ));
    }

    @Transactional
    public void onSkip(FishingSession session, UUID waypointId, ClientEventRequest request) {
        activityStateUpdater.refresh(session, request.occurredAt());
        writeAndRoute(session, event(
                SessionEventType.WAYPOINT_LEFT,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                Map.of("waypointId", waypointId.toString(), "skipped", true),
                null
        ));
    }

    @Transactional
    public void onComplete(FishingSession session, UUID waypointId, ClientEventRequest request) {
        activityStateUpdater.refresh(session, request.occurredAt());
        writeAndRoute(session, event(
                SessionEventType.WAYPOINT_LEFT,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                Map.of("waypointId", waypointId.toString(), "completed", true),
                null
        ));
    }

    @Transactional
    public void onPause(FishingSession session, ClientEventRequest request) {
        activityStateUpdater.refresh(session, request.occurredAt());
        writeAndRoute(session, event(
                SessionEventType.SESSION_PAUSED,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                Map.of(),
                null
        ));
    }

    @Transactional
    public void onResume(FishingSession session, ClientEventRequest request) {
        activityStateUpdater.refresh(session, request.occurredAt());
        writeAndRoute(session, event(
                SessionEventType.SESSION_RESUMED,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                Map.of(),
                null
        ));
    }

    @Transactional
    public void onEnd(FishingSession session, ClientEventRequest request) {
        activityStateUpdater.refresh(session, request.occurredAt());
        writeAndRoute(session, event(
                SessionEventType.SESSION_COMPLETED,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                Map.of(),
                null
        ));
        learningOutboxService.enqueue(
                session.getId(),
                LearningJobType.SESSION_SUMMARY,
                "session-summary:" + session.getId(),
                Map.of("userId", session.getUserId().toString())
        );
    }

    @Transactional
    public void onBite(FishingSession session, String clientEventId, Instant occurredAt, UUID fishInteractionId) {
        activityStateUpdater.refresh(session, occurredAt);
        writeAndRoute(session, event(
                SessionEventType.BITE,
                occurredAt,
                EventSource.CLIENT,
                clientEventId,
                Map.of(),
                fishInteractionId
        ));
    }

    @Transactional
    public void onFishOn(FishingSession session, String clientEventId, Instant occurredAt, UUID fishInteractionId) {
        activityStateUpdater.refresh(session, occurredAt);
        writeAndRoute(session, event(
                SessionEventType.FISH_ON,
                occurredAt,
                EventSource.CLIENT,
                clientEventId,
                Map.of(),
                fishInteractionId
        ));
    }

    @Transactional
    public void onCatch(FishingSession session, CreateCatchRequest request) {
        Instant occurredAt = request.occurredAt();
        UUID interactionId = request.fishInteractionId() != null
                ? request.fishInteractionId()
                : interactionIdFromClientCatch(request.clientCatchId());
        boolean fishOnExists = sessionEventRepository.existsByFishingSessionIdAndTypeAndFishInteractionId(
                session.getId(), SessionEventType.FISH_ON, interactionId
        );
        activityStateUpdater.refresh(session, occurredAt);
        if (!fishOnExists) {
            writeAndRoute(session, event(
                    SessionEventType.FISH_ON,
                    occurredAt,
                    EventSource.SERVER,
                    "fish-on:" + interactionId,
                    Map.of("synthesized", true, "clientCatchId", request.clientCatchId()),
                    interactionId
            ));
        }
        writeAndRoute(session, event(
                SessionEventType.CATCH_CREATED,
                occurredAt,
                EventSource.CLIENT,
                "catch:" + request.clientCatchId(),
                Map.of("clientCatchId", request.clientCatchId()),
                interactionId
        ));
    }

    @Transactional
    public void onLure(FishingSession session, String clientEventId, LureEvent lureEvent) {
        Instant occurredAt = lureEvent.occurredAt();
        activityStateUpdater.refresh(session, occurredAt);
        LureEventEntity lure = new LureEventEntity();
        lure.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        lure.setFishingSessionId(session.getId());
        lure.setOccurredAt(occurredAt);
        lure.setLureFamily(lureEvent.lureFamily());
        lure.setPresentation(lureEvent.presentation());
        lure.setDepthM(lureEvent.depthM() == null
                ? null
                : BigDecimal.valueOf(lureEvent.depthM()).setScale(2, RoundingMode.HALF_UP));
        lure.setRetrieveStyle(lureEvent.retrieveStyle());
        lure.setEnvelope(Map.of(
                "schemaVersion", GuidanceSchemaVersion.VALUE,
                "occurredAt", occurredAt.toString(),
                "lureFamily", lureEvent.lureFamily().name(),
                "presentation", lureEvent.presentation().name()
        ));
        lureEventRepository.save(lure);
        Map<String, Object> payload = new HashMap<>();
        payload.put("lureFamily", lureEvent.lureFamily().name());
        payload.put("presentation", lureEvent.presentation().name());
        if (lureEvent.depthM() != null) {
            payload.put("depthM", lureEvent.depthM());
        }
        if (lureEvent.retrieveStyle() != null) {
            payload.put("retrieveStyle", lureEvent.retrieveStyle().name());
        }
        writeAndRoute(session, event(
                SessionEventType.LURE_CHANGED,
                occurredAt,
                EventSource.CLIENT,
                clientEventId == null || clientEventId.isBlank()
                        ? "lure:" + occurredAt + ":" + lureEvent.lureFamily()
                        : clientEventId,
                payload,
                null
        ));
    }

    @Transactional
    public void onAdHocStarted(FishingSession session, ClientEventRequest request, Map<String, Object> payload) {
        writeAndRoute(session, event(
                SessionEventType.USER_STARTED_AD_HOC_FISHING,
                request.occurredAt(),
                EventSource.CLIENT,
                request.clientEventId(),
                payload == null ? Map.of() : payload,
                null
        ));
    }

    @Transactional
    public void onAdHocEnded(
            FishingSession session,
            Instant occurredAt,
            String idempotencyKey,
            EventSource source,
            Map<String, Object> payload
    ) {
        writeAndRoute(session, event(
                SessionEventType.USER_ENDED_AD_HOC_FISHING,
                occurredAt,
                source == null ? EventSource.SERVER : source,
                idempotencyKey,
                payload == null ? Map.of() : payload,
                null
        ));
    }

    @Transactional
    public void auditAdHocEnded(
            FishingSession session,
            Instant occurredAt,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
        persist(session.getId(), event(
                SessionEventType.USER_ENDED_AD_HOC_FISHING,
                occurredAt,
                EventSource.SERVER,
                idempotencyKey,
                payload == null ? Map.of() : payload,
                null
        ));
    }

    @Transactional
    public void onSessionStarted(FishingSession session, StartFishingSessionRequest.LateStartMode lateStart) {
        if (session == null || session.getId() == null || lateStart == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("lateStart", lateStart.name());
        if (session.getGuidanceMode() != null) {
            payload.put("guidanceMode", session.getGuidanceMode().name());
        }
        persist(session.getId(), event(
                SessionEventType.SESSION_STARTED,
                session.getStartedAt() == null ? clock.instant() : session.getStartedAt(),
                EventSource.SERVER,
                "session-started:" + session.getId(),
                payload,
                null
        ));
    }

    @Transactional
    public void writeAudit(UUID fishingSessionId, SessionEvent event) {
        persist(fishingSessionId, event);
    }

    private void writeAndRoute(FishingSession session, SessionEvent event) {
        SessionEventEntity saved = persist(session.getId(), event);
        if (saved == null) {
            return;
        }
        try {
            learningHooks.onObservationalEvent(session.getId(), saved);
        } catch (RuntimeException ex) {
            log.warn("Learning observe/enqueue failed after {}: {}", event.type(), ex.getMessage());
        }
        if (!session.getGuidanceMode().routesAgent()) {
            return;
        }
        try {
            FishingSessionState state = stateBuilder.build(
                    session.getId(),
                    new EnvironmentSnapshot(clock.instant(), null)
            );
            Optional<TriggerRoutingDecision> decision = triggerRouter.route(toContract(saved, event), state);
            decision.ifPresent(value -> outboxService.upsert(
                    session.getId(), value, GuidanceTriggerOutboxSource.EVENT
            ));
        } catch (RuntimeException ex) {
            log.warn("Guidance route/outbox failed after {}: {}", event.type(), ex.getMessage());
        }
    }

    private SessionEventEntity persist(UUID fishingSessionId, SessionEvent event) {
        Optional<SessionEventEntity> existing = sessionEventRepository
                .findByFishingSessionIdAndIdempotencyKey(fishingSessionId, event.idempotencyKey());
        if (existing.isPresent()) {
            return null;
        }
        SessionEventEntity entity = new SessionEventEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setFishingSessionId(fishingSessionId);
        entity.setType(event.type());
        entity.setOccurredAt(event.occurredAt());
        entity.setPayload(event.payload() == null ? Map.of() : new HashMap<>(event.payload()));
        entity.setSource(event.source());
        entity.setIdempotencyKey(event.idempotencyKey());
        entity.setFishInteractionId(event.fishInteractionId());
        return sessionEventRepository.save(entity);
    }

    private static SessionEvent toContract(SessionEventEntity saved, SessionEvent requested) {
        return new SessionEvent(
                GuidanceSchemaVersion.VALUE,
                saved.getType(),
                saved.getOccurredAt(),
                saved.getSource(),
                saved.getIdempotencyKey(),
                saved.getPayload(),
                saved.getFishInteractionId()
        );
    }

    private static SessionEvent event(
            SessionEventType type,
            Instant occurredAt,
            EventSource source,
            String idempotencyKey,
            Map<String, Object> payload,
            UUID fishInteractionId
    ) {
        return new SessionEvent(
                GuidanceSchemaVersion.VALUE,
                type,
                occurredAt,
                source,
                idempotencyKey,
                payload,
                fishInteractionId
        );
    }

    public static UUID interactionIdFromClientCatch(String clientCatchId) {
        if (clientCatchId == null || clientCatchId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(clientCatchId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(clientCatchId.getBytes());
        }
    }
}
